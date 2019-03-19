package edin.nn.tree.encoders

import edin.nn.tree.EncodableNode
import edin.nn.tree.composers.{CompositionFunction, MultiCompositionFunctionConfig}
import edu.cmu.dynet.ParameterCollection

sealed case class CompositionFunctionFullEncoderConfig(
                                                        compositionType   : String,
                                                        inDim             : Int,
                                                        outDim            : Int,
                                                        maxArity          : Int,
                                                        ignoreHigherInput : Boolean,
                                                        withLayerNorm     : Boolean,
                                                        withWeightNorm    : Boolean,
                                                        dropout           : Float
                                                      ){
  def construct()(implicit model: ParameterCollection) = new CompositoinFunctionFullEncoder(this)
}


class CompositoinFunctionFullEncoder(c:CompositionFunctionFullEncoderConfig)(implicit model: ParameterCollection) extends TreeEncoder {

  var composer:CompositionFunction = _

  define()

  override def reencode(root: EncodableNode): Unit = {
    recEncode(root)
  }

  private def recEncode(node: EncodableNode): Unit ={
    for(child <- node.children)
      recEncode(child)

    if(node.isTerm){
      node.nn = composer.initState(node.nn.h)
    }else{
      node.nn = composer.compose(node.children.map{_.nn}, node.nn.h)
    }
  }

  protected def define()(implicit model: ParameterCollection): Unit = {
    // composer = MultiCompositionFunction.singleComposerFactory(c.compositionType, c.inDim, c.outDim, c.maxArity, c.dropout)
    composer = MultiCompositionFunctionConfig(
      compositionType   = c.compositionType,
      inDim             = c.inDim,
      outDim            = c.outDim,
      layers            = 1,
      maxArity          = c.maxArity,
      ignoreHigherInput = c.ignoreHigherInput,
      withLayerNorm     = c.withLayerNorm,
      withWeightNorm    = c.withWeightNorm,
      dropout           = c.dropout
    ).construct()
  }

}
