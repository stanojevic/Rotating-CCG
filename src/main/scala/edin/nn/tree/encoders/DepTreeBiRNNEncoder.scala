package edin.nn.tree.encoders

import edin.general.trees.dependency.DepNode
import edin.nn._
import edin.nn.sequence.{MultiRNN, MultiRNNConfig}
import edin.nn.tree.EncodableNode
import edu.cmu.dynet.ParameterCollection

sealed case class DepTreeBiRNNEncoderConfig(
                                             inDim          : Int,
                                             outDim         : Int,
                                             seqType        : String,
                                             seqLayers      : Int,
                                             withLayerNorm  : Boolean,
                                             withWeightNorm : Boolean,
                                             seqDropout     : Float
                                           ){
  def construct()(implicit model: ParameterCollection) = new DepTreeBiRNNEncoder(this)
}


class DepTreeBiRNNEncoder(c:DepTreeBiRNNEncoderConfig)(implicit model: ParameterCollection) extends TreeEncoder {

  var rnn: MultiRNN = _

  define()

  override def reencode(root: EncodableNode): Unit = {
    val allNodes   = root.asInstanceOf[DepNode].allNodesLinear
    val oldVectors = allNodes.map{_.nn.h}
    val newVectors = rnn.transduce(oldVectors)
    (allNodes zip newVectors).foreach{ case (node, vec) => node.nn = SimpleState(vec) }
  }

  protected def define()(implicit model: ParameterCollection): Unit = {
    rnn = MultiRNNConfig(
      rnnType           = c.seqType,
      inDim             = c.inDim,
      outDim            = c.outDim,
      layers            = c.seqLayers,
      withLayerNorm     = c.withLayerNorm,
      withWeightNorm    = c.withWeightNorm,
      dropProbRecurrent = c.seqDropout
    ).construct()
  }

}
