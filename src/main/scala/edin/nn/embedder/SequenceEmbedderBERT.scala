package edin.nn.embedder

import java.io.File

import edin.general.{Global, Python, YamlConfig}
import edu.cmu.dynet.{Expression, ParameterCollection}
import edin.nn.DyFunctions._
import edin.nn.layers.SingleLayer

case class SequenceEmbedderBERTConfig(
                                       bertModelDir            : String,
                                       normalize               : Boolean,
                                       dropout                 : Float,
                                       outDim                  : Int,
                                     ) extends SequenceEmbedderGeneralConfig[String]{
  override def construct()(implicit model: ParameterCollection): SequenceEmbedderGeneral[String] = new SequenceEmbedderBERT(this)
}

object SequenceEmbedderBERTConfig{

  def fromYaml(origConf:YamlConfig) : SequenceEmbedderGeneralConfig[String] = {
    val bertModelDir = System getenv "BERT_DIR"
    require(         bertModelDir          != null                         , s"You need to set BERT_DIR env variable")
    require(new File(bertModelDir).getName == "multi_cased_L-12_H-768_A-12", s"BERT_DIR should point to BERT directory named multi_cased_L-12_H-768_A-12")
    require(new File(bertModelDir).exists()                                , s"BERT_DIR $bertModelDir doesn't exist")
    SequenceEmbedderBERTConfig(
      bertModelDir       = bertModelDir,
      normalize          = origConf("normalize").bool,
      dropout            = origConf("dropout").float,
      outDim             = origConf("out-dim").int
    )
  }

}

class SequenceEmbedderBERT(config: SequenceEmbedderBERTConfig)(implicit model: ParameterCollection) extends SequenceEmbedderGeneral[String] with SequenceEmbedderPrecomputable {

  override type T = List[Array[Float]]

  override val name: String = "BERT_"+(if(config.normalize) "normalized" else "raw")

  private val compressor = SingleLayer.compressor(768, config.outDim)

  private var BERT_loaded = false
  private def loadBERT():Unit = {
    val myExtractScript  = Global.projectDir+"/scripts/embedding/bert_extract.py"
    val bertCodeDir = Global.projectDir+"/scripts/embedding/bert"
    Python.addToPath(bertCodeDir)
    Python.runPythonFile(myExtractScript)
    Python.set(s"${name}bert_model_dir", config.bertModelDir)
    Python.exec(s"${name}tokenizer = load_tokenizer(${name}bert_model_dir)")
    Python.exec(s"${name}estimator = load_BERT_estimator(${name}bert_model_dir)")
  }

  override protected def embedBatchDirect(sents: Seq[List[String]]) : List[List[Array[Float]]] = {
    if(! BERT_loaded){
      loadBERT()
      BERT_loaded = true
    }
    // Computing embeddings
    Python.setList(s"${name}input", sents.map(_.mkString(" ")).toList)
    Python.exec(s"${name}res, ${name}errors = embed_sent_pairs(${name}estimator, ${name}tokenizer, ${name}input)")

    // Normalization
    if(config.normalize)
      Python.exec(s"${name}res = list(map(normalize_embedings_dict, ${name}res))")

    System.err.print(Python.getString(s"${name}errors"))

    // Reading out
    val all_embs = sents.indices.map( i => Python.getListOfNumPyArrays(s"${name}res[$i]['A']") )

    Python.delVar(s"${name}input")
    Python.delVar(s"${name}res")
    Python.delVar(s"${name}errors")

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
