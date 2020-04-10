# -*- coding: utf-8 -*-

from parser import BiaffineParser, Model
from parser.utils import Corpus, Vocab

import torch

import os
import argparse
import subprocess
from datetime import datetime, timedelta


parser = argparse.ArgumentParser(
    description='Data preprocessing module.'
)

parser.add_argument('--ftrain', default='data/train.conllx',
                    help='path to train file')
parser.add_argument('--fdev', default='data/dev.conllx',
                    help='path to dev file')
parser.add_argument('--ftest', default='data/test.conllx',
                    help='path to test file')
parser.add_argument('--ftrain_cache', default='data/binary/trainset',
                    help='path to train file cache')
parser.add_argument('--fdev_cache', default='data/binary/devset',
                    help='path to dev file cache')
parser.add_argument('--ftest_cache', default='data/binary/testset',
                    help='path to test file cache')
parser.add_argument('--vocab', '-v', default='vocab.pt',
                    help='path to vocabulary file')
parser.add_argument('--cloud_address', '-c',
                    default='gs://no_you_dont_want_cloud_bro',
                    help='path to Google Cloud Storage')

# parser.set_defaults(func=self)
args = parser.parse_args()

print("***Start preprocessing the data at {}***".format(datetime.now()))

train = Corpus.load(args.ftrain)
dev = Corpus.load(args.fdev)
# test = Corpus.load(args.ftest)

if not os.path.isfile(args.vocab):
    FNULL = open(os.devnull, 'w')
    cloud_address = os.path.join(args.cloud_address, args.vocab)
    # subprocess.call(['gsutil', 'cp', cloud_address, args.vocab],
    #                 stdout=FNULL, stderr=subprocess.STDOUT)
if not os.path.isfile(args.vocab):
    print("***Loading vocab from scratch.")
    vocab = Vocab.from_corpus(corpus=train, min_freq=2)
    torch.save(vocab, args.vocab)
    FNULL = open(os.devnull, 'w')
    cloud_address = os.path.join(args.cloud_address, args.vocab)
    # subprocess.call(['gsutil', 'cp', args.vocab, cloud_address],
    #                 stdout=FNULL, stderr=subprocess.STDOUT)
else:
    print("***Loading vocab from checkpoint.")
    vocab = torch.load(args.vocab)

print("***Start loading the dataset at {}***".format(datetime.now()))
if not os.path.isfile(args.ftrain_cache):
    print('Loading trainset from scratch.')
    vocab.numericalize(train, args.ftrain_cache)
    print('***trainset loaded at {}***'.format(datetime.now()))
else:
    print('trainset already exists.')

if not os.path.isfile(args.fdev_cache):
    print('Loading devset from scratch.')
    vocab.numericalize(dev, args.fdev_cache)
    print('***devset loaded at {}***'.format(datetime.now()))
else:
    print('devset already exists.')
# if not os.path.isfile(args.ftest_cache):
#     print('Loading testset from scratch.')
#     vocab.numericalize(test, args.ftest_cache)
#     print('***testset loaded at {}***'.format(datetime.now()))
# else:
#     print('testset already exists.')
print('Data preprocessing done.')