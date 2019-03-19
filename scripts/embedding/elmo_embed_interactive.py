#!/usr/bin/env python3

from allennlp.commands.elmo import ElmoEmbedder
import numpy as np
from sys import stderr

layer=2
word_position=0
half_dimension=512

options_file = "https://s3-us-west-2.amazonaws.com/allennlp/models/elmo/2x4096_512_2048cnn_2xhighway/elmo_2x4096_512_2048cnn_2xhighway_options.json"
weight_file = "https://s3-us-west-2.amazonaws.com/allennlp/models/elmo/2x4096_512_2048cnn_2xhighway/elmo_2x4096_512_2048cnn_2xhighway_weights.hdf5"

embedder = ElmoEmbedder(options_file=options_file, weight_file=weight_file)


emb_type = "forward-top" # "concat-top", "backward", "average-top", "local"


left_to_batch = 1
batch = []


while True:
    s = input(">>> ")
    if s.startswith("!!! to batch = "):
        left_to_batch = int(s.replace("!!! to batch = ", ""))
    elif s.startswith("!!! emb_type = "):
        emb_type = s.replace("!!! emb_type = ", "")
    elif s == "EXIT":
        exit()
    else:
        batch.append(s.split())
        left_to_batch -= 1
        if(left_to_batch == 0):
            ress = embedder.embed_sentences(batch)

            for sent, res in zip(batch, ress):
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

                    # solution 1
                    print( str(vec.tolist()).lstrip("[ ").rstrip("] ").replace(",", " ") )

                    # solution 2
                    # np.set_printoptions(threshold = np.prod(vec.shape))
                    # print( np.array_str(vec, max_line_width=100000000).lstrip("[ ").rstrip(" ]") )

                    # solution 3
                    # print( " ".join(str(v) for v in vec) )

                input(">>> ")
            batch = []
            left_to_batch = 1

