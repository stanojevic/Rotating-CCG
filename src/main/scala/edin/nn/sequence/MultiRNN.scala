package edin.nn.sequence

import edin.general.YamlConfig
import edu.cmu.dynet.{Expression, ParameterCollection}

sealed case class MultiRNNConfig(
                                  rnnType           : String,
                                  inDim             : Int,
                                  outDim            : Int,
                                  layers            : Int,
                                  withLayerNorm     : Boolean,
                                  withWeightNorm    : Boolean,
                                  dropProbRecurrent : Float
                                ) extends SequenceEncoderConfig {
  def construct()(implicit model: ParameterCollection) = new MultiRNN(this)
}

object MultiRNNConfig{

  def fromYaml(conf:YamlConfig) : MultiRNNConfig = {
    MultiRNNConfig(
      rnnType           = conf("rnn-type").str,
      inDim             = conf("in-dim"  ).int,
      outDim            = conf("out-dim" ).int,
      layers            = conf("layers" ).int,
      withLayerNorm     = conf.getOrElse("with-layer-norm" , false),
      withWeightNorm    = conf.getOrElse("with-weight-norm", false),
      dropProbRecurrent = conf.getOrElse("dropout", 0f)
    )
  }

}



class MultiRNN(c:MultiRNNConfig)(implicit model: ParameterCollection) extends RecurrentNN {

  private var rnns = List[RecurrentNN]()

  define()

  private def produceInitState(start_hidden:Option[Expression]) : RecurrentState = {
    var newStates = List[RecurrentState]()
    start_hidden match {
      case Some(x) =>
        newStates ::= rnns.head.initState(x)
      case None =>
        newStates ::= rnns.head.initState()
    }
    for(rnn <- rnns.tail){
      newStates ::= rnn.initState().addInput(newStates.head.output())
    }
    new MultiRNNState(newStates.reverse)
  }

  override def initState(start_hidden: Expression): RecurrentState =
    produceInitState(Some(start_hidden))

  override def initState(): RecurrentState =
    produceInitState(None)

  protected def define()(implicit model: ParameterCollection) : Unit = {

    assert(c.layers > 0)

    rnns ::= RecurrentNN.singleFactory(c.rnnType, c.inDim, c.outDim, c.dropProbRecurrent, c.withLayerNorm, c.withWeightNorm)
    for(_ <- 2 to c.layers){
      rnns ::= RecurrentNN.singleFactory(c.rnnType, c.outDim, c.outDim, c.dropProbRecurrent, c.withLayerNorm, c.withWeightNorm)
    }
    rnns = rnns.reverse
  }

  class MultiRNNState(rnnStates:List[RecurrentState]) extends RecurrentState {

    override val h: Expression = rnnStates.last.output()

    override def addInput(x: Expression): RecurrentState = {
      var newStates = List[RecurrentState]()
      var currOut = x
      for(rnnState <- rnnStates){
        val newState = rnnState.addInput(currOut)
        currOut = newState.output()
        newStates ::= newState
      }
      new MultiRNNState(newStates.reverse)
    }

  }

}


