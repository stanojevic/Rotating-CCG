package edin.nn.sequence

import edin.general.YamlConfig
import edin.nn.{StateClosed, State}
import edu.cmu.dynet.{Expression, ParameterCollection}

case class NeuralStackBuilderConfig[T <: State](
                                        rnnConfig : MultiRNNConfig
                                      ){
  def construct()(implicit model: ParameterCollection) = {
    new NeuralStackBuilder(this)
  }
}

object NeuralStackBuilderConfig{

  def fromYaml[T <: State](conf:YamlConfig) : NeuralStackBuilderConfig[T] = {
    NeuralStackBuilderConfig(
      rnnConfig = MultiRNNConfig.fromYaml(conf)
    )
  }

}


class NeuralStackBuilder[T <: State](stackLSTMConfig: NeuralStackBuilderConfig[T])(implicit model: ParameterCollection){

  var rnn:MultiRNN = stackLSTMConfig.rnnConfig.construct()

  def empty : NeuralStack[T] = {
    new NeuralStack[T](None, None, rnn.initState())
  }
}

class NeuralStack[T <: State](
                               prevState:Option[NeuralStack[T]],
                               el:Option[T],
                               prevRNNstate: RecurrentState
                           ) extends StateClosed {

  private val currRNNstate = el match {
    case Some(x) => prevRNNstate.addInput(x.h)
    case None => prevRNNstate
  }

  def bottomToTop : List[T] = prevState match {
    case Some(x) => x.bottomToTop :+ el.get
    case None => List()
  }

  override val h: Expression = currRNNstate.h

  def first : T = this(0)
  def second : T = this(1)

  val size:Int = prevState match {
    case None => 0
    case Some(x) => x.size+1
  }

  def apply(i:Int):T = {
    if(i==0){
      el.get
    }else{
      prevState.get(i-1)
    }
  }

  def nonEmpty:Boolean = ! isEmpty

  def isEmpty:Boolean = this.size == 0

  def pop:NeuralStack[T] = prevState.get

  def push(el:T):NeuralStack[T] = {
    new NeuralStack(Some(this), Some(el), currRNNstate)
  }

  def head: T = first // some aliases

  def tail: NeuralStack[T] = pop   // some aliases

}

