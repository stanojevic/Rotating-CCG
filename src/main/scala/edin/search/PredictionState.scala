package edin.search

import edu.cmu.dynet.Expression
import edin.nn.DyFunctions._

trait PredictionState{

  val scoreExp                      : Expression
  final lazy val score              : Float = scoreExp.toFloat
  val nextActionLogProbsLocal       : Expression

  lazy val nextActionLogProbsTotal       : Expression = nextActionLogProbsLocal + scoreExp
  lazy val nextActionLogProbsTotalValues : Seq[Float] = nextActionLogProbsTotal.toSeq

  val isReadyToEmit        : Boolean

  val isFinished           : Boolean

  def applyAction(a:Int)   : PredictionState

  def unwrapState[T <: PredictionState] : T = this.asInstanceOf[T]

  def isPreemissionAction(a:Int) : Boolean

}

class EnsamblePredictionState(
                               states : List[PredictionState],
                               alpha : Double = 1f, /** the smaller the alpha the sharper the distribution */
                               val scoreExp  : Expression = scalar(0f) // log of prob 1.0
                             ) extends PredictionState {

  assert(states.nonEmpty)

  private def renormalize(logProbs:Expression) : Expression = {
    if(alpha == 1f){
      logProbs
    }else{
      val newLogs = logProbs*alpha
      newLogs - log(sumElems(exp(newLogs)))
    }
  }

  override val nextActionLogProbsLocal : Expression = renormalize(averageLogSoftmaxes(states.map{_.nextActionLogProbsLocal}))
  override val isReadyToEmit           : Boolean    = states.exists(_.isReadyToEmit)
  override val isFinished              : Boolean    = states.exists(_.isFinished)

  override def unwrapState[T <: PredictionState] : T = states.maxBy(_.score).asInstanceOf[T]

  override def applyAction(a: Int): PredictionState = {
    val newStates = states.map{_.applyAction(a)}
    val actionScore = nextActionLogProbsTotal(a)
    new EnsamblePredictionState(states=newStates, scoreExp=actionScore, alpha=alpha)
  }

  def isPreemissionAction(a:Int) : Boolean = states.exists(_.isPreemissionAction(a))

}


