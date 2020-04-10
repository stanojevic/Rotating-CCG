# -*- coding: utf-8 -*-

from collections import namedtuple
from collections import defaultdict

import torch
import subprocess
import os

Sentence = namedtuple(typename='Sentence',
                      field_names=['ID', 'FORM', 'LEMMA', 'CPOS',
                                   'POS', 'FEATS', 'HEAD', 'DEPREL',
                                   'PHEAD', 'PDEPREL'])


class Corpus(object):
    ROOT = '<ROOT>'

    def __init__(self, sentences):
        super(Corpus, self).__init__()

        self.sentences = sentences

    def __len__(self):
        return len(self.sentences)

    def __repr__(self):
        return '\n'.join(
            '\n'.join('\t'.join(map(str, i)) for i in zip(*sentence)) + '\n'
            for sentence in self
        )

    def __getitem__(self, index):
        return self.sentences[index]

    @property
    def words(self):
        return [[self.ROOT] + [word for word in sentence.FORM]
                for sentence in self.sentences]

    @property
    def heads(self):
        # flag = False
        # for s in self.sentences:
            
        #     if '_' in s.HEAD:
        #         print(s.ID)
        #         flag = True
        #         # print(s)
        # if flag:
        #     assert 1 == 2

        return [[0] + list(map(int, sentence.HEAD))
                for sentence in self.sentences]

    @property
    def tags(self):
        return [[self.ROOT] + list(sentence.CPOS)
                for sentence in self.sentences]
                
    @property
    def rels(self):
        return [[self.ROOT] + list(sentence.DEPREL)
                for sentence in self.sentences]

    @heads.setter
    def heads(self, sequences):
        self.sentences = [sentence._replace(HEAD=sequence)
                          for sentence, sequence in zip(self, sequences)]

    @rels.setter
    def rels(self, sequences):
        self.sentences = [sentence._replace(DEPREL=sequence)
                          for sentence, sequence in zip(self, sequences)]

    @classmethod
    def load(cls, fname):
        start, sentences = 0, []
        with open(fname, 'r', encoding='utf-8') as f:
            lines = f.readlines()

        for i, line in enumerate(lines):
            if line[0] == '#':
                start += 1
            if len(line) <= 1:
                sentence = Sentence(*zip(*[l.split('\t') for l in lines[start:i] if "." not in l.split('\t')[0] and "-" not in l.split('\t')[0]]))
                sentences.append(sentence)
                start = i + 1

        corpus = cls(sentences)

        return corpus

    def save(self, fname, cloud_address):
        with open(fname, 'w') as f:
            f.write(f"{self}\n")
        FNULL = open(os.devnull, 'w')
        cloud_address = os.path.join(cloud_address, fname)
        # subprocess.call(['gsutil', 'cp', fname, cloud_address],
        #                 stdout=FNULL, stderr=subprocess.STDOUT)
