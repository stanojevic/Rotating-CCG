package edin.nn.embedder

import edin.general.{Python, YamlConfig}
import edu.cmu.dynet.{Expression, ParameterCollection}
import edin.nn.DyFunctions._
import edin.nn.layers.SingleLayer

case class SequenceEmbedderELMoConfig(
                                       embeddingType           : String,
                                       normalize               : Boolean,
                                       dropout                 : Float,
                                       outDim                  : Int,
                                     ) extends SequenceEmbedderGeneralConfig[String]{
  override def construct()(implicit model: ParameterCollection): SequenceEmbedderGeneral[String] = new SequenceEmbedderELMo(this)
}

object SequenceEmbedderELMoConfig{

  def fromYaml(origConf:YamlConfig) : SequenceEmbedderGeneralConfig[String] = {
    val embType = origConf("ELMo-type").str
    assert(Set("average_top", "concat_top", "forward_top", "backward_top", "local") contains embType)
    val outDim = origConf("out-dim").int
    val withoutCompression = origConf.getOrElse("withoutCompression", false)
    SequenceEmbedderELMoConfig(
      embeddingType      = embType,
      normalize          = origConf("normalize").bool,
      dropout            = origConf("dropout").float,
      outDim             = outDim
    )
  }

}

class SequenceEmbedderELMo(config: SequenceEmbedderELMoConfig)(implicit model: ParameterCollection) extends SequenceEmbedderGeneral[String] with SequenceEmbedderPrecomputable {

  override type T = List[Array[Float]]

  override val name: String = "ELMo_"+(if(config.normalize) "normalized" else "raw")+"_"+config.embeddingType

  private val compressor = SingleLayer.compressor(ELMoDim, config.outDim)

  private def ELMoDim: Int = config.embeddingType match {
    case "concat_top" => 2*512
    case _            =>   512
  }

  private var ELMO_loaded = false
  private def loadELMo() : Unit = {
    System.err.println("Loading ELMO model START -- potentially also downloading the models if this is a first run")
    Python.exec("from allennlp.commands.elmo import ElmoEmbedder")
    Python.exec("import numpy as np")
    Python.exec(s"${name}layer=2")
    Python.exec(s"${name}half_dimension=512")
    Python.set(s"${name}emb_type", config.embeddingType)
    Python.set(s"${name}options_file", "https://s3-us-west-2.amazonaws.com/allennlp/models/elmo/2x4096_512_2048cnn_2xhighway/elmo_2x4096_512_2048cnn_2xhighway_options.json")
    Python.set(s"${name}weight_file",  "https://s3-us-west-2.amazonaws.com/allennlp/models/elmo/2x4096_512_2048cnn_2xhighway/elmo_2x4096_512_2048cnn_2xhighway_weights.hdf5")
    Python.exec(s"${name}embedder = ElmoEmbedder(options_file=${name}options_file, weight_file=${name}weight_file)")
    System.err.println("Loading ELMO model DONE")
  }

  override protected def embedBatchDirect(sents: Seq[List[String]]) : List[List[Array[Float]]] = {
    if(! ELMO_loaded){
      loadELMo()
      ELMO_loaded = true
    }
    Python.setList(s"${name}batch", sents)
    Python.exec(s"${name}ress = ${name}embedder.embed_sentences(${name}batch)")

    Python.exec(s"${name}all_vectors = []")
    Python.exec(cmd =
      s"for ${name}sent, ${name}res in zip(${name}batch, ${name}ress):",
      s"  ${name}curr_seq_vecs = []",
      s"  for ${name}word_position in range(len(${name}sent)):",
      s"    if ${name}emb_type == 'forward_top':",
      s"      ${name}vec = ${name}res[${name}layer, ${name}word_position, :${name}half_dimension]",
      s"    elif ${name}emb_type == 'backward_top':",
      s"      ${name}vec = ${name}res[${name}layer, ${name}word_position, ${name}half_dimension:]",
      s"    elif ${name}emb_type == 'concat_top':",
      s"      ${name}vec = ${name}res[${name}layer, ${name}word_position, :]",
      s"    elif ${name}emb_type == 'average_top':",
      s"      ${name}fwds_vec = ${name}res[${name}layer, ${name}word_position, :${name}half_dimension]",
      s"      ${name}bcks_vec = ${name}res[${name}layer, ${name}word_position, ${name}half_dimension:]",
      s"      ${name}vec = np.add(${name}fwd_vec, ${name}bck_vec)/2",
      s"    elif ${name}emb_type == 'local':",
      s"      ${name}vec = ${name}res[0, ${name}word_position, :${name}half_dimension]",
      s"    else:",
      s"      print('unknown emb_type %s'%emb_type, file=stderr)",
      s"      exit(-1)",
      if(config.normalize) s"    ${name}vec = ${name}vec/np.linalg.norm(${name}vec, 2)" else s"",
      s"    ${name}curr_seq_vecs.append(${name}vec)",
      s"  ${name}all_vectors.append(${name}curr_seq_vecs)")

    val all_embs = sents.indices.map( i => Python.getListOfNumPyArrays(s"${name}all_vectors[$i]") )

    Python.delVar(s"${name}all_vectors")
    Python.delVar(s"${name}batch")
    Python.delVar(s"${name}ress")

    all_embs.toList

  }

  override def transduce(xs: List[String]): List[Expression] =
    cache
      .getOrElse(xs, embedBatchDirect(xs::Nil).head)
      .map(x => dropout(compressor(vector(x)), config.dropout) )

  override def zeros: Expression = Expression.zeros(config.outDim)

  override def precomputeEmbeddings(sents: Iterable[List[String]]): Unit = {}

  override def cleanPrecomputedCache(): Unit = {}

}
