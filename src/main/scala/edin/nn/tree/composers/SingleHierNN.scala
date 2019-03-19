package edin.nn.tree.composers

import edin.nn.{SimpleStateLazy, State}
import edin.nn.layers.{Layer, SingleLayer}
import edin.nn.DyFunctions._
import edin.nn.sequence.{MultiRNN, MultiRNNConfig}
import edu.cmu.dynet.{Expression, ParameterCollection}

sealed case class SingleHierNNConfig(
                                      inDim          : Int,
                                      outDim         : Int,
                                      seqType        : String,
                                      withLayerNorm  : Boolean,
                                      withWeightNorm : Boolean,
                                      seqDropout     : Float
                                    ) extends CompositionFunctionConfig {
  def construct()(implicit model: ParameterCollection) = new SingleHierNN(this)
}


class SingleHierNN(c:SingleHierNNConfig)(implicit model: ParameterCollection) extends CompositionFunction {

  var leftRNN:MultiRNN = _
  var rightRNN:MultiRNN = _
  var compressor:Layer = _

  var outDim:Int = _

  define()

  protected def define()(implicit model: ParameterCollection): Unit = {
    outDim = c.outDim
    leftRNN  = MultiRNNConfig(
      rnnType           = c.seqType ,
      inDim             = c.outDim ,
      outDim            = c.outDim ,
      layers            = 1 ,
      withLayerNorm     = c.withLayerNorm,
      withWeightNorm    = c.withWeightNorm,
      dropProbRecurrent = c.seqDropout ).construct
    rightRNN = MultiRNNConfig(
      rnnType           = c.seqType ,
      inDim             = c.outDim ,
      outDim            = c.outDim ,
      layers            = 1 ,
      withLayerNorm     = c.withLayerNorm,
      withWeightNorm    = c.withWeightNorm,
      dropProbRecurrent = c.seqDropout ).construct
    compressor = SingleLayer.compressor(2*c.outDim+c.inDim, c.outDim)
  }

  override def initState(h: Expression): State =
    SimpleStateLazy(
      compressor(
        concat(
          h,
          zeros(2*outDim)
        )
      )
    )

  override def compose(childrenStates: List[State], parentRep: Expression): State = {
    val childrenVectors = childrenStates.map{_.h}
    val l2r = leftRNN.transduce(childrenVectors)
    val r2l = rightRNN.transduceBackward(childrenVectors)
    val leftEndRep  = r2l.head
    val rightEndRep = l2r.last
    SimpleStateLazy(
      compressor(
        concat(
          leftEndRep,
          rightEndRep,
          parentRep
        )
      )
    )
  }

}
