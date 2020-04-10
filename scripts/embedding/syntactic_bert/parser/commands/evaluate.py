# -*- coding: utf-8 -*-

import os
from parser import BiaffineParser, Model
from parser.utils import Corpus, TextDataset, collate_fn

import torch
from torch.utils.data import DataLoader
import subprocess


class Evaluate(object):

    def add_subparser(self, name, parser):
        subparser = parser.add_parser(
            name, help='Evaluate the specified model and dataset.'
        )
        subparser.add_argument('--batch-size', default=200, type=int,
                               help='batch size')
        subparser.add_argument('--include-punct', action='store_true',
                               help='whether to include punctuation')
        subparser.add_argument('--fdata', default='data/test.conllx',
                               help='path to dataset')
        subparser.set_defaults(func=self)

        return subparser

    def __call__(self, args):
        print("Load the model")
        if not os.path.isfile(args.vocab):
            FNULL = open(os.devnull, 'w')
            cloud_address = os.path.join(args.cloud_address, args.vocab)
            # subprocess.call(['gsutil', 'cp', cloud_address, args.vocab], stdout=FNULL, stderr=subprocess.STDOUT)
        vocab = torch.load(args.vocab)
        network = BiaffineParser.load(args.file, args.cloud_address)
        model = Model(vocab, network)

        print("Load the dataset")
        corpus = Corpus.load(args.fdata)
        dataset = TextDataset(vocab.numericalize(corpus))
        # set the data loader
        loader = DataLoader(dataset=dataset,
                            batch_size=args.batch_size,
                            collate_fn=collate_fn)

        print("Evaluate the dataset")
        loss, metric = model.evaluate(loader, include_punct=args.include_punct)
        print(f"Loss: {loss:.4f} {metric}")
