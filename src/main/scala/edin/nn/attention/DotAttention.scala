package edin.nn.attention

import edu.cmu.dynet._

class DotAttention(config:AttentionConfig)(implicit model:ParameterCollection) extends Attention {

  override val inDim: Int = config.inDim

  override def unnormalizedAlign(sourceMatrix: ContextVector, targetHidden: ContextVector, sourceWordsCount:Int): Alignments =
    sourceMatrix * targetHidden

}
