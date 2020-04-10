package edin.nn.sequence

import edu.cmu.dynet.{Expression, ParameterCollection}

trait SequenceEncoderConfig{

  val outDim:Int

  def construct()(implicit model: ParameterCollection) : SequenceEncoder
}

trait SequenceEncoder{

  def transduce(x:List[Expression]) : List[Expression]

}
