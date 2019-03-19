package edin.nn.embedder

import edin.general.{Any2Int, String2Int, YamlConfig}
import edin.nn.DyFunctions._
import edin.algorithms.MathArray
import edin.nn.{DyFunctions, DynetSetup}
import edu.cmu.dynet._
import edu.cmu.dynet.{FloatVector, LookupParameter}

import scala.io.Source
import scala.util.Random

sealed case class EmbedderStandardConfig[K](
                                             s2i               : Any2Int[K],
                                             outDim            : Int,
                                             normalize         : Boolean,
                                             dropout           : Float=0f,
                                             initFile          : String=null,
                                             wordDropoutUse    : Boolean = false,
                                             wordDropoutAlpha  : Double = 0.25
                                   ) extends EmbedderConfig[K] {
  def construct()(implicit model: ParameterCollection) = new EmbedderStandard[K](this)
}

object EmbedderStandardConfig{

  def fromYaml[K](conf:YamlConfig) : EmbedderConfig[K] =
    EmbedderStandardConfig[K](
      s2i               = conf("w2i").any2int,
      outDim            = conf("out-dim").int,
      initFile          = conf.getOrElse("init-file", null.asInstanceOf[String]),
      normalize         = conf.getOrElse("normalize", false),
      dropout           = conf.getOrElse("dropout", 0f),
      wordDropoutUse    = conf.getOrElse("word-dropout-use", false),
      wordDropoutAlpha  = conf.getOrElse("word-dropout-alpha", 0.25)
    )

}

class EmbedderStandard[T](config:EmbedderStandardConfig[T])(implicit model: ParameterCollection) extends Embedder[T] {

  val normalized: Boolean = config.normalize

  private var E:LookupParameter = _
  var s2i:Any2Int[T] = _  // TODO eventually you should make this property private
  private var dim:Int = _
  private var dropProb:Float = _

  override val outDim: Int = config.outDim

  define()

  protected def define(): Unit = {
    s2i = config.s2i
    dim = config.outDim
    dropProb = config.dropout
    E = model.addLookupParameters(s2i.size, dim)
    if(config.initFile != null){
      EmbedderStandard.initEmbedderFromPretrainedTable(config.initFile, this.asInstanceOf[EmbedderStandard[String]])
    }
  }

  private var droppedWordTypes = Set[T]()
  private var latestCG_id = -1

  private def wordDrop(w:T, alpha:Double, s2i:Any2Int[T]) : Boolean = {
    /** used in the similar way to Kiperwasser & Goldberg 2016 but over word types as in Gal & Ghahramani 2016 */
    if(latestCG_id != DynetSetup.cg_id){
      droppedWordTypes = Set()
      latestCG_id = DynetSetup.cg_id
    }

    if(droppedWordTypes contains w){
      true
    }else{
      val counts = s2i.frequency(w)
      val dropProb = config.wordDropoutAlpha / (counts + config.wordDropoutAlpha)
      val willDrop = Random.nextDouble() < dropProb
      if(willDrop){
        droppedWordTypes += w
      }
      willDrop
    }
  }

  override def apply(w:T) : Expression = {
    val ww = if(config.wordDropoutUse && DyFunctions.dropoutIsEnabled && wordDrop(w, config.wordDropoutAlpha, s2i)){
      s2i.UNK_str
    }else{
      w
    }
    dropout(E(s2i(ww)), dropProb)
  }

}

object EmbedderStandard{

  def pretrainedEmb_loadDim(file:String) : Int =
    Source.fromFile(file).getLines().next().split(" +").length-1

  def pretrainedEmb_loadS2I(file:String, lowercased:Boolean) : Any2Int[String] = {
    val s2i : Any2Int[String] = new String2Int(lowercased = lowercased)
    Source.fromFile(file).getLines().zipWithIndex.foreach{ case (line, i) =>
      val word = line.split(" ")(0)
      s2i.addToCounts(word)
      if(i% 100000 == 0)
        System.err.println(s"Loading w2i for pretrained embeddings $i")
    }
    s2i
  }

  private def initEmbedderFromPretrainedTable(fn:String, embedder:EmbedderStandard[String]) : Unit = {
    val p = embedder.E
    val s2i = embedder.s2i
    var avgVector:MathArray = null
    var vecCount = 0
    Source.fromFile(fn).getLines().zipWithIndex.foreach{ case (line, line_id) =>
      vecCount+=1
      val fields = line.split(" ").toList
      val word = fields.head
      val i = s2i(word)
      var v = new MathArray(fixEmbedding(fields.tail.map{_.toFloat}.toArray))
      if(embedder.normalized)
        v = new MathArray(to_l2_normalized(v.array))
      if(avgVector == null){
        avgVector = MathArray(v.length)
      }
      avgVector += v
      val vec:FloatVector = new FloatVector(v.toArray)
      p.initialize(i, vec)
      if(line_id % 100000 == 0)
        System.err.println(s"Loading pretrained embedding vectors file line $line_id")
    }
    avgVector /= vecCount
    p.initialize(s2i.UNK_i, new FloatVector(avgVector.toArray))
    p.setUpdated(false)
  }

  def to_l2_normalized(array:Array[Float]) : Array[Float] = {
    var norm = array.map(x => x*x).sum
    if(norm == 0) norm = 1
    array.map(_/norm)
  }

  def fixEmbedding(emb:Array[Float]) : Array[Float] = {
    val e = emb.clone()
    for(i <- e.indices){
      if(e(i).isNaN || e(i).isInfinity){
        System.err.println("EMBEDDING IS WRONG; I'M FIXING IT")
        e(i) = 0f
      }
    }
    e
  }

}


