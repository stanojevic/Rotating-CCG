#!/usr/bin/env python3

import argparse
from sys import stdin, stderr

layer=2
half_dimension=512

options_file = "https://s3-us-west-2.amazonaws.com/allennlp/models/elmo/2x4096_512_2048cnn_2xhighway/elmo_2x4096_512_2048cnn_2xhighway_options.json"
weight_file = "https://s3-us-west-2.amazonaws.com/allennlp/models/elmo/2x4096_512_2048cnn_2xhighway/elmo_2x4096_512_2048cnn_2xhighway_weights.hdf5"

MAX_BATCH_SIZE = 124

def main(emb_type):

    print("ELMo Loading START", file=stderr)
    from allennlp.commands.elmo import ElmoEmbedder
    embedder = ElmoEmbedder(options_file=options_file, weight_file=weight_file)
    print("ELMo Loading END", file=stderr)

    print("ELMo Computing START", file=stderr)
    minibatch = []
    for i, line in enumerate(stdin):
        if i%100 == 0:
            print("processed %d"%i, file=stderr)
        minibatch.append(line.split())
        if len(minibatch) == MAX_BATCH_SIZE:
            process(embedder, emb_type, minibatch)
            minibatch = []
    if len(minibatch)>0:
        process(embedder, emb_type, minibatch)
    print("ELMo Computing END", file=stderr)

def process(embedder, emb_type, sents):
    ress = embedder.embed_sentences(sents)
    for sent, res in zip(sents, ress):
        print("words %d"%len(sent))
        for word_position in range(len(sent)):
            if emb_type == "forward-top":
                vec = res[layer, word_position, :half_dimension]
            elif emb_type == "backward-top":
                vec = res[layer, word_position, half_dimension:]
            elif emb_type == "concat-top":
                vec = res[layer, word_position, :]
            elif emb_type == "average-top":
                fwd_vec = res[layer, word_position, :half_dimension]
                bck_vec = res[layer, word_position, half_dimension:]
                vec = (fwd_vec+bck_vec)/2
            elif emb_type == "local":
                vec = res[0, word_position, :half_dimension]
            else:
                print("unknown emb_type %s"%emb_type, file=stderr)
                exit()
            print( str(vec.tolist()).lstrip("[ ").rstrip("] ").replace(",", " ") )

if __name__ == "__main__":

    parser = argparse.ArgumentParser()
    parser.add_argument("--emb_type", required=True, type=str, help="Model output directory")
    args = parser.parse_args()

    main(args.emb_type)
