package edin.nn.tree.encoders

import edin.general.Any2Int
import edin.nn.tree._
import edu.cmu.dynet.ParameterCollection


sealed case class MultiTreeEncoderConfig(
                                        layers             : Int             ,
                                        inDim              : Int             ,
                                        outDim             : Int             ,
                                        encoderType        : String          ,
                                        maxArity           : Int             ,
                                        dropout            : Float           ,
                                        withLayerNorm      : Boolean         ,
                                        withWeightNorm     : Boolean         ,
                                        compositionName    : String          , // if encoderType == "Composer"
                                        ignoreHigherInput  : Boolean         , // if encoderType == "Composer" and uses SpanRep
                                        gcnActivationName  : String          , // if encoderType == "GCN"
                                        gcnGated           : Boolean         , // if encoderType == "GCN"
                                        gcnEdgeSpecificBias: Boolean         , // if encoderType == "GCN"
                                        gcnE2I             : Any2Int[String]   // if encoderType == "GCN"
                                        ){
  def construct()(implicit model: ParameterCollection) = new MultiTreeEncoder(this)
}


class MultiTreeEncoder(c:MultiTreeEncoderConfig)(implicit model: ParameterCollection) extends TreeEncoder {

  var layers: List[TreeEncoder] = _

  define()

  override def reencode(root: EncodableNode): Unit = {
    for(layer <- layers){
      layer.reencode(root)
    }
  }

  protected def define()(implicit model: ParameterCollection): Unit = {
    layers ::= constructTreeLayer(c, initial = true)
    for(i <- 2 to c.layers){
      layers ::= constructTreeLayer(c, initial = false)
    }
    layers = layers.reverse
  }

  private def constructTreeLayer(c:MultiTreeEncoderConfig, initial:Boolean)(implicit model: ParameterCollection) : TreeEncoder = {
    val inDim = if(initial) c.inDim else c.outDim
    c.encoderType match {
      case "GCN"      => SingleGCNConfig(
                           inDim             = inDim    ,
                           outDim            = c.outDim ,
                           activationName    = c.gcnActivationName ,
                           gated             = c.gcnGated ,
                           edgeSpecificBias  = c.gcnEdgeSpecificBias ,
                           e2i               = c.gcnE2I,
                           edgeDropout       = c.dropout
                         ).construct()
      case "IORNN"    => SingleIORNNConfig(
                           inDim      = inDim,
                           outDim     = c.outDim,
                           seqType    = "LSTM",
                           withLayerNorm = false,// TODO
                           withWeightNorm = false, // TODO
                           seqDropout = c.dropout
                         ).construct()
      case "Composer" => CompositionFunctionFullEncoderConfig(
                           compositionType   = c.compositionName,
                           inDim             = inDim,
                           outDim            = c.outDim,
                           maxArity          = c.maxArity,
                           ignoreHigherInput = c.ignoreHigherInput,
                           withLayerNorm     = c.withLayerNorm,
                           withWeightNorm    = c.withWeightNorm,
                           dropout           = c.dropout
                         ).construct()
    }
  }

}
