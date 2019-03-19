package edin.nn.sequence
import edin.nn.DynetSetup
import edin.nn.DyFunctions._
import edu.cmu.dynet._

sealed case class SingleDyLSTMConfig(
                                      inDim   : Int,
                                      outDim  : Int,
                                      dropout : Float,
                                      withLayerNorm  : Boolean
                                  ){
  def construct()(implicit model: ParameterCollection) = new SingleDyLSTM(this)
}

class SingleDyLSTM(config:SingleDyLSTMConfig)(implicit model: ParameterCollection) extends RecurrentNN {

  assert(config.inDim % 2 == 0)
  assert(config.outDim % 2 == 0)

  private val dyLSTM = new LstmBuilder(1, config.inDim, config.outDim, model, config.withLayerNorm)
  private val initHidden:Parameter = model.addParameters(config.inDim)

  private var currGraph = -1

  override def initState(): RecurrentState = {
    initState(initHidden)
  }

  override def initState(start_lowest_rnn_hidden: Expression): RecurrentState = {
    if(currGraph != DynetSetup.cg_id){
      currGraph = DynetSetup.cg_id
      dyLSTM.newGraph()
    }

    val v = new ExpressionVector()
    v.add(zeros(config.inDim)) // cell   initialization
    v.add(start_lowest_rnn_hidden) // hidden initialization
    dyLSTM.startNewSequence(v)

    if(dropoutIsEnabled){
      dyLSTM.setDropout(config.dropout)
    }else{
      dyLSTM.disableDropout()
    }

    val out = dyLSTM.addInput(zeros(config.inDim))
    val stateId= dyLSTM.state()
    new SingleDyLSTMState(builder=dyLSTM, stateId=stateId, output=out)
  }


  class SingleDyLSTMState(builder:RnnBuilder, stateId:Int, output:Expression) extends RecurrentState{

    override def addInput(x: Expression): RecurrentState = {
      val out = dyLSTM.addInput(stateId, x)
      val newStateId= dyLSTM.state()
      new SingleDyLSTMState(builder=dyLSTM, stateId=newStateId, output=out)
    }

    override def h: Expression = output

  }

}

