package edin.search

import edin.nn.DyFunctions._
import edin.algorithms.Sampler

class NeuralSampler(maxExpansion:Int) extends NeuralBeam {

  override def search(initStates: List[PredictionState]): List[PredictionState] = {
    assert(initStates.tail.isEmpty)
    NeuralSampler.sample(initStates.head, maxExpansion)._1 :: Nil
  }

}

object NeuralSampler {

  def sample(initState:PredictionState, maxExpansion:Int) : (PredictionState, Float, List[Int]) = {
    var state = initState
    var actions = List[Int]()
    var actionCount = 0

    while(!state.isFinished && actionCount<maxExpansion){
      val logProbs:List[Float] = state.nextActionLogProbsLocalExp.toList
      val probs: List[(Int, Double)] = logProbs.map { x => math.exp(x.toDouble) }.zipWithIndex.map(_.swap)
      val a = Sampler.sample(probs)
      actions ::= a
      state = state.applyAction(a)
      actionCount += 1
    }

    (state, state.score, actions.reverse)
  }

}

