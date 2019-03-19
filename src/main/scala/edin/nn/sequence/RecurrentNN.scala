package edin.nn.sequence

import edin.nn.{StateClosed, State}
import edu.cmu.dynet.{Expression, ParameterCollection}

trait RecurrentNN extends SequenceEncoder {

  def initState() : RecurrentState

  def initState(start_lowest_rnn_hidden:Expression) : RecurrentState

  def transduce(vectors:List[Expression]) : List[Expression] = {
    this.initState().transduce(vectors)
  }

  // keeps the order but only computes representation backward
  def transduceBackward(vectors:List[Expression]) : List[Expression] = {
    transduce(vectors.reverse).reverse
  }

}

object RecurrentNN{

  def singleFactory(rnnType:String, inDim:Int, outDim:Int, dropProb:Float, withLayerNorm:Boolean, withWeightNorm:Boolean)(implicit model:ParameterCollection) : RecurrentNN = {
    rnnType.toLowerCase match {
      case "lstm" => SingleLSTMConfig(
        inDim          = inDim        ,
        outDim         = outDim       ,
        dropout        = dropProb     ,
        withLayerNorm  = withLayerNorm,
        withWeightNorm = withWeightNorm
      ).construct
      case "dylstm" => SingleDyLSTMConfig(
        inDim          = inDim        ,
        outDim         = outDim       ,
        dropout        = dropProb     ,
        withLayerNorm  = withLayerNorm
      ).construct
    }
  }

}

trait RecurrentState extends StateClosed {

  final def output() : Expression = h

  def addInput(x:Expression) : RecurrentState

  def transduce(vectors:List[Expression]) : List[Expression] = {
    var output = List[Expression]()
    var currState = this
    for(vec <- vectors){
      currState = currState.addInput(vec)
      output ::= currState.output()
    }
    output.reverse
  }

}

