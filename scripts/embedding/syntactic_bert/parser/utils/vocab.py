# -*- coding: utf-8 -*-

from collections import Counter, defaultdict

import regex
import torch
import torch.nn as nn
from pytorch_pretrained_bert import BertTokenizer
import numpy as np
import unicodedata
import os


class Vocab(object):
    PAD = '<PAD>'

    def __init__(self, words, chars, rels):
        self.pad_index = 0

        self.words = [self.PAD] + sorted(words)
        self.chars = [self.PAD] + sorted(chars)
        self.rels = sorted(rels)

        self.word_dict = {word: i for i, word in enumerate(self.words)}
        self.char_dict = {char: i for i, char in enumerate(self.chars)}
        self.rel_dict = {rel: i for i, rel in enumerate(self.rels)}

        # ids of punctuation that appear in words
        self.puncts = set(sorted(i for word, i in self.word_dict.items()
                                if regex.match(r'\p{P}+$', word)))

        self.n_words = len(self.words)
        self.n_chars = len(self.chars)
        self.n_rels = len(self.rels)
        self.n_train_words = self.n_words

        self.tokenizer = BertTokenizer.from_pretrained('bert-base-multilingual-cased', do_lower_case=False)
        # self.tokenizer = BertTokenizer.from_pretrained('bert-base-cased', do_lower_case=False)

    def __repr__(self):
        info = f"{self.__class__.__name__}(\n"
        info += f"  num of words: {self.n_words}\n"
        info += f"  num of chars: {self.n_chars}\n"
        info += f"  num of rels: {self.n_rels}\n"
        info += f")"

        return info

    def rel2id(self, sequence):
        return torch.tensor([self.rel_dict.get(rel, 0)
                             for rel in sequence])

    def id2rel(self, ids):
        return [self.rels[i] for i in ids]

    def extend(self, words):
        self.words += sorted(set(words).difference(self.word_dict))
        self.chars += sorted(set(''.join(words)).difference(self.char_dict))
        self.word_dict = {w: i for i, w in enumerate(self.words)}
        self.char_dict = {c: i for i, c in enumerate(self.chars)}
        self.puncts = set(sorted(i for word, i in self.word_dict.items()
                                if regex.match(r'\p{P}+$', word)))
        self.n_words = len(self.words)
        self.n_chars = len(self.chars)

    def numericalize(self, corpus, save_name=None):
        words_numerical = []
        arcs_numerical = []
        rels_numerical = []
        token_start_mask = []
        attention_mask = []
        offending_set = set()
        symbol_set = set()
        empty_words = set()
        len_dict = defaultdict(int)
        sent_count = 0
        exceeding_count = 0
        for words, arcs, rels in zip(corpus.words, corpus.heads, corpus.rels):
            sentence_token_ids = []
            sentence_arc_ids = []
            sentence_rel_ids = []
            token_starts = []
            attentions = []
            words = ['[CLS]'] + words + ['[SEP]']
            arcs = [0] + arcs + [0]
            rels = ['<ROOT>'] + rels + ['<ROOT>']
            for word, arc, rel in zip(words, arcs, rels):
                # skip <ROOT>
                if word == '<ROOT>':
                    continue
                
                # take care of some idiosyncracies
                if word == '`':
                    word = "'"
                if word == '``':
                    word = '"'
                if word == "''":
                    word = '"'
                if word == "non-``":
                    word = 'non-"'
                word = word.replace('“', '"')
                word = word.replace('”', '"')
                word = word.replace("`", "'")
                word = word.replace("’", "'")
                word = word.replace("‘", "'")
                word = word.replace("'", "'")
                word = word.replace("´", "'")
                word = word.replace("…", "...")
                word = word.replace("–", "-")
                word = word.replace('—', '-')


                tokens = self.tokenizer.tokenize(word)                
                if tokens:
                    ids = self.tokenizer.convert_tokens_to_ids(tokens)

                    # take care of punctuation
                    if regex.match(r'\p{P}+$', word):
                        for token_id in ids:
                            self.puncts.add(token_id)

                    # log any unknown words
                    if '[UNK]' in tokens:
                        # print('words: ', words)
                        # print('offending word: ', word)
                        # print('offending chars: ')
                        for offending_char in word:
                            token = self.tokenizer.tokenize(offending_char)
                            if '[UNK]' in token:
                                if unicodedata.category(offending_char) != 'So':
                                    offending_set.add(offending_char)
                                else:
                                    symbol_set.add(offending_char)
                        
                    # main thing to do
                    sentence_token_ids.extend(ids)
                    sentence_arc_ids.extend([arc] * len(tokens))
                    sentence_rel_ids.extend([self.rel_dict.get(rel, 0)] * len(tokens))
                    token_starts.extend([1] + [0] * (len(tokens) - 1))
                    attentions.extend([1] * len(tokens))

                # take care of empty tokens
                else:
                    # print('\noffending word: ', word)
                    # print('empty words: ', ' '.join(words))
                    empty_words.add(word)
                    continue
                
            # error checking for lengths
            len_sentence_token_ids = len(sentence_token_ids)
            len_sentence_arc_ids = len(sentence_arc_ids)
            len_sentence_rel_ids = len(sentence_rel_ids)
            len_token_starts = len(token_starts)
            len_attentions = len(attentions)
            if not (len_sentence_token_ids == len_sentence_arc_ids == len_sentence_rel_ids == len_token_starts == len_attentions):
                print(words)
                print(arcs)
                print(rels)
                print('len_sentence_token_ids: ', len_sentence_token_ids)
                print('len_sentence_arc_ids', len_sentence_arc_ids)
                print('len_sentence_rel_ids', len_sentence_rel_ids)
                print('len_token_starts', len_token_starts)
                print('len_attentions', len_attentions)
                raise RuntimeError("Lengths don't match up.")

            # Skip too long sentences
            len_dict[len_sentence_token_ids] += 1
            if len_sentence_token_ids > 128:
                exceeding_count += 1
                continue
            sent_count += 1                

            words_numerical.append(torch.tensor(sentence_token_ids))
            arcs_numerical.append(torch.tensor(sentence_arc_ids))
            rels_numerical.append(torch.tensor(sentence_rel_ids))
            token_start_mask.append(torch.ByteTensor(token_starts))
            attention_mask.append(torch.ByteTensor(attentions))

        if offending_set: 
            print('WARNING: The following non-symbol characters are unknown to BERT:')
            try:
                print(offending_set)
            except:
                pass
        if symbol_set:
            print('WARNING: The following symbol characters are unknown to BERT:')
            try:         
                print(symbol_set)
            except:
                pass
        if empty_words:
            print('WARNING: The following characters are empty after going through tokenizer:')
            try:
                print(empty_words)
            except:
                pass
        if save_name:
            try:
                index = save_name.rfind('/')
                if index > -1:
                    save_dir = save_name[:index]
                    os.makedirs(save_dir)
            except FileExistsError:
                # directory already exists
                pass
            torch.save((words_numerical, attention_mask, token_start_mask, arcs_numerical, rels_numerical), save_name)
        
        print('Total number of sentences: {}'.format(sent_count))
        print('Number of sentences exceeding max seq length of 128: {}'.format(exceeding_count))

        return words_numerical, attention_mask, token_start_mask, arcs_numerical, rels_numerical

    def numericalize_sentences(self, sentences):
        words_numerical = []
        token_start_mask = []
        attention_mask = []
        offending_set = set()
        symbol_set = set()
        empty_words = set()
        exceeding_count = 0
        for sentence in sentences:
            sentence_token_ids = []
            token_starts = []
            attentions = []
            sentence = ['[CLS]'] + sentence + ['[SEP]']
            for word in sentence:
                # skip <ROOT>
                if word == '<ROOT>':
                    continue
                
                # take care of some idiosyncracies
                if word == '`':
                    word = "'"
                if word == '``':
                    word = '"'
                if word == "''":
                    word = '"'
                if word == "non-``":
                    word = 'non-"'
                word = word.replace('“', '"')
                word = word.replace('”', '"')
                word = word.replace("`", "'")
                word = word.replace("’", "'")
                word = word.replace("‘", "'")
                word = word.replace("'", "'")
                word = word.replace("´", "'")
                word = word.replace("…", "...")
                word = word.replace("–", "-")
                word = word.replace('—', '-')


                tokens = self.tokenizer.tokenize(word)
                if tokens:
                    ids = self.tokenizer.convert_tokens_to_ids(tokens)
                    
                    # Keep track of punctuation
                    if regex.match(r'\p{P}+$', word):
                        for token_id in ids:
                            self.puncts.add(token_id)

                    # log any unknown words
                    if '[UNK]' in tokens:
                        for offending_char in word:
                            token = self.tokenizer.tokenize(offending_char)
                            if unicodedata.category(offending_char) != 'So':
                                offending_set.add(offending_char)
                            else:
                                symbol_set.add(offending_char)
                        
                    sentence_token_ids.extend(ids)
                    token_starts.extend([1] + [0] * (len(tokens) - 1))
                    attentions.extend([1] * len(tokens))
                
                # take care of empty tokens
                else:
                    empty_words.add(word)
                    continue

            # Skip too long sentences
            len_sentence_token_ids = len(sentence_token_ids)
            if len_sentence_token_ids > 128:
                exceeding_count += 1
                continue

            words_numerical.append(torch.tensor(sentence_token_ids))
            attention_mask.append(torch.ByteTensor(attentions))
            token_start_mask.append(torch.ByteTensor(token_starts))    
        
        return words_numerical, attention_mask, token_start_mask

    def numericalize_tags(self, corpus):
        words_numerical = []
        words_total = []
        tags_total = []
        token_start_mask = []
        attention_mask = []
        offending_set = set()
        symbol_set = set()
        empty_words = set()
        exceeding_count = 0
        for sentence, words, tags in zip(corpus.words, corpus.words, corpus.tags):
            # skip <ROOT>
            words = words[1:]
            tags = tags[1:]

            sentence_token_ids = []
            token_starts = []
            attentions = []
            sentence = ['[CLS]'] + sentence + ['[SEP]']
            for word in sentence:
                # skip <ROOT>
                if word == '<ROOT>':
                    continue
                
                # take care of some idiosyncracies
                if word == '`':
                    word = "'"
                if word == '``':
                    word = '"'
                if word == "''":
                    word = '"'
                if word == "non-``":
                    word = 'non-"'
                word = word.replace('“', '"')
                word = word.replace('”', '"')
                word = word.replace("`", "'")
                word = word.replace("’", "'")
                word = word.replace("‘", "'")
                word = word.replace("'", "'")
                word = word.replace("´", "'")
                word = word.replace("…", "...")
                word = word.replace("–", "-")
                word = word.replace('—', '-')


                tokens = self.tokenizer.tokenize(word)
                if tokens:
                    ids = self.tokenizer.convert_tokens_to_ids(tokens)
                    
                    # Keep track of punctuation
                    if regex.match(r'\p{P}+$', word):
                        for token_id in ids:
                            self.puncts.add(token_id)

                    # log any unknown words
                    if '[UNK]' in tokens:
                        for offending_char in word:
                            token = self.tokenizer.tokenize(offending_char)
                            if unicodedata.category(offending_char) != 'So':
                                offending_set.add(offending_char)
                            else:
                                symbol_set.add(offending_char)
                        
                    sentence_token_ids.extend(ids)
                    token_starts.extend([1] + [0] * (len(tokens) - 1))
                    attentions.extend([1] * len(tokens))
                
                # take care of empty tokens
                else:
                    empty_words.add(word)
                    continue

            # Skip too long sentences
            len_sentence_token_ids = len(sentence_token_ids)
            if len_sentence_token_ids > 128:
                exceeding_count += 1
                continue

            words_numerical.append(torch.tensor(sentence_token_ids))
            attention_mask.append(torch.ByteTensor(attentions))
            token_start_mask.append(torch.ByteTensor(token_starts))
            words_total.append(words)
            tags_total.append(tags)
            
        return words_numerical, attention_mask, token_start_mask, words_total, tags_total

    @classmethod
    def from_corpus(cls, corpus, min_freq=1):
        words = Counter(word for seq in corpus.words for word in seq)
        words = list(word for word, freq in words.items() if freq >= min_freq)
        chars = list({char for seq in corpus.words for char in ''.join(seq)})
        rels = list({rel for seq in corpus.rels for rel in seq})
        vocab = cls(words, chars, rels)

        return vocab
