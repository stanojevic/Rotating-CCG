package edin.search

import edin.nn.DyFunctions._

class NeuralBeamWordSynchronous(
                                 k                       : Int,
                                 k_wd                    : Int,
                                 k_ft                    : Int,
                                 maxWordExpansion        : Int,
                                 stopWhenBestKfinished   : Int=0,
                                 stopWhenKfinished       : Int=0
                               ) extends NeuralBeam {
  assert((stopWhenBestKfinished>0) != (stopWhenKfinished>0))

  import NeuralBeamWordSynchronous._

  override def search(initStates: List[PredictionState]): List[PredictionState] = {
    ???
  }

  override def searchConstrained(initStates : List[PredictionState], constraints : List[Int]) : List[PredictionState] = {
    var states = initStates
    for(constraint <- constraints){
      states = oneStep(states, constraint)
    }
    states.filter(_.isFinished)
  }

  private def oneStep(states:List[PredictionState], w:Int) : List[PredictionState] = {
    var nextWord = states.filter(state => state.isFinished).map(state => (state, -1, state.score))
    // var thisWord = states.filterNot(state => state.isFinished).flatMap(stateExpansionConstrained(_, w))
    var thisWordStates = states.filterNot(state => state.isFinished) // .flatMap(stateExpansionConstrained(_, w))

    while(nextWord.size <= k && thisWordStates.nonEmpty){
      // var fringe = thisWord.flatMap{ case (state, action, _) => stateExpansionConstrained(state.applyAction(action), w)}
      val fringe = thisWordStates.flatMap(stateExpansionConstrained(_, w))
      var (ready, nonready) = fringe.partition{case (state, _, _) => state.isReadyToEmit || state.isFinished}
      ready = ready.sortBy(-_._3)
      nextWord ++= ready.take(k_ft) // fastrack
      ready = ready.drop(k_ft)
      val (ready2, nonready2) = (ready++nonready).sortBy(-_._3).take(k).partition{case (state, _, _) => state.isReadyToEmit || state.isFinished}
      nextWord ++= ready2
      thisWordStates = sortTakeTopApply(nonready2, k)
    }
    sortTakeTopApply(nextWord, k_wd)
  }

  private def sortTakeTopApply(xs:List[(PredictionState, Int, Float)], top:Int) : List[PredictionState] = {
    xs.sortBy(-_._3).take(top).map{ case (state, action, _) =>
      if(action < 0)
        state
      else
        state.applyAction(action)
    }
  }

  private def stateExpansionConstrained(state:PredictionState, w:Int) : List[(PredictionState, Int, Float)] =
    topActions(state, 100000).map{ case (a, cost) =>
      if(state.isPreemissionAction(a)) {
        val newState = state.applyAction(a)
        (newState, w, newState.nextActionLogProbsTotalValues(w))
      }else{
        (state, a, cost)
      }
    }

//  override def search(initStates: List[PredictionState]): List[PredictionState] = {
//    val initStates2 = inbetweenWordPass(initStates)
//    var (finishedStates, unfinishedStates) = initStates2.sortBy(-_.score).partition(_.isFinished)
//    var wordExpansions = 0
//    while(wordExpansions <= maxWordExpansion && ! stop(finishedStates, unfinishedStates)){
//      unfinishedStates = singleDerivationMove(unfinishedStates, cutSize = wordBeamSize)
//
//      finishedStates ++= unfinishedStates.filter(_.isFinished)
//      finishedStates = finishedStates.sortBy(-_.score).take(wordBeamSize)
//
//      unfinishedStates = unfinishedStates.filterNot(_.isFinished).sortBy(-_.score)
//      unfinishedStates = inbetweenWordPass(unfinishedStates)
//
//      wordExpansions += 1
//    }
//    finishedStates
//  }
//
//  private def stop(finished:List[PredictionState], unfinished:List[PredictionState]) : Boolean = {
//    val finishedCount = finished.size
//    val unfinishedCount = unfinished.size
//    if(stopWhenKfinished > 0 && finishedCount>=stopWhenKfinished)
//      return true
//    if(unfinishedCount == 0)
//      return true
//    if(stopWhenBestKfinished>0 && finishedCount>=stopWhenBestKfinished && finished.take(stopWhenBestKfinished).last.score > unfinished.head.score)
//      return true
//    false
//  }
//
//  override def searchConstrained(initStates : List[PredictionState], constraints : List[Int]) : List[PredictionState] = {
//    var currStates = initStates
//    for(constraint <- constraints){
//      currStates = inbetweenWordPass(currStates)
//      // currStates = currStates.map(_.applyAction(constraint))
//      currStates = currStates.sortBy(-_.nextActionLogProbsTotalValues(constraint)).take(wordBeamSize).map(_.applyAction(constraint))
//    }
//    currStates.filter(_.isFinished)
//  }
//
//  private def inbetweenWordPass(currStates: List[PredictionState]) : List[PredictionState] = {
//    // explained in page 3 https://arxiv.org/pdf/1806.04127.pdf
//    var (nextWordBeam, thisWordBeam) = currStates.partition(s => s.isReadyToEmit || s.isFinished)
//    while( nextWordBeam.size < latentBeamSize && thisWordBeam.nonEmpty ){
//      val fringe:List[PredictionState] = thisWordBeam.flatMap { state =>
//        topActions(state, latentBeamSize).map{ case (a, _) =>
//          state.applyAction(a)
//        }
//      }
//      nextWordBeam ++= fringe.filter(s => s.isReadyToEmit || s.isFinished)
//      thisWordBeam = fringe.filterNot(s => s.isReadyToEmit || s.isFinished).sortBy(-_.score).take(latentBeamSize)
//    }
//    nextWordBeam.sortBy(-_.score).take(latentBeamSize)
//  }

}

object NeuralBeamWordSynchronous{

//  private def singleDerivationMove(currStates: List[PredictionState], cutSize:Int) : List[PredictionState] = {
//    currStates.flatMap{ state =>
//      topActions(state, cutSize).map((state, _))
//    }.sortBy(-_._2._2).take(cutSize).map{ case (state, (a, cost)) =>
//        state.applyAction(a)
//    }
//  }

  private def topActions(state:PredictionState, k: Int): List[(Int, Float)] =
    argmaxWithScores(state.nextActionLogProbsTotalValues, k)

}

