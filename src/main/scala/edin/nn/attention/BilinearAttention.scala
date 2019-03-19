package edin.nn.attention

import edin.nn.DyFunctions._
import edu.cmu.dynet._

class BilinearAttention(config:AttentionConfig)(implicit model:ParameterCollection) extends Attention {

  private var W : Parameter = _

  define()

  override def align(sourceMatrix: ContextVector, targetHidden: ContextVector, sourceWordsCount: Int): Alignments = {
    softmax(sourceMatrix*W*targetHidden)
  }

  protected def define()(implicit model: ParameterCollection): Unit = {
    val dim = config.inDim
    W = model.addParameters((dim, dim))
  }

  override val inDim: Int = config.inDim
}
