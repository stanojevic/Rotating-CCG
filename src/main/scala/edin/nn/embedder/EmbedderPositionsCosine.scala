package edin.nn.embedder

import edin.general.YamlConfig
import edin.nn.DyFunctions._
import edin.algorithms.MathArray
import edu.cmu.dynet.{Expression, ParameterCollection}

final case class EmbedderPositionsSinusoidConfig(
                                                  outDim      : Int,
                                                  dropout     : Float=0f
                                                ) extends EmbedderConfig[Int] {
  def construct()(implicit model: ParameterCollection) = new EmbedderPositionsCosine(this)
}

object EmbedderPositionsSinusoidConfig{

  def fromYaml[Int](conf:YamlConfig) : EmbedderConfig[Int] =
    EmbedderPositionsSinusoidConfig(
      outDim      = conf("out-dim").int,
      dropout     = conf.getOrElse("dropout", 0f)
    ).asInstanceOf[EmbedderConfig[Int]]

}

class EmbedderPositionsCosine(config:EmbedderPositionsSinusoidConfig)(implicit model: ParameterCollection) extends Embedder[Int] {

  var dropProb:Float = config.dropout

  override val outDim: Int = config.outDim

  private val proVec:MathArray = MathArray((0 until outDim).map{i : Int => math.pow(10000.0, -i.toDouble*2/outDim).toFloat}.toArray)

  private def ineffective:Boolean = outDim == 0

  private def getEmb(pos:Int) : Expression = vector(proVec.map(x => math.sin(pos*x)).array)

  def apply(pos:Int) : Expression = if(ineffective) null else dropout(getEmb(pos), dropProb)

  def apply(ws:List[Int]) : List[Expression] = ws.map(apply)

}
