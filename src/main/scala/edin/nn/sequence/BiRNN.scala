package edin.nn.sequence

import edin.general.YamlConfig
import edin.nn.DyFunctions.concat
import edu.cmu.dynet.{Expression, ParameterCollection}

sealed case class BiRNNConfig(
                               rnnType        : String,
                               inDim          : Int,
                               outDim         : Int,
                               layers         : Int,
                               withLayerNorm  : Boolean,
                               withWeightNorm : Boolean,
                               dropProb       : Float
                             ) extends SequenceEncoderConfig{
  def construct()(implicit model: ParameterCollection) = new BiRNN(this)
}

object BiRNNConfig{

  def fromYaml(conf:YamlConfig) : BiRNNConfig = {
    BiRNNConfig(
      rnnType           = conf("rnn-type").str,
      inDim             = conf("in-dim"  ).int,
      outDim            = conf("out-dim" ).int,
      layers            = conf("layers" ).int,
      withLayerNorm     = conf.getOrElse("with-layer-norm", false),
      withWeightNorm    = conf.getOrElse("with-weight-norm", false),
      dropProb          = conf.getOrElse("dropout", 0f)
    )
  }

}

class BiRNN(c:BiRNNConfig)(implicit model: ParameterCollection) extends SequenceEncoder {

  private var rnnPairs = List[(RecurrentNN, RecurrentNN)]()

  define()

  protected def define()(implicit model: ParameterCollection) : Unit = {
    var inDim     = c.inDim

    assert(c.outDim % 2 == 0)

    for(i <- 0 until c.layers){
      rnnPairs ::= (
        RecurrentNN.singleFactory(c.rnnType, inDim, c.outDim/2, c.dropProb, c.withLayerNorm, c.withWeightNorm), // forward
        RecurrentNN.singleFactory(c.rnnType, inDim, c.outDim/2, c.dropProb, c.withLayerNorm, c.withWeightNorm)  // backward
      )
      inDim = c.outDim
    }
    rnnPairs = rnnPairs.reverse
  }

  def transduce(x:List[Expression]) : List[Expression] = {
    var currRepr = x
    for( (fwd, bck) <- rnnPairs ){
      val fwdRep = fwd.transduce(currRepr)
      val bckRep = bck.transduceBackward(currRepr)
      val biRep = (fwdRep, bckRep).zipped.map{ case (f, b) => concat(f, b) }
      currRepr = biRep
    }
    currRepr
  }

}
