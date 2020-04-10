package edin.nn.attention

import edin.nn._
import DyFunctions._
import edin.general.YamlConfig
import edu.cmu.dynet._

sealed case class AttentionConfig(attType:String, inDim:Int, withLayerNorm:Boolean, withWeightNorm:Boolean){
  def construct()(implicit model:ParameterCollection): Attention ={
    attType.toLowerCase match {
      case "dot" => new DotAttention(this)
      case "mlp" => new MLPAttention(this)
      case "bilinear" => new BilinearAttention(this)
    }
  }
}

object AttentionConfig{

  def fromYaml(conf:YamlConfig) : AttentionConfig = {
    AttentionConfig(
      attType       = conf("attention-type").str,
      inDim         = conf("in-dim").int,
      withLayerNorm = conf.getOrElse("with-layer-norm", false),
      withWeightNorm = conf.getOrElse("with-weight-norm", false)
    )
  }

}

object Attention{

  def toSourceMatrix(sourceVectors:List[Expression]) : Expression = {
    assert(sourceVectors.nonEmpty)
    Expression.concatenateCols(sourceVectors).T
  }

}

trait Attention{

  val inDim : Int

  type ContextVector = Expression
  type Alignments = Expression

  def unnormalizedAlign(sourceMatrix:Expression, targetHidden:Expression, sourceWordsCount:Int) : Alignments

  final def unnormalizedAlign(sourceVectors:List[Expression], targetHidden:Expression) : Alignments = {
    val sourceMatrix = Attention.toSourceMatrix(sourceVectors)
    val sourceWordsCount = sourceVectors.size
    unnormalizedAlign(sourceMatrix, targetHidden, sourceWordsCount)
  }


  final def align(sourceVectors:List[Expression], targetHidden:Expression) : Alignments = {
    val sourceMatrix = Attention.toSourceMatrix(sourceVectors)
    val sourceWordsCount = sourceVectors.size
    align(sourceMatrix, targetHidden, sourceWordsCount)
  }

  final def align(sourceMatrix:Expression, targetHidden:Expression, sourceWordsCount:Int) : Alignments =
    DyFunctions.softmax(unnormalizedAlign(sourceMatrix, targetHidden, sourceWordsCount))

  final def context(sourceMatrix:Expression, alignment:Alignments) : ContextVector =
    sourceMatrix.T * alignment

}






