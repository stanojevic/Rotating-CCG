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


BATCH_SIZE = 8				# only affects speed, if too big you could OOM
CHECKPOINT_DIR = 'model.pt' # path to model checkpoint
vocab = Vocab([], [], [])

# what's in the params won't affect embeddings, they're just here so that my initialization code doesn't break
params = {
	'n_words': vocab.n_train_words,
	'n_chars': vocab.n_chars,
	'word_dropout': Config.word_dropout,
	'n_bert_hidden': Config.n_bert_hidden,
	'bert_dropout': Config.bert_dropout,
	'n_mlp_arc': Config.n_mlp_arc,
	'n_mlp_rel': Config.n_mlp_rel,
	'mlp_dropout': Config.mlp_dropout,
	'n_rels': vocab.n_rels,
	'pad_index': vocab.pad_index
}
network = BiaffineParser(params)			  			# if you want to use the original BERT
# syntactic_network = BiaffineParser.load(CHECKPOINT_DIR) # if you want to use the tuned BERT

if torch.cuda.is_available():
	network.to(torch.device('cuda'))
	# syntactic_network.to(torch.device('cuda'))

model = Model(vocab, network)
# syntactic_model = Model(vocab, syntactic_network)

# sentences = [['Yes', 'yes', 'yes'], ["It's", 'all', 'done', ':)', '.']]
# example(sentences)

# PennTreebank('data/dev.conllx', 'embeddings.tsv', 'meta.tsv')

corpus = {
	'train_path': 'data/train',
	'dev_path': 'data/dev',
	'test_path': 'data/test'
}

my_embeddings = {
	'train_path': 'data/train.bert-layers.hdf5',
	'dev_path': 'data/dev.bert-layers.hdf5',
	'test_path': 'data/test.bert-layers.hdf5',
}


def example(sentences):
	'''
	Demos how to extract embeddings or matrices from sentences.
	'''
	dataset = TextDataset(vocab.numericalize_sentences(sentences))
	loader = DataLoader(dataset=dataset,
						batch_size=BATCH_SIZE,
						collate_fn=collate_fn)

	# get_embeddings returns the embedding of the first subword as the embedding for the whole word.
	# set ignore=True to not return embeddings for start-of-sentence ([CLS]) and end-of-sentence ([SEP]) tokens
	# set return_all=True to return embeddings for all 12 layers [batch_size, num_layer, num_word, hidden_dim], 
	#     return_all=False to return only layer_index layer
	# set ignore_token_start_mask=True if you want to return token-level (instead of word-level) embeddings
	# default is layer_index=-1, ignore=True, return_all=False, ignore_token_start_mask=False
	# all the named arguments are optional
	embeddings = model.get_embeddings(loader, layer_index=8, return_all=False, ignore=True, ignore_token_start_mask=False)
	    
	
	# get_avg_embeddings returns the avg embedding of the subwords as the embedding for the whole word.
	# It also has a ignore flag that defaults to True.
	avg_embeddings = model.get_avg_embeddings(loader, layer_index=8, ignore=True)

	s_arc, s_rel = model.get_matrices(loader)
	
	# Don't use this this doesn't work
	# s_arc, s_rel, embeddings = model.get_everything(loader)


	# FOR DEBUGGING
	# tokenizer = BertTokenizer.from_pretrained('bert-base-multilingual-cased', do_lower_case=False)
	# print(tokenizer.tokenize(' '.join(sentences[0])))
	# print(embeddings[0].shape)
	# print(avg_embeddings[0].shape)
	# print(embeddings[0][:,:3])
	# print(avg_embeddings[0][:,:3])

	# print(tokenizer.tokenize(' '.join(sentences[1])))
	# print(embeddings[1].shape)
	# print(avg_embeddings[1].shape)
	# print(embeddings[1][:,:3])
	# print(avg_embeddings[1][:,:3])


# def PennTreebank(corpus_path, out_file, meta_file):
# 	'''
# 	Extracts embeddings and labels for visualization
# 	'''
# 	corpus = Corpus.load(corpus_path)
# 	vocab = Vocab.from_corpus(corpus=corpus, min_freq=2)
# 	a, b, c, words, tags = vocab.numericalize_tags(corpus)
# 	dataset = TextDataset((a, b, c))
# 	loader = DataLoader(dataset=dataset,
# 						batch_size=BATCH_SIZE,
# 						collate_fn=collate_fn)
# 	original_embeddings = model.get_embeddings(loader)
# 	syntactic_embeddings = syntactic_model.get_embeddings(loader)
# 	with open(out_file, 'w') as f, open(meta_file, 'w') as ff:
# 		embeddings = []
# 		embeddings2 = []
# 		for sentence in tqdm(original_embeddings):
# 			for word_embed in sentence:
# 				embeddings.append(torch.FloatTensor(word_embed))
# 		for sentence in tqdm(syntactic_embeddings):
# 			for word_embed in sentence:
# 				embeddings2.append(torch.FloatTensor(word_embed))

# 		embeddings = torch.stack(embeddings)
# 		embeddings2 = torch.stack(embeddings2)

# 		embeddings = torch.cat([embeddings, embeddings2], dim=0)
# 		embeddings = F.normalize(embeddings, p=2, dim=1).tolist()

# 		for embedding in tqdm(embeddings):
# 			f.write('\t'.join([str(val) for val in embedding])+'\n')
		

# 		ff.write('Word\tPOS\n')
# 		for sentence, sentence_tags in tqdm(zip(words, tags)):
# 			for word, tag in zip(sentence, sentence_tags):
# 				ff.write('original_' + word + '\t' + 'original_' + tag + '\n')

# 		for sentence, sentence_tags in tqdm(zip(words, tags)):
# 			for word, tag in zip(sentence, sentence_tags):
# 				ff.write('syntactic_' + word + '\t' + 'syntactic_' + tag + '\n')

def write_hdf5(input_path, output_path, model, all_tokens):
	'''
	Extracts embeddings to a format compatible with structural probes
	'''
	LAYER_COUNT = 12
	BATCH_SIZE = 1
	word_piece = False # Use word_piece tokenizer instead of full tokenizer

	# tokenizer = BertTokenizer.from_pretrained('bert-base-multilingual-cased', do_lower_case=False)
	tokenizer = BertTokenizer.from_pretrained('bert-base-cased', do_lower_case=False)

	fouts = []
	for layer_index in range(LAYER_COUNT):
		fout = h5py.File(output_path + str(layer_index), 'w')
		fouts.append(fout)

	
	# with h5py.File(output_path, 'w') as fout:
	for index, line in enumerate(open(input_path)):
		line = line.strip()
		line = '[CLS] ' + line + ' [SEP]'
		if word_piece:
			tokenized_text = tokenizer.wordpiece_tokenizer.tokenize(line)
		else:
			tokenized_text = tokenizer.tokenize(line)
		indexed_tokens = torch.tensor(tokenizer.convert_tokens_to_ids(tokenized_text))
		attention_mask = torch.ByteTensor([1 for x in tokenized_text])
		
		if all_tokens: # return subword-level embedding rather than word-level embedding
			token_start_mask = torch.ByteTensor([1 for x in tokenized_text])
		else:
			# construct token_start_mask to get word-level embedding
			token_start_mask = []
			for word in line.split():
				if word_piece:
					tokens = tokenizer.wordpiece_tokenizer.tokenize(word)
				else:
					tokens = tokenizer.tokenize(word)
				if tokens:
					token_start_mask.extend([1]+[0]*(len(tokens)-1))
			if index < 5:
				print('len token start mask ', len(token_start_mask))
				print('sum token start mask ', np.array(token_start_mask).sum())
			token_start_mask = torch.ByteTensor(token_start_mask)

		if torch.cuda.is_available():
			indexed_tokens = indexed_tokens.cuda()
			token_start_mask = token_start_mask.cuda()	
			attention_mask = attention_mask.cuda()
		
		dataset = TextDataset(([indexed_tokens], [attention_mask], [token_start_mask]))
		loader = DataLoader(dataset=dataset,
							batch_size=BATCH_SIZE)
		# embeddings = model.get_avg_embeddings(loader, layer_index=8)
		# embeddings = model.get_avg_concat_embeddings(loader)
		# embeddings = model.get_embeddings(loader, layer_index=8)
		# embeddings = model.get_embeddings(loader, return_all=True)
		
		embeddings = model.get_embeddings(loader, return_all=True)
		embeddings = np.array(embeddings[0])
		embed = embeddings

		for layer_index in range(LAYER_COUNT):
			fout = fouts[layer_index]
			embed = embeddings[layer_index]
			
			dset = fout.create_dataset(str(index), (1, embed.shape[-2], embed.shape[-1]))
			dset[:,:,:] = embed
		
		# embed = np.array(embeddings[0])

		if index % 1000 == 0:
			print('Processing sentence {}...'.format(index))
		# if index < 5:
		# 	print(tokenized_text)
		# 	print('token_start_mask ', token_start_mask)
		# 	print('Len of tokens: {}'.format(len(tokenized_text)))
		# 	print('Len of original: ', len(line.split()))
		# 	print('embed shape: {}\n'.format(embed.shape))
		
		# if all_tokens:
		# 	assert len(tokenized_text) == embed.shape[-2]
		# else:
		# 	assert len(line.split()) - 2 == embed.shape[-2]
		
		# dset = fout.create_dataset(str(index), (LAYER_COUNT, embed.shape[-2], embed.shape[-1]))
		# dset[:,:,:] = embed


		# This converts all at once. Works on small datasets, but will OOM on PTB
		# corpus = Corpus.load(input_path)
		# print('corpus loaded')
		# vocab = Vocab.from_corpus(corpus=corpus, min_freq=2)
		# print('vocab loaded')
		# a, b, c, words, tags = vocab.numericalize_tags(corpus)
		# print('vocab numericalized')
		# dataset = TextDataset((a, b, c))
		# print('dataset loaded')
		# loader = DataLoader(dataset=dataset,
		#                     batch_size=BATCH_SIZE,
		#                     collate_fn=collate_fn)
		# print('loader loaded')
		# embeddings = model.get_embeddings(loader)
		# print('embeddings computed')

		# for index, (sentence, embed) in tqdm(enumerate(zip(words, embeddings))):
		# 	dset = fout.create_dataset(str(index), (LAYER_COUNT, len(sentence), FEATURE_COUNT))
		# 	embed = np.array(embed)
		# 	dset[:,:,:] = embed

# for input_path, output_path in zip(corpus.values(), my_embeddings.values()):
# 	print(input_path)
# 	print(output_path)
# 	write_hdf5(input_path, output_path, model=model, all_tokens=False)