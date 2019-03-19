package edin.nn.tree.encoders

import edin.general.Any2Int
import edin.general.trees.dependency.DepNode
import edin.nn.{StateClosed, SimpleState, State}
import edin.nn.DyFunctions._
import edin.nn.tree.EncodableNode
import edu.cmu.dynet.{Expression, LookupParameter, Parameter, ParameterCollection}

sealed case class SingleGCNConfig(
                                 inDim:Int,
                                 outDim:Int,
                                 activationName:String,
                                 gated:Boolean,
                                 edgeSpecificBias:Boolean,
                                 e2i:Any2Int[String],
                                 edgeDropout:Float
                                 ){
  def construct()(implicit model: ParameterCollection) = new SingleGCN(this)
}


sealed case class StateGCN(curr_h: Expression, parentH:Expression, childrenH:List[Expression]) extends StateClosed{
  override val h:Expression = curr_h
}

class SingleGCN(c:SingleGCNConfig)(implicit model: ParameterCollection) extends TreeEncoder {

  var gating_W_in   :Parameter = _
  var gating_W_out  :Parameter = _
  var gating_W_loop :Parameter = _
  var gating_b_in   :Parameter = _
  var gating_b_out  :Parameter = _
  var gating_b_loop :Parameter = _
  var gating_edge_biases_in  :LookupParameter = _
  var gating_edge_biases_out :LookupParameter = _

  var W_in   :Parameter = _
  var W_out  :Parameter = _
  var W_loop :Parameter = _
  var b_in   :Parameter = _
  var b_out  :Parameter = _
  var b_loop :Parameter = _
  var edge_biases_in  :LookupParameter = _
  var edge_biases_out :LookupParameter = _

  var activation: Expression => Expression = _
  var edgeDropout: Float = _

  var gated: Boolean = _
  var edgeSpecificBias:Boolean = _
  var e2i:Any2Int[String] = _

  define()

  private implicit def anyRef2GCNstate(x:State) : StateGCN = x.asInstanceOf[StateGCN]

  override def reencode(root: EncodableNode): Unit = {
    transferParentVector(root)
    computeRep(root)
  }

  private def computeRep(node:EncodableNode) : Unit = {
    for(child <- node.children)
      computeRep(child)

    val state: StateGCN = node.nn
    var edgeVectors = List[Expression]()

    // LOOP edge
    var logitLoop = W_loop * node.nn.h + b_loop
    if(gated)
      logitLoop *= gating_b_loop
    edgeVectors ::= logitLoop

    // INCOMING edge
    if(state.parentH != null){
      var logitIn = W_in * state.parentH + findBias(node, edge_biases_in, b_in)
      if(gated){
        val gate = gating_W_in * state.parentH + findBias(node, gating_edge_biases_in, gating_b_in)
        logitIn *= gate
      }
      edgeVectors ::= logitIn
    }

    // OUTGOING edge
    for((child, childH) <- (node.children zip state.childrenH)){
      var logitOut = W_out * childH + findBias(child, edge_biases_out, b_out)
      if(gated){
        val gate = gating_W_out * childH + findBias(child, gating_edge_biases_out, gating_b_out)
        logitOut *= gate
      }
      edgeVectors ::= logitOut
    }

    // MASK edge
    val mask = dropout(ones(edgeVectors.size), edgeDropout)
    val total = Expression.concatenateCols(edgeVectors)*mask
    val new_h = activation(total)
    node.nn = SimpleState(new_h)
  }

  private def findBias(node:EncodableNode, edgeBiases:LookupParameter, bias:Parameter) : Expression = {
    if(edgeSpecificBias){
      val rel:String = node.asInstanceOf[DepNode].relation
      edgeBiases(e2i(rel))
    }else{
      bias
    }
  }

  private def transferParentVector(node:EncodableNode, startingNode:Boolean=true) : Unit = {
    if(startingNode){
      node.nn = StateGCN(
        curr_h = node.nn.h,
        parentH = null,
        childrenH = node.children.map{_.nn.h}
      )
    }
    for(child <- node.children){
      child.nn = StateGCN(
        curr_h = child.nn.h,
        parentH = node.nn.h,
        childrenH = child.children.map{_.nn.h}
      )
      transferParentVector(child, startingNode=false)
    }
  }

  protected def define()(implicit model: ParameterCollection): Unit = {

    gated = c.gated
    edgeSpecificBias = c.edgeSpecificBias
    e2i = c.e2i
    edgeDropout = c.edgeDropout

    activation = activationFactory(c.activationName)

    if(c.gated){
      gating_W_in        = model.addParameters((1,c.inDim))
      gating_W_out       = model.addParameters((1,c.inDim))
      gating_W_loop      = model.addParameters((1,c.inDim))
      if(c.edgeSpecificBias) {
        gating_edge_biases_in  = model.addLookupParameters(c.e2i.size, 1)
        gating_edge_biases_out = model.addLookupParameters(c.e2i.size, 1)
      }else{
        gating_b_in   = model.addParameters(1)
        gating_b_out  = model.addParameters(1)
      }
      gating_b_loop = model.addParameters(1)
    }

    W_in        = model.addParameters((c.outDim,c.inDim))
    W_out       = model.addParameters((c.outDim,c.inDim))
    W_loop      = model.addParameters((c.outDim,c.inDim))
    if(c.edgeSpecificBias){
      edge_biases_in  = model.addLookupParameters(c.e2i.size, c.outDim)
      edge_biases_out = model.addLookupParameters(c.e2i.size, c.outDim)
    }else{
      b_in        = model.addParameters(c.outDim)
      b_out       = model.addParameters(c.outDim)
    }
    b_loop      = model.addParameters(c.outDim)
  }

}
