# -*- coding: utf-8 -*-

import os
from parser import BiaffineParser, Model
from parser.utils import Corpus, TextDataset, collate_fn

import torch
from torch.utils.data import DataLoader
import subprocess


class Predict(object):

    def add_subparser(self, name, parser):
        subparser = parser.add_parser(
            name, help='Use a trained model to make predictions.'
        )
        subparser.add_argument('--batch-size', default=200, type=int,
                               help='batch size')
        subparser.add_argument('--fdata', default='data/test.conllx',
                               help='path to dataset')
        subparser.add_argument('--fpred', default='pred.conllx',
                               help='path to predicted result')
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

        print("Predict the dataset")
        corpus.heads, corpus.rels = model.predict(loader)

        print(f"Save the predicted result")
        corpus.save(args.fpred, args.cloud_address)
