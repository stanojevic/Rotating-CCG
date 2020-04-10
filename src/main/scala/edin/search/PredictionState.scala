package edin.search

import edu.cmu.dynet.Expression
import edin.nn.DyFunctions._

trait PredictionState{

  val scoreExp                   : Expression
  final lazy val score           : Float     = scoreExp.toFloat
  val nextActionLogProbsLocalExp : Expression

  final lazy val nextActionLogProbsTotalExp    : Expression = nextActionLogProbsLocalExp + scoreExp
  final lazy val nextActionLogProbsTotalValues : Seq[Float] = nextActionLogProbsTotalExp.toSeq

  val isReadyToEmit        : Boolean

  val isFinished           : Boolean

  def applyAction(a:Int)   : PredictionState

  def unwrapState[T <: PredictionState] : T = this.asInstanceOf[T]

  override def equals(o: Any): Boolean = ???

}

class EnsamblePredictionState(
                           val states   : List[PredictionState],
                               alpha    : Double = 1, /** the smaller the alpha the sharper the distribution */
                           val scoreExp : Expression = scalar(0f) // log of prob 1.0
                             ) extends PredictionState {

  assert(states.nonEmpty)

  private def renormalize(logProbs:Expression) : Expression =
    if(alpha == 1f){
      logProbs
    }else{
      val newLogs = logProbs*alpha
      newLogs - log(sumElems(exp(newLogs)))
    }

  override lazy val nextActionLogProbsLocalExp : Expression =
    if(states.size == 1)
      renormalize(states.head.nextActionLogProbsLocalExp)
    else
      renormalize(averageLogSoftmaxes(states.map(_.nextActionLogProbsLocalExp)))

  override lazy val isReadyToEmit           : Boolean    = states.exists(_.isReadyToEmit)
  override lazy val isFinished              : Boolean    = states.exists(_.isFinished)

  override def unwrapState[T <: PredictionState] : T = states.head.unwrapState[T]

  override def applyAction(a: Int): PredictionState =
    new EnsamblePredictionState(
      states   = states.map(_ applyAction a),
      scoreExp = nextActionLogProbsTotalExp(a),
      alpha    = alpha
    )

  override def equals(o: Any): Boolean = o match {
    case that:EnsamblePredictionState => this.states.head == that.states.head
    case _                            => false
  }

}


