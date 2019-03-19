package edin.nn.attention

import edin.nn.DyFunctions._
import edin.nn.layers.{Layer, MLPConfig}
import edu.cmu.dynet._

class MLPAttention(config:AttentionConfig)(implicit model:ParameterCollection) extends Attention {

  var mlp:Layer = _

  override val inDim: Int = config.inDim

  define()

  override def align(sourceMatrix: ContextVector, targetHidden: ContextVector, sourceWordsCount:Int): Alignments = {
    val targetExpanded = targetHidden.expandHorizontally(sourceWordsCount).T
    val input:Expression = concat(sourceMatrix.T, targetExpanded.T)
    val output = mlp(input).T
    val a = softmax(output)
    a
  }

  def define()(implicit model: ParameterCollection): Unit = {
    val dim = config.inDim
    mlp = MLPConfig(
      activations    = List("tanh", "linear") ,
      sizes          = List(dim, dim/2, 1)    ,
      withLayerNorm  = config.withLayerNorm   ,
      withWeightNorm = config.withWeightNorm   ,
    ).construct

  }

}
