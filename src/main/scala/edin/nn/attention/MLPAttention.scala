package edin.nn.attention

import edin.nn.DyFunctions._
import edin.nn.layers.{Layer, MLPConfig}
import edu.cmu.dynet._

class MLPAttention(config:AttentionConfig)(implicit model:ParameterCollection) extends Attention {

  override val inDim: Int = config.inDim

  private val mlp:Layer = MLPConfig(
    activations    = List("tanh", "linear") ,
    sizes          = List(inDim, inDim/2, 1)    ,
    withLayerNorm  = config.withLayerNorm   ,
    withWeightNorm = config.withWeightNorm   ,
  ).construct

  override def unnormalizedAlign(sourceMatrix: ContextVector, targetHidden: ContextVector, sourceWordsCount:Int): Alignments = {
    val targetExpanded = targetHidden.expandHorizontally(sourceWordsCount).T
    val input:Expression = concat(sourceMatrix.T, targetExpanded.T)
    val output = mlp(input).T
    output
  }

}
