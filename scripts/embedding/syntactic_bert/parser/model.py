# -*- coding: utf-8 -*-

from parser.metric import AttachmentMethod
from parser.parser import BiaffineParser

import torch
import torch.nn as nn
import torch.optim as optim
from pytorch_pretrained_bert import BertAdam
from pytorch_pretrained_bert import BertTokenizer

from datetime import datetime, timedelta
from tqdm import tqdm

import numpy as np

class Model(object):

    def __init__(self, vocab, network):
        super(Model, self).__init__()

        self.vocab = vocab
        self.network = network
        self.criterion = nn.CrossEntropyLoss()
        if torch.cuda.is_available():
            self.device = torch.device('cuda')
        else:
            self.device = torch.device('cpu')
        # self.tokenizer = BertTokenizer.from_pretrained('bert-base-multilingual-cased', do_lower_case=False)
        
    def __call__(self, loaders, epochs, patience,
                 lr, betas, epsilon, weight_decay, annealing, file,
                 last_epoch, cloud_address, args, gradient_accumulation_steps=1, max_metric=0.0):

        self.gradient_accumulation_steps = gradient_accumulation_steps
        total_time = timedelta()
        max_e, max_metric = last_epoch, max_metric
        train_loader, dev_loader, test_loader = loaders
        self.optimizer = BertAdam(params=self.network.parameters(),
                                  lr=lr, b1=betas[0], b2=betas[1],
                                  e=epsilon, weight_decay=weight_decay,
                                  max_grad_norm=5.0)
        # self.optimizer = optim.Adam(params=self.network.parameters(),
        #                             lr=lr, betas=betas, eps=epsilon)
        # self.scheduler = optim.lr_scheduler.LambdaLR(optimizer=self.optimizer,
        #                                              lr_lambda=annealing)
        if args.local_rank == 0:
            print('***Started training at {}***'.format(datetime.now()))
        
        for epoch in range(last_epoch + 1, epochs + 1):
            start = datetime.now()
            # train one epoch and update the parameters
            if args.local_rank == 0:
                print(f"Epoch {epoch} / {epochs}:")
            if args.distributed:
                train_loader.sampler.set_epoch(epoch)
            self.train(train_loader, distirbuted=args.distributed)
            
            # train_loss, train_metric = self.evaluate(train_loader)
            # if args.local_rank == 0:
            #     print(f"{'train:':<6} Loss: {train_loss:.4f} {train_metric}")
            
            dev_loss, dev_metric = self.evaluate(dev_loader)
            if args.local_rank == 0:
                print(f"{'dev:':<6} Loss: {dev_loss:.4f} {dev_metric}")

            # test_loss, test_metric = self.evaluate(test_loader)
            # if args.local_rank == 0:
            #     print(f"{'test:':<6} Loss: {test_loss:.4f} {test_metric}")

            t = datetime.now() - start
            if args.local_rank == 0:
                print(f"{t}s elapsed\n")
            total_time += t       

            # save the model if it is the best so far
            if args.local_rank == 0:
                if dev_metric > max_metric:
                    max_e, max_metric = epoch, dev_metric
                    if args.distributed or torch.cuda.device_count() > 1:
                        self.network.module.save(file, epoch, cloud_address, self.optimizer, max_metric, args.local_rank)
                    else:
                        self.network.save(file, epoch, cloud_address, self.optimizer, max_e, max_metric)
                    
                elif epoch - max_e >= patience:
                    break
        if args.local_rank == 0:
            print('***Finished training at {}***'.format(datetime.now()))
            self.network = BiaffineParser.load(file, cloud_address)
            loss, metric = self.evaluate(test_loader)
            print(f"max score of dev is {max_metric.score:.2%} at epoch {max_e}")
            print(f"the score of test at epoch {max_e} is {metric.score:.2%}")
            print(f"mean time of each epoch is {total_time / epoch}s")
            print(f"{total_time}s elapsed")

    def train(self, loader, distirbuted=False):
        self.network.train()
        step = 0
        if not distirbuted:
            loader = tqdm(loader)

        for batch in loader:
            batch = tuple(t.to(self.device) for t in batch)
            words, attention_mask, token_start_mask, arcs, rels = batch
            
            # try: 
            s_arc, s_rel = self.network(words, attention_mask)
            # ignore [CLS]
            token_start_mask[:, 0] = 0
            # ignore [SEP]
            lens = attention_mask.sum(dim=1) - 1
            token_start_mask[torch.arange(len(token_start_mask)), lens] = 0
            
            gold_arcs, gold_rels = arcs[token_start_mask], rels[token_start_mask]
            s_arc, s_rel = s_arc[token_start_mask], s_rel[token_start_mask]            

            loss = self.get_loss(s_arc, s_rel, gold_arcs, gold_rels)

            # except:
            #     for sentence in words:
            #         print(self.tokenizer.convert_ids_to_tokens(sentence.detach().to(torch.device("cpu")).numpy()))
                
            #     print('***DEBUGGING PARSER START***')
            #     self.network.eval()
            #     self.network(words, attention_mask, debug=True)
            #     print('***DEBUGGING PARSER END***')

            #     print('words', words.shape)
            #     print('arcs', arcs.shape)
            #     print('rels', rels.shape)
            #     print('attention_mask', attention_mask.shape)
            #     print('token_start_mask', token_start_mask.shape)

            #     print('s_arc', s_arc.shape)
            #     print('s_rel', s_rel.shape)
            #     print('gold_arcs', gold_arcs.shape)
            #     print('gold_rels', gold_rels.shape)

                
            #     raise RuntimeError('training failed.')
            
            
            if self.gradient_accumulation_steps > 1:
                loss = loss / self.gradient_accumulation_steps
            
            loss.backward()
            
            if (step + 1) % self.gradient_accumulation_steps == 0:
                nn.utils.clip_grad_norm_(self.network.parameters(), 5.0)
                self.optimizer.step()
                # self.scheduler.step()
                self.optimizer.zero_grad()
            step += 1
  
    @torch.no_grad()
    def evaluate(self, loader, include_punct=False):
        self.network.eval()

        loss, metric = 0, AttachmentMethod()
        for i, batch in enumerate(loader):
            batch = tuple(t.to(self.device) for t in batch)
            words, attention_mask, token_start_mask, arcs, rels = batch

            # try:
            # ignore [CLS]
            token_start_mask[:, 0] = 0
            # ignore [SEP]
            lens = attention_mask.sum(dim=1) - 1
            token_start_mask[torch.arange(len(token_start_mask)), lens] = 0

            # ignore all punctuation if specified
            if not include_punct:
                puncts = words.new_tensor([punct for punct in self.vocab.puncts])
                token_start_mask &= words.unsqueeze(-1).ne(puncts).all(-1)

            s_arc, s_rel = self.network(words, attention_mask)
            s_arc, s_rel = s_arc[token_start_mask], s_rel[token_start_mask]
            
            gold_arcs, gold_rels = arcs[token_start_mask], rels[token_start_mask]
            pred_arcs, pred_rels = self.decode(s_arc, s_rel)
            
            loss += self.get_loss(s_arc, s_rel, gold_arcs, gold_rels)
            metric(pred_arcs, pred_rels, gold_arcs, gold_rels)

            # except:
            #     for sentence in words:
            #         print(self.tokenizer.convert_ids_to_tokens(sentence.detach().to(torch.device("cpu")).numpy()))
                
            #     print('***DEBUGGING PARSER START***')
            #     self.network.eval()
            #     self.network(words, attention_mask, debug=True)
            #     print('***DEBUGGING PARSER END***')

            #     print('words', words.shape)
            #     print('arcs', arcs.shape)
            #     print('rels', rels.shape)
            #     print('attention_mask', attention_mask.shape)
            #     print('token_start_mask', token_start_mask.shape)

            #     print('s_arc', s_arc.shape)
            #     print('s_rel', s_rel.shape)
            #     print('gold_arcs', gold_arcs.shape)
            #     print('gold_rels', gold_rels.shape)
            #     print('pred_arcs', pred_arcs.shape)
            #     print('pred_rels', pred_rels.shape)
                
            #     raise RuntimeError('evaluation failed.')

        loss /= len(loader)

        return loss, metric

    @torch.no_grad()
    def predict(self, loader):
        self.network.eval()

        all_arcs, all_rels = [], []
        # for words, attention_mask, token_start_mask, arcs, rels in tqdm(loader):
        for words, attention_mask, token_start_mask, arcs, rels in loader:
            # ignore [CLS]
            token_start_mask[:, 0] = 0
            # ignore [SEP]
            lens = attention_mask.sum(dim=1) - 1
            token_start_mask[torch.arange(len(token_start_mask)), lens] = 0

            s_arc, s_rel = self.network(words, attention_mask)
            s_arc, s_rel = s_arc[token_start_mask], s_rel[token_start_mask]
            pred_arcs, pred_rels = self.decode(s_arc, s_rel)

            # lens for splitting
            lens = token_start_mask.sum(dim=1).tolist()

            all_arcs.extend(torch.split(pred_arcs, lens))
            all_rels.extend(torch.split(pred_rels, lens))
        all_arcs = [seq.tolist() for seq in all_arcs]
        all_rels = [self.vocab.id2rel(seq) for seq in all_rels]

        return all_arcs, all_rels

    @torch.no_grad()
    def get_embeddings(self, loader, layer_index=-1, return_all=False, ignore=True, ignore_token_start_mask=False):
        self.network.eval()

        all_embeddings = []
        for words, attention_mask, token_start_mask in loader:
            if ignore_token_start_mask:
                token_start_mask = attention_mask.clone()
 
            if ignore:
                # ignore [CLS]
                token_start_mask[:, 0] = 0
                # ignore [SEP]
                lens = attention_mask.sum(dim=1) - 1
                token_start_mask[torch.arange(len(token_start_mask)), lens] = 0
                
            embed = self.network.get_embeddings(words, attention_mask, layer_index, return_all=return_all)
            
            if return_all:
                embed = torch.stack(embed)        # [num_layer, batch_size, seq_len, bert_dim]
                embed = embed[:,token_start_mask] # [num_layer, num_word, bert_dim]
            else:
                embed = embed[token_start_mask]   # [num_word, bert_dim]      
            
            # lens for splitting
            lens = token_start_mask.sum(dim=1).tolist()
            for sentence_embed in torch.split(embed, lens, dim=-2):
                all_embeddings.append(np.array(sentence_embed.tolist()))
            
        return all_embeddings

    @torch.no_grad()
    def get_concat_embeddings(self, loader):
        self.network.eval()

        all_embeddings = []
        for words, attention_mask, token_start_mask in loader:    
            embed = self.network.get_concat_embeddings(words, attention_mask)
            embed = embed[token_start_mask]   # [num_word, bert_dim]      
            
            # lens for splitting
            lens = token_start_mask.sum(dim=1).tolist()
            for sentence_embed in torch.split(embed, lens, dim=-2):
                all_embeddings.append(np.array(sentence_embed.tolist()))
            
        return all_embeddings

    @torch.no_grad()
    def get_avg_concat_embeddings(self, loader, ignore=True, layer_index=-1):
        self.network.eval()

        all_embeddings = []
        for words, attention_mask, token_start_mask in loader:
            # [batch_size, seq_len, bert_dim]
            embed = self.network.get_concat_embeddings(words, attention_mask)
            
            if ignore:
                # ignore [CLS]
                token_start_mask[:, 0] = 0
                # ignore [SEP]
                lens = attention_mask.sum(dim=1) - 1
                token_start_mask[torch.arange(len(token_start_mask)), lens] = 0
                # need to take care of attention as well since we later rely on attention to do averaging
                attention_mask[torch.arange(len(token_start_mask)), lens] = 0

            for sent_embed, sent_att_mask, sent_mask in zip(embed, attention_mask, token_start_mask):
                sent_avg_embeddings = []
                tmp = None
                tmp_len = 0
                sent_embed = sent_embed.tolist()
                sent_att_mask = sent_att_mask.tolist()
                sent_mask = sent_mask.tolist()
                for word_embed, word_att_mask, word_mask in zip(sent_embed, sent_att_mask, sent_mask):
                    if word_att_mask != 1:
                        if tmp is not None:
                            sent_avg_embeddings.append(tmp/tmp_len)
                        tmp = None
                        break
                    if word_mask == 1:
                        if tmp is not None:
                            if tmp_len == 0:
                                tmp_len = 1
                            sent_avg_embeddings.append(tmp/tmp_len)
                        tmp = np.array(word_embed)
                        tmp_len = 1
                    else:
                        if tmp is not None:
                            tmp += np.array(word_embed)
                            tmp_len += 1

                # take care of last word when sentence len == max_seq_len in batch
                if tmp is not None:
                    sent_avg_embeddings.append(tmp/tmp_len)

                all_embeddings.append(np.array(sent_avg_embeddings))

        return all_embeddings  

    @torch.no_grad()
    def get_avg_embeddings(self, loader, ignore=True, layer_index=-1):
        self.network.eval()

        all_embeddings = []
        for words, attention_mask, token_start_mask in loader:
            # [batch_size, seq_len, bert_dim]
            embed = self.network.get_embeddings(words, attention_mask, layer_index)
            
            if ignore:
                # ignore [CLS]
                token_start_mask[:, 0] = 0
                # ignore [SEP]
                lens = attention_mask.sum(dim=1) - 1
                token_start_mask[torch.arange(len(token_start_mask)), lens] = 0
                # need to take care of attention as well since we later rely on attention to do averaging
                attention_mask[torch.arange(len(token_start_mask)), lens] = 0

            for sent_embed, sent_att_mask, sent_mask in zip(embed, attention_mask, token_start_mask):
                sent_avg_embeddings = []
                tmp = None
                tmp_len = 0
                sent_embed = sent_embed.tolist()
                sent_att_mask = sent_att_mask.tolist()
                sent_mask = sent_mask.tolist()
                for word_embed, word_att_mask, word_mask in zip(sent_embed, sent_att_mask, sent_mask):
                    if word_att_mask != 1:
                        if tmp is not None:
                            sent_avg_embeddings.append(tmp/tmp_len)
                        tmp = None
                        break
                    if word_mask == 1:
                        if tmp is not None:
                            if tmp_len == 0:
                                tmp_len = 1
                            sent_avg_embeddings.append(tmp/tmp_len)
                        tmp = np.array(word_embed)
                        tmp_len = 1
                    else:
                        if tmp is not None:
                            tmp += np.array(word_embed)
                            tmp_len += 1

                # take care of last word when sentence len == max_seq_len in batch
                if tmp is not None:
                    sent_avg_embeddings.append(tmp/tmp_len)

                all_embeddings.append(np.array(sent_avg_embeddings))

        return all_embeddings            

    @torch.no_grad()
    def get_everything(self, loader):
        self.network.eval()

        all_arcs, all_rels, all_embeddings = [], [], []
        for words, attention_mask, token_start_mask in loader:
            # ignore [CLS]
            token_start_mask[:, 0] = 0
            # ignore [SEP]
            lens = attention_mask.sum(dim=1) - 1
            token_start_mask[torch.arange(len(token_start_mask)), lens] = 0

            s_arc, s_rel, embed = self.network.get_everything(words, attention_mask)
            s_arc, s_rel, embed = s_arc[token_start_mask], s_rel[token_start_mask], embed[token_start_mask]
            
            # lens for splitting
            lens = token_start_mask.sum(dim=1).tolist()
            for i, sentence_arc in enumerate(torch.split(s_arc, lens)):
                all_arcs.append(np.array(sentence_arc[:,:lens[i]].tolist()))

            for i, sentence_rel in enumerate(torch.split(s_rel, lens)):
                all_rels.append(np.array(sentence_rel[:,:lens[i]].tolist()))

            for sentence_embed in torch.split(embed, lens, dim=-2):
                all_embeddings.append(np.array(sentence_embed.tolist()))

        return all_arcs, all_rels, all_embeddings

    @torch.no_grad()
    def get_matrices(self, loader):
        self.network.eval()

        all_arcs, all_rels = [], []
        for words, attention_mask, token_start_mask in loader:
            # ignore [CLS]
            token_start_mask[:, 0] = 0
            # ignore [SEP]
            lens = attention_mask.sum(dim=1) - 1
            token_start_mask[torch.arange(len(token_start_mask)), lens] = 0

            s_arc, s_rel = self.network(words, attention_mask)
            s_arc, s_rel = s_arc[token_start_mask], s_rel[token_start_mask]
            
            # lens for splitting
            lens = token_start_mask.sum(dim=1).tolist()
            for i, sentence_arc in enumerate(torch.split(s_arc, lens)):
                all_arcs.append(np.array(sentence_arc[:,:lens[i]].tolist()))

            for i, sentence_rel in enumerate(torch.split(s_rel, lens)):
                all_rels.append(np.array(sentence_rel[:,:lens[i]].tolist()))

        return all_arcs, all_rels

    def get_loss(self, s_arc, s_rel, gold_arcs, gold_rels):
        s_rel = s_rel[torch.arange(len(s_rel)), gold_arcs]

        arc_loss = self.criterion(s_arc, gold_arcs)
        rel_loss = self.criterion(s_rel, gold_rels)
        loss = arc_loss + rel_loss

        return loss

    def decode(self, s_arc, s_rel):
        pred_arcs = s_arc.argmax(dim=-1)
        pred_rels = s_rel[torch.arange(len(s_rel)), pred_arcs].argmax(dim=-1)

        return pred_arcs, pred_rels
