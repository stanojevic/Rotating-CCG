package edin.nn.tree.encoders

import edin.nn._
import DyFunctions._
import edin.nn.layers.{Layer, MLPConfig}
import edin.nn.sequence.{MultiRNN, MultiRNNConfig}
import edin.nn.tree.EncodableNode
import edu.cmu.dynet.{Expression, ParameterCollection}

import scala.language.implicitConversions

sealed case class SingleIORNNConfig(
                                     inDim          : Int,
                                     outDim         : Int,
                                     seqType        : String,
                                     withLayerNorm  : Boolean,
                                     withWeightNorm : Boolean,
                                     seqDropout     : Float
                                   ){
  def construct()(implicit model: ParameterCollection) = new SingleIORNN(this)
}


sealed case class IOstate(
                           curr_h:Expression,
                       var inside:Expression,
                       var outside:Expression,
                       var childrenInsideL2R:Array[Expression],
                       var childrenInsideR2L:Array[Expression]
                     ) extends StateClosed {
  override val h: Expression = curr_h
}

class SingleIORNN(c:SingleIORNNConfig)(implicit model: ParameterCollection) extends TreeEncoder{

  private var insideLeftRNN:MultiRNN = _
  private var insideRightRNN:MultiRNN = _
  private var insideFF:Layer = _
  private var outsideFF:Layer = _

  define()

  private var outDim:Int = _

  private implicit def anyRef2IOstate(x:State):IOstate = x.asInstanceOf[IOstate]

  override def reencode(root: EncodableNode): Unit = {
    prepareStates(root)
    inside(root)
    outside(root)
    combine(root)
  }

  private def combine(node: EncodableNode) : Unit = {
    for(child <- node.children)
      combine(child)
    val new_h = concat(node.nn.inside, node.nn.outside)
    node.nn = SimpleState(new_h)
  }

  private def inside(node: EncodableNode) : Unit = {
    for(child <- node.children)
      inside(child)
    var leftRepr : Expression = null
    var rightRepr: Expression = null
    if(node.isTerm){
      node.nn.childrenInsideL2R = null
      node.nn.childrenInsideR2L = null
      leftRepr  = zeros(outDim/2)
      rightRepr = zeros(outDim/2)
    } else {
      val childrenVectors = node.children.map{_.nn.h}
      val l2r:List[Expression] = insideLeftRNN.transduce(childrenVectors)
      val r2l:List[Expression] = insideRightRNN.transduceBackward(childrenVectors)
      node.nn.childrenInsideL2R = l2r.toArray
      node.nn.childrenInsideR2L = r2l.toArray
      leftRepr = l2r.last
      rightRepr = r2l.head
    }
    node.nn.inside = insideFF(concat(node.nn.h, leftRepr, rightRepr))
  }

  private def outside(node: EncodableNode, startingNode:Boolean=true) : Unit = {
    if(startingNode)
      node.nn.outside = zeros(outDim/2)

    for((child, i) <- node.children.zipWithIndex) {
      val leftInside  = if (i - 1 < 0                 ) zeros(outDim/2) else node.nn.childrenInsideL2R(i-1)
      val rightInside = if (i + 1 > node.children.size) zeros(outDim/2) else node.nn.childrenInsideR2L(i+1)
      child.nn.outside = outsideFF(concat(leftInside, rightInside, node.nn.outside))
      outside(child, startingNode = false)
    }
  }

  private def prepareStates(node: EncodableNode) : Unit ={
    node.nn = IOstate(node.nn.h, null, null, null, null)
    for(child <- node.children)
      prepareStates(child)
  }

  protected def define()(implicit model: ParameterCollection): Unit = {
    assert(c.outDim % 2 == 0)
    outDim = c.outDim
    insideLeftRNN  = MultiRNNConfig(
      rnnType           = c.seqType    ,
      inDim             = c.outDim/2   ,
      outDim            = c.outDim/2   ,
      layers            = 1            ,
      withLayerNorm     = c.withLayerNorm     ,
      withWeightNorm    = c.withWeightNorm     ,
      dropProbRecurrent = c.seqDropout ).construct
    insideRightRNN = MultiRNNConfig(
      rnnType           = c.seqType    ,
      inDim             = c.outDim/2   ,
      outDim            = c.outDim/2   ,
      layers            = 1            ,
      withLayerNorm     = c.withLayerNorm     ,
      withWeightNorm    = c.withWeightNorm    ,
      dropProbRecurrent = c.seqDropout ).construct
    insideFF = MLPConfig(
      activations   = List("tanh"),
      sizes         = List(c.outDim+c.inDim, c.outDim/2),
      withLayerNorm = c.withLayerNorm,
      withWeightNorm = c.withWeightNorm
    ).construct
    outsideFF = MLPConfig(
      activations   = List("tanh"),
      sizes         = List(3*c.outDim/2, c.outDim/2),
      withLayerNorm = c.withLayerNorm,
      withWeightNorm = c.withWeightNorm
    ).construct
  }

}
