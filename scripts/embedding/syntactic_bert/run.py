# -*- coding: utf-8 -*-

import os
import argparse
from parser.commands import Evaluate, Predict, Train

import torch
import logging


if __name__ == '__main__':
    # logging.basicConfig(level=logging.INFO)
    # torch.set_printoptions(threshold=10000)
    
    parser = argparse.ArgumentParser(
        description='Create the Biaffine Parser model.'
    )
    parser.add_argument('--local_rank', '-l', default=0, type=int,
                         help='local rank for distributed training')

    subparsers = parser.add_subparsers(title='Commands')
    subcommands = {
        'evaluate': Evaluate(),
        'predict': Predict(),
        'train': Train()
    }
    for name, subcommand in subcommands.items():
        subparser = subcommand.add_subparser(name, subparsers)
        # subparser.add_argument('--device', '-d', default='-1',
        #                        help='ID of GPU to use')
        subparser.add_argument('--seed', '-s', default=1, type=int,
                               help='seed for generating random numbers')
        # subparser.add_argument('--threads', '-t', default=4, type=int,
        #                        help='max num of threads')
        subparser.add_argument('--file', '-f', default='model.pt',
                               help='path to model file')
        subparser.add_argument('--vocab', '-v', default='vocab.pt',
                               help='path to vocabulary file')
        subparser.add_argument('--cloud_address', '-c',
                               default='gs://no_cloud_by_default/',
                               help='path to Google Cloud Storage')
    args = parser.parse_args()

    # FOR DISTRIBUTED:  If we are running under torch.distributed.launch,
    # the 'WORLD_SIZE' environment variable will also be set automatically.
    args.distributed = False
    if 'WORLD_SIZE' in os.environ:
        args.distributed = int(os.environ['WORLD_SIZE']) > 1

    if args.distributed:
        if args.local_rank == 0:
            print('Distributed training enabled.')
        # FOR DISTRIBUTED:  Set the device according to local_rank.
        torch.cuda.set_device(args.local_rank)

        # FOR DISTRIBUTED:  Initialize the backend.  torch.distributed.launch will provide
        # environment variables, and requires that you use init_method=`env://`.
        torch.distributed.init_process_group(backend='nccl',
                                             init_method='env://')

    torch.backends.cudnn.benchmark = True

    # if args.local_rank == 0:
        # print(f"Set the max num of threads to {args.threads}")
        # print(f"Set the seed for generating random numbers to {args.local_rank}")
        # print(f"Set the device with ID {args.device} visible")
    
    # torch.set_num_threads(args.threads)
    # torch.manual_seed(args.seed)
    torch.manual_seed(args.local_rank)
    n_gpu = torch.cuda.device_count()
    if args.local_rank == 0:
        print("\nCUDNN VERSION: {}\n".format(torch.backends.cudnn.version()))
    
    if n_gpu > 0:
        torch.cuda.manual_seed_all(args.seed)
    
    args.func(args)
