package edin.nn.attention

import edin.nn.DyFunctions._
import edu.cmu.dynet._

class BilinearAttention(config:AttentionConfig)(implicit model:ParameterCollection) extends Attention {

  override val inDim: Int = config.inDim

  private val W : Parameter = model.addParameters((inDim, inDim))

  override def unnormalizedAlign(sourceMatrix: ContextVector, targetHidden: ContextVector, sourceWordsCount: Int): Alignments =
    sourceMatrix*W*targetHidden

}
