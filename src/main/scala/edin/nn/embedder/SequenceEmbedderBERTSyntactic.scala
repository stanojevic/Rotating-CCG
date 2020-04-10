package edin.nn.embedder

import edin.general.{Global, Python, YamlConfig}
import edu.cmu.dynet.{Expression, ParameterCollection}
import edin.nn.DyFunctions._
import edin.nn.layers.SingleLayer

/**
  * you need to do
  * pip3 install tqdm h5py pytorch_pretrained_bert
  */

case class SequenceEmbedderBERTSyntacticConfig(
                                                embeddingVariation      : String,
                                                bertCheckpoint          : String,
                                                normalizeEmbs           : Boolean,
                                                averageEmbs             : Boolean,
                                                dropout                 : Float,
                                                bertLayer               : Int,
                                                outDim                  : Int,
                                     ) extends SequenceEmbedderGeneralConfig[String]{
  override def construct()(implicit model: ParameterCollection): SequenceEmbedderGeneral[String] = new SequenceEmbedderBERTSyntactic(this)
}

object SequenceEmbedderBERTSyntacticConfig{

  def fromYaml(origConf:YamlConfig) : SequenceEmbedderGeneralConfig[String] = {
    val embeddingVariation = origConf("embedding-variation").str
    require(Set("original", "syntactic") contains embeddingVariation)
    val syntacticBertCheckpoint = System.getenv("SYNTACTIC_BERT_CHECKPOINT")
    require(embeddingVariation == "original" || syntacticBertCheckpoint != null, "ERROR: SYNTACTIC_BERT_CHECKPOINT ENV VARIABLE NOT SET")
    SequenceEmbedderBERTSyntacticConfig(
      embeddingVariation = embeddingVariation,
      bertCheckpoint     = syntacticBertCheckpoint,
      bertLayer          = origConf("bert-layer").int,
      averageEmbs        = origConf("average-embs").bool,
      normalizeEmbs      = origConf("normalize").bool,
      dropout            = origConf("dropout").float,
      outDim             = origConf("out-dim").int
    )
  }

}

class SequenceEmbedderBERTSyntactic(config: SequenceEmbedderBERTSyntacticConfig)(implicit model: ParameterCollection) extends SequenceEmbedderGeneral[String] with SequenceEmbedderPrecomputable {

  require(config.bertLayer >=0 && config.bertLayer<16)

  override val name: String = {
    val normalization = if(config.normalizeEmbs) "normalized" else "nonnormalized"
    val averaging = if(config.averageEmbs) "avg" else "first"
    s"SynBERT_${normalization}_${config.embeddingVariation}_${config.bertLayer}_$averaging"
  }

  override type T = List[Array[Float]]

  private val compressor  = SingleLayer.compressor(768, config.outDim)

  override def transduce(xs: List[String]): List[Expression] =
    cache
      .getOrElse(xs, embedBatchDirect(xs::Nil).head)
      .map(x => dropout(compressor(vector(x)), config.dropout) )

  override def zeros: Expression = Expression.zeros(config.outDim)

  override def precomputeEmbeddings(sents: Iterable[List[String]]): Unit = {}

  override def cleanPrecomputedCache(): Unit = {}

  private var BERT_loaded = false
  private def loadBERT():Unit = {
    System.err.println(s"START load ${config.embeddingVariation} BERT")
    val myExtractScript  = Global.projectDir+"/scripts/embedding/syntactic_bert_extract.py"
    val bertCodeDir = Global.projectDir+"/scripts/embedding/syntactic_bert"
    Python.addToPath(bertCodeDir)
    Python.runPythonFile(myExtractScript)
    val normalizedStr = if(config.normalizeEmbs) "True" else "False"
    val averageStr    = if(config.averageEmbs  ) "True" else "False"
    if(config.embeddingVariation == "original"){
      Python.exec(s"${name}model = SynBERT_load_model(layer=${config.bertLayer}, normalized=$normalizedStr, average_embs=$averageStr, syntactic_checkpoint=None                 )")
    }else{
      Python.set(s"${name}bert_model_dir", config.bertCheckpoint)
      Python.exec(s"${name}model = SynBERT_load_model(layer=${config.bertLayer}, normalized=$normalizedStr, average_embs=$averageStr, syntactic_checkpoint=${name}bert_model_dir)")
      Python.delVar(s"${name}bert_model_dir")
    }
    System.err.println("DONE load syntactic BERT")
  }

  override protected def embedBatchDirect(sents: Seq[List[String]]) : List[List[Array[Float]]] = {
    if(! BERT_loaded){
      loadBERT()
      BERT_loaded = true
    }
    // Computing embeddings
    Python.setList(s"${name}input", sents)
    Python.exec(s"${name}res, ${name}errors = SynBERT_embed_sentences(${name}model, ${name}input)")

    System.err.print(Python.getString(s"${name}errors"))

    // Reading out
    val all_embs = sents.indices.map( i => Python.getListOfNumPyArrays(s"${name}res[$i]") )

    Python.delVar(s"${name}input")
    Python.delVar(s"${name}res")

    all_embs.toList
  }

}
