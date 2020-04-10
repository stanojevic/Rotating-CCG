#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from config import Config
from parser import BiaffineParser, Model
from parser.utils import Corpus, TextDataset, Vocab, collate_fn
import torch
from torch.utils.data import DataLoader
from tqdm import tqdm
import torch.nn.functional as F
import h5py
import numpy as np
from pytorch_pretrained_bert import BertTokenizer, BertModel, BertForMaskedLM, WordpieceTokenizer

from collections import namedtuple
from sys import stderr

# SynBERT_syntactic_bert_model = None

SynBERT_BATCH_SIZE = 8		# only affects speed, if too big you could OOM
SynBERT_vocab = Vocab([], [], [])

MModel = namedtuple('MModel', ['model', 'layer', 'normalized', 'average_embs'])
MAX_SENT_LEN=80

# SynBERT_AVERAGE_EMBEDDINGS=True
# SynBERT_LAYER=8
# SynBERT_NORMALIZED=False

def SynBERT_load_model(syntactic_checkpoint, layer, normalized, average_embs):
	# what's in the params won't affect embeddings, they're just here so that my initialization code doesn't break
	params = {
		'n_words': SynBERT_vocab.n_train_words,
		'n_chars': SynBERT_vocab.n_chars,
		'word_dropout': Config.word_dropout,
		'n_bert_hidden': Config.n_bert_hidden,
		'bert_dropout': Config.bert_dropout,
		'n_mlp_arc': Config.n_mlp_arc,
		'n_mlp_rel': Config.n_mlp_rel,
		'mlp_dropout': Config.mlp_dropout,
		'n_rels': SynBERT_vocab.n_rels,
		'pad_index': SynBERT_vocab.pad_index
	}
	if syntactic_checkpoint is None:
		network = BiaffineParser(params)              # if you want to use the original BERT
	else:
		network = BiaffineParser.load(syntactic_checkpoint) # if you want to use the tuned BERT

	if torch.cuda.is_available():
		network.to(torch.device('cuda'))

	bert_model = Model(SynBERT_vocab, network)
	return MModel(bert_model, layer, normalized, average_embs)

def SynBERT_embed_sentences(model, sentences):
	
	too_long = []
	normal   = []
	my_errs = ""
	for i, sent in enumerate(sentences):
		if len(sent)>MAX_SENT_LEN:
			too_long.append(i)
			my_errs += "SynBERT ERROR EXCEEDS MAX LEN %d: %s\n"%(len(sent), " ".join(sent))
		else:
			normal.append(sent)
	embs, errors = SynBERT_embed_sentences_FOR_REAL(model, normal)
	final_embs = []
	curr_too_long = 0
	curr_normal   = 0
	for i in range(0, len(sentences)):
		if curr_too_long<len(too_long) and too_long[curr_too_long] == i:
			new_sent_emb = []
			for k in range(0, len(sentences[i])):
				new_sent_emb.append(np.zeros(768, dtype=np.float32))
			final_embs.append(new_sent_emb)
			curr_too_long += 1
		else:
			final_embs.append(embs[curr_normal])
			curr_normal += 1
	return final_embs, my_errs+errors
			

def SynBERT_embed_sentences_FOR_REAL(model, sentences):
	dataset = TextDataset(SynBERT_vocab.numericalize_sentences(sentences))
	loader = DataLoader(dataset=dataset,
						batch_size=SynBERT_BATCH_SIZE,
						collate_fn=collate_fn)
	if model.average_embs:
		# get_avg_embeddings returns the avg embedding of the subwords as the embedding for the whole word.
		# It also has a ignore flag that defaults to True.
		embeddings = model.model.get_avg_embeddings(loader, layer_index=model.layer, ignore=True)
	else:
		# get_embeddings returns the embedding of the first subword as the embedding for the whole word.
		# set ignore=True to not return embeddings for start-of-sentence ([CLS]) and end-of-sentence ([SEP]) tokens
		# set return_all=True to return embeddings for all 12 layers [batch_size, num_layer, num_word, hidden_dim], 
		#     return_all=False to return only layer_index layer
		# set ignore_token_start_mask=True if you want to return token-level (instead of word-level) embeddings
		# default is layer_index=-1, ignore=True, return_all=False, ignore_token_start_mask=False
		# all the named arguments are optional
		embeddings = model.model.get_embeddings(loader, layer_index=model.layer, return_all=False, ignore=True, ignore_token_start_mask=False)
	embeddings = SynBERT_convert_to_my_format(embeddings)
	if model.normalized:
		embeddings = list(map(SynBERT_normalize_embedings_list, embeddings))
	errors = ""
	for i, sentence in enumerate(sentences):
		if i >= len(embeddings):
			errors += "SynBERT ERROR FAILED SENTENCE: %s\n"%(" ".join(sentence))
			new_sent_emb = []
			for k in range(0, len(sentence)):
				new_sent_emb.append(np.zeros(768, dtype=np.float32))
			embeddings.append(new_sent_emb)
		if len(embeddings[i]) > len(sentence):
			errors += "SynBERT ERROR TOO MANY EMBEDDINGS %d %d: %s\n"%(len(embeddings[i]), len(sentence), " ".join(sentence))
			embeddings[i] = embeddings[i][0:len(sentence)]
		if len(embeddings[i]) < len(sentence):
			errors += "SynBERT ERROR MISSING EMBEDDINGS %d %d: %s\n"%(len(embeddings[i]), len(sentence), " ".join(sentence))
			for k in range(0, len(sentence)-len(embeddings[i])):
				embeddings[i].append(np.zeros(768, dtype=np.float32))
	return embeddings, errors


def SynBERT_convert_to_my_format(embeddings):
	return list(map(lambda es: list(map(lambda e : np.array(e, dtype=np.float32), es.tolist())), embeddings))


def SynBERT_normalize_vec(vec):
    norm = np.linalg.norm(vec, 2)
    if(norm == 0):
        return vec
    else:
        return vec/norm

def SynBERT_normalize_embedings_list(embeddings):
    if embeddings is None:
        return None
    else:
        embeddings_new = []
        for e in embeddings:
            embeddings_new.append(SynBERT_normalize_vec(e))
        return embeddings_new

# sentences = [
#   ['Hello', 'there'],
#   ['What', 'is', 'this'],
# ]
# orig_model = SynBERT_load_model(layer=8, normalized=False, average_embs=False, syntactic_checkpoint=None)
# syn_model  = SynBERT_load_model(layer=8, normalized=False, average_embs=False, syntactic_checkpoint='/home/milos/Projects/SyntacticBERT/syntactic_checkpoint.pt')
# 
# print("first original", file=stderr)
# orig_result, orig_errors = SynBERT_embed_sentences(orig_model, sentences)
# print(orig_errors, file=stderr)
# print(orig_result, file=stderr)
# 
# print("now syntactic", file=stderr)
# syn_result, syn_errors = SynBERT_embed_sentences(syn_model, sentences)
# print(syn_errors, file=stderr)
# print(syn_result, file=stderr)
