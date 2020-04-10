package edin.nn.embedder
import edin.general.YamlConfig
import edin.nn.DyFunctions
import edin.nn.DyFunctions._
import edu.cmu.dynet.{Expression, ParameterCollection}

case class SequenceEmbedderGeneralCombinerConfig[T](
                                                     combiningMethod    : SequenceEmbedderGeneralCombinerConfig.CombiningMethod,
                                                     subEmbedderConfigs : List[SequenceEmbedderGeneralConfig[T]],
                                                     dropProb           : Float
                                    ) extends SequenceEmbedderGeneralConfig[T]{
  import SequenceEmbedderGeneralCombinerConfig._
  override val outDim: Int = combiningMethod match {
      case Sum | Average =>
        val sizes = subEmbedderConfigs.map(_.outDim)
        require(sizes.forall(_ == sizes.head))
        sizes.head
      case Concat =>
        val sizes = subEmbedderConfigs.map(_.outDim)
        sizes.sum
    }
  override def construct()(implicit model: ParameterCollection): SequenceEmbedderGeneral[T] = new SequenceEmbedderGeneralCombiner[T](this)
}

object SequenceEmbedderGeneralCombinerConfig{

  sealed trait CombiningMethod
  case object Average extends CombiningMethod
  case object Sum     extends CombiningMethod
  case object Concat  extends CombiningMethod

  def fromYaml[K](conf:YamlConfig) : SequenceEmbedderGeneralConfig[K] = {
    val combiningMethod = conf("combining-method").str match {
      case "sum"     => Sum
      case "average" => Average
      case "concat"  => Concat
    }
    val subEmbedders = conf("subembs").list.map(x => SequenceEmbedderGeneralConfig.fromYaml[K](x))
    SequenceEmbedderGeneralCombinerConfig[K](
      combiningMethod    = combiningMethod,
      dropProb           = conf.getOrElse("dropout", 0f),
      subEmbedderConfigs = subEmbedders
    )
  }

}

class SequenceEmbedderGeneralCombiner[T](config : SequenceEmbedderGeneralCombinerConfig[T])(implicit model: ParameterCollection) extends SequenceEmbedderGeneral[T] {

  import edin.nn.embedder.SequenceEmbedderGeneralCombinerConfig._

  private val subEmbedders = config.subEmbedderConfigs.map(_.construct())

  override def transduce(xs: List[T]): List[Expression] = {
    val subEmbs : Array[List[Expression]] = subEmbedders.map(_.transduce(xs)).transpose.toArray

    var result = List[Expression]()
    for(i <- subEmbs.indices){
      val wSubEmbs = subEmbs(i)
      result ::= {config.combiningMethod match {
        case Average => wSubEmbs.eavg
        case Sum     => wSubEmbs.esum
        case Concat  => concatSeq(wSubEmbs)
      }}
    }
    result.reverse
  }

  override def zeros: Expression = DyFunctions.zeros(config.outDim)

  override def precomputeEmbeddings(sents: Iterable[List[T]]): Unit = {}

  override def cleanPrecomputedCache(): Unit = {}
}
