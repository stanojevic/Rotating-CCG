package edin.nn.attention

import edin.nn.DyFunctions._
import edu.cmu.dynet._

class DotAttention(config:AttentionConfig)(implicit model:ParameterCollection) extends Attention {

  override def align(sourceMatrix: ContextVector, targetHidden: ContextVector, sourceWordsCount:Int): Alignments = {
    softmax(sourceMatrix * targetHidden)
  }

  override val inDim: Int = config.inDim

}
