#!/usr/bin/env python3

# from os.path import realpath, dirname
import glob
import sys
from sys import stderr
# script_dir = dirname(realpath(__file__))
# sys.path.append(script_dir+'/SequenceEmbedderELMo_Service')
from SequenceEmbedderELMo_Service import SequenceEmbedderELMo_Service
from SequenceEmbedderELMo_Service.ttypes import SequenceEmbedderELMo_UnknownEmbType

# don't forget to pip3 install thrift

from thrift.transport import TSocket
from thrift.transport import TTransport
from thrift.protocol import TBinaryProtocol
from thrift.server.TProcessPoolServer import TProcessPoolServer

layer=2
word_position=0
half_dimension=512

options_file = "https://s3-us-west-2.amazonaws.com/allennlp/models/elmo/2x4096_512_2048cnn_2xhighway/elmo_2x4096_512_2048cnn_2xhighway_options.json"
weight_file = "https://s3-us-west-2.amazonaws.com/allennlp/models/elmo/2x4096_512_2048cnn_2xhighway/elmo_2x4096_512_2048cnn_2xhighway_weights.hdf5"

class ELMo_Service_Handler:

    def __init__(self):
        self._memo_embedder = None

    def start_elmo(self):
        self.embedder()

    def embedder(self):
        if self._memo_embedder is None:
            from allennlp.commands.elmo import ElmoEmbedder
            self._memo_embedder = ElmoEmbedder(options_file=options_file, weight_file=weight_file)
        return self._memo_embedder

    def quit(self):
        self.server.stop()

    def register_server(self, server):
        self.server = server

    # list<list<list<double>>> embed_sents(1:string emb_type, 2:list<list<string>> sents)
    def embed_sents(self, sents, emb_type):
        ress = self.embedder().embed_sentences(sents)
        all_vecs = []
        for sent, res in zip(sents, ress):
            vecs = []
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
                    raise SequenceEmbedderELMo_UnknownEmbType("unknown emb_type %s"%emb_type)
                vecs.append(vec.tolist())
            all_vecs.append(vecs)
        return all_vecs

if __name__ == '__main__':

    if len(sys.argv)!= 2:
        raise Exception("wrong number of arguments", file=stderr)
    PORT=int(sys.argv[1])

    handler = ELMo_Service_Handler()

    processor = SequenceEmbedderELMo_Service.Processor(handler)
    transport = TSocket.TServerSocket(host='127.0.0.1', port=PORT)
    tfactory = TTransport.TBufferedTransportFactory()
    pfactory = TBinaryProtocol.TBinaryProtocolFactory()

    server = TProcessPoolServer(processor, transport, tfactory, pfactory)
    server.setNumWorkers(1)
    handler.register_server(server)
    server.serve()


