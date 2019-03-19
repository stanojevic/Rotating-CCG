
namespace java edin.nn.embedder
// namespace py edin.nn.embedder

exception SequenceEmbedderELMo_UnknownEmbType{
  1: string message
}

service SequenceEmbedderELMo_Service {

  void start_elmo()

  list<list<list<double>>> embed_sents(1:list<list<string>> sents, 2:string emb_type) throws(1:SequenceEmbedderELMo_UnknownEmbType e)

  void quit()

}
