package edin.nn.embedder

import edin.general.YamlConfig
import edin.nn.DyFunctions._
import edu.cmu.dynet.LookupParameter
import edu.cmu.dynet.{Expression, ParameterCollection}

sealed case class EmbedderPositionsLearnedConfig(
                                             maxPosition : Int,
                                             outDim      : Int,
                                             dropout     : Float=0f
                                           ) extends EmbedderConfig[Int] {
  def construct()(implicit model: ParameterCollection) = new EmbedderPositionsLearned(this)
}

object EmbedderPositionsLearnedConfig{

  def fromYaml[Int](conf:YamlConfig) : EmbedderConfig[Int] =
    EmbedderPositionsLearnedConfig(
      maxPosition = conf("max-position").int,
      outDim      = conf("out-dim").int,
      dropout     = conf.getOrElse("dropout", 0f)
    ).asInstanceOf[EmbedderConfig[Int]]

}

class EmbedderPositionsLearned(config:EmbedderPositionsLearnedConfig)(implicit model: ParameterCollection) extends Embedder[Int] {

  var E:LookupParameter = _
  var dropProb:Float = _
  var maxPosition:Int = _

  override val outDim: Int = config.outDim

  private def ineffective:Boolean = maxPosition == 0 || outDim == 0

  define()

  protected def define(): Unit = {
    dropProb = config.dropout
    maxPosition = config.maxPosition
    E = model.addLookupParameters(config.maxPosition+1, outDim)
  }

  def apply(pos:Int) : Expression = if(ineffective) null else dropout(E(if(pos < maxPosition) pos else maxPosition), dropProb)
  // def apply(pos:Int) : Expression = apply(s2i(w))

  // def apply(is:List[Int]) : List[Expression] = is.map{apply(_)}
  def apply(ws:List[Int]) : List[Expression] = ws.map(apply)

}
