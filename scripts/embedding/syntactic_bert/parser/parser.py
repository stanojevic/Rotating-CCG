# -*- coding: utf-8 -*-

from parser.modules import MLP, Biaffine
from parser.modules.dropout import SharedDropout
from pytorch_pretrained_bert import BertTokenizer, BertModel

import torch
import torch.nn as nn

import subprocess
import os


def length_to_mask(length, max_len=None, dtype=None):
    """length: B.
    return B x max_len.
    If max_len is None, then max of length will be used.
    """
    assert len(length.shape) == 1, 'Length shape should be 1 dimensional.'
    max_len = max_len or length.max().item()
    mask = torch.arange(max_len, device=length.device,
                        dtype=length.dtype).expand(len(length), max_len) < length.unsqueeze(1)
    if dtype is not None:
        mask = torch.as_tensor(mask, dtype=dtype, device=length.device)
    return mask


class BiaffineParser(nn.Module):

    def __init__(self, params):
        super(BiaffineParser, self).__init__()

        self.params = params
        # self.word_dropout = nn.Dropout(p=params['word_dropout'])
        # self.word_dropout_p = params['word_dropout']
        
        # BERT
        self.bert = BertModel.from_pretrained('bert-base-multilingual-cased')
        # self.bert = BertModel.from_pretrained('bert-base-cased')

        self.bert_dropout = SharedDropout(p=params['bert_dropout'])

        # the MLP layers
        self.mlp_arc_h = MLP(n_in=params['n_bert_hidden'],
                             n_hidden=params['n_mlp_arc'],
                             dropout=params['mlp_dropout'])
        self.mlp_arc_d = MLP(n_in=params['n_bert_hidden'],
                             n_hidden=params['n_mlp_arc'],
                             dropout=params['mlp_dropout'])
        self.mlp_rel_h = MLP(n_in=params['n_bert_hidden'],
                             n_hidden=params['n_mlp_rel'],
                             dropout=params['mlp_dropout'])
        self.mlp_rel_d = MLP(n_in=params['n_bert_hidden'],
                             n_hidden=params['n_mlp_rel'],
                             dropout=params['mlp_dropout'])

        # the Biaffine layers
        self.arc_attn = Biaffine(n_in=params['n_mlp_arc'],
                                 bias_x=True,
                                 bias_y=False)
        self.rel_attn = Biaffine(n_in=params['n_mlp_rel'],
                                 n_out=params['n_rels'],
                                 bias_x=True,
                                 bias_y=True)
        self.pad_index = params['pad_index']
        
    def forward(self, words, mask, debug=False):
        # get the mask and lengths of given batch
        lens = mask.sum(dim=1)
        
        # no word dropout for now
        # if self.training:
        #     x_ = self.word_dropout(words.float())
        #     words = x_.mul(1-self.word_dropout_p).long()  
        
        # get outputs from bert
        embed, _ = self.bert(words, attention_mask=mask, output_all_encoded_layers=False)
        del _
        x = embed

        if debug:
            print('words', words.shape)
            print('bert output', x.shape)

        # bert dropout
        x = self.bert_dropout(x)

        # apply MLPs to the BERT output states
        arc_h = self.mlp_arc_h(x)
        arc_d = self.mlp_arc_d(x)
        rel_h = self.mlp_rel_h(x)
        rel_d = self.mlp_rel_d(x)

        if debug:
            print('arc_h', arc_h.shape)
            print('arc_d', arc_d.shape)
            print('rel_h', rel_h.shape)
            print('rel_d', rel_d.shape)

        # get arc and rel scores from the bilinear attention
        # [batch_size, seq_len, seq_len]
        s_arc = self.arc_attn(arc_d, arc_h)
        # [batch_size, seq_len, seq_len, n_rels]
        s_rel = self.rel_attn(rel_d, rel_h).permute(0, 2, 3, 1)

        if debug:
            print('s_arc', s_arc.shape)
            print('s_rel', s_rel.shape)

        # set the scores that exceed the length of each sentence to -inf
        len_mask = length_to_mask(lens, max_len=words.shape[-1], dtype=torch.uint8)
        s_arc.masked_fill_((1 - len_mask).unsqueeze(1), float('-inf'))

        return s_arc, s_rel

    def get_embeddings(self, words, mask, layer_index=-1, return_all=False):
        # get outputs from bert
        encoded_layers, _ = self.bert(words, attention_mask=mask, output_all_encoded_layers=True)
        del _
        if return_all:
            return encoded_layers
        else:
            return encoded_layers[layer_index]

    def get_concat_embeddings(self, words, mask):
        # get outputs from bert
        x, _ = self.bert(words, attention_mask=mask, output_all_encoded_layers=False)
        del _
        arc_h = self.mlp_arc_h(x)
        arc_d = self.mlp_arc_d(x)
        rel_h = self.mlp_rel_h(x)
        rel_d = self.mlp_rel_d(x)
        return torch.cat((arc_h, arc_d, rel_h, rel_d), -1)

    def get_everything(self, words, mask, layer_index=-1, return_all=False):
        # get the mask and lengths of given batch
        lens = mask.sum(dim=1)
        
        # word dropout
        # if self.training:
        #     x_ = self.word_dropout(words.float())
        #     words = x_.mul(1-self.word_dropout_p).long()  
        
        # get outputs from bert
        encoded_layers, _ = self.bert(words, attention_mask=mask, output_all_encoded_layers=True)
        del _
        if return_all:
            embed_to_return = encoded_layers
        else:
            embed_to_return = encoded_layers[:,layer_index]
        
        embed, _ = self.bert(words, attention_mask=mask, output_all_encoded_layers=False)
        del _
        x = embed

        # bert dropout
        x = self.bert_dropout(x)

        # apply MLPs to the BERT output states
        arc_h = self.mlp_arc_h(x)
        arc_d = self.mlp_arc_d(x)
        rel_h = self.mlp_rel_h(x)
        rel_d = self.mlp_rel_d(x)

        # get arc and rel scores from the bilinear attention
        # [batch_size, seq_len, seq_len]
        s_arc = self.arc_attn(arc_d, arc_h)
        # [batch_size, seq_len, seq_len, n_rels]
        s_rel = self.rel_attn(rel_d, rel_h).permute(0, 2, 3, 1)

        # set the scores that exceed the length of each sentence to -inf
        len_mask = length_to_mask(lens, max_len=words.shape[-1], dtype=torch.uint8)
        s_arc.masked_fill_((1 - len_mask).unsqueeze(1), float('-inf'))

        return s_arc, s_rel, embed

    @classmethod
    def load(cls, fname, cloud_address=None, local_rank=0):
        # print("I'm loading now haha. This is {}".format(local_rank))
        # Copy from cloud if there's no saved checkpoint
        if not os.path.isfile(fname):
            if cloud_address:
                FNULL = open(os.devnull, 'w')
                cloud_address = os.path.join(cloud_address, fname)
                # subprocess.call(['gsutil', 'cp', cloud_address, fname], stdout=FNULL, stderr=subprocess.STDOUT)
        # Proceed only if either [1] copy success [2] local file already exists
        if os.path.isfile(fname):
            if torch.cuda.is_available():
                device = torch.device('cuda')
            else:
                device = torch.device('cpu')
            state = torch.load(fname, map_location='cpu')
            network = cls(state['params'])
            network.load_state_dict(state['state_dict'])
            network.to(device)
            print('Loaded model from checkpoint (local rank {})'.format(local_rank))
        else:
            raise IOError('Local checkpoint does not exists. Failed to load model.')

        return network

    def save(self, fname, epoch, cloud_address, optimizer, max_metric, local_rank=0):
        state = {
            'params': self.params,
            'state_dict': self.state_dict(),
            'last_epoch': epoch,
            'optimizer': optimizer.state_dict(),
            'max_metric': max_metric,
        }
        torch.save(state, fname)
        print("Model saved (local rank {})".format(local_rank))
        # Save a copy to cloud as well
        # FNULL = open(os.devnull, 'w')
        # cloud_address = os.path.join(cloud_address, fname)
        # subprocess.call(['gsutil', 'cp', fname, cloud_address], stdout=FNULL, stderr=subprocess.STDOUT)
