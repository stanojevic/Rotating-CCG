package edin.search

import edu.cmu.dynet.Expression
import edin.nn.DyFunctions._

object BSO{

  sealed trait UpdateMethod
  case object EarlyUpdate extends UpdateMethod
  case object LaSOUpdate  extends UpdateMethod
  case object AllUpdate   extends UpdateMethod

  def loss(
            goldStates     : List[PredictionState],
            beamSize       : Int,
            distanceMetric : (PredictionState, PredictionState) => Float,
            updateMethod   : UpdateMethod
          ) : Expression = {
    val initState     = goldStates.head
    var goldLeftover  = goldStates.tail
    var loss          = scalar(0)
    var finished      = List[PredictionState]()
    var beam          = List[PredictionState](initState)
    var prevGoldState = initState
    var subLossCount  = 0
    while(goldLeftover.nonEmpty && !(updateMethod == EarlyUpdate && subLossCount > 0)){
      val currGoldState = goldLeftover.head
      goldLeftover  = goldLeftover.tail

      if(beam.isEmpty && goldLeftover.nonEmpty){
        beam = List(prevGoldState)
      }

      if(beam.nonEmpty){
        beam = bestK(beamSize, beam)
      }

      if(goldLeftover.isEmpty){
        // DECODING FINISHED
        assert(currGoldState.isFinished)
        val candidates = (beam++finished).filterNot(_ == currGoldState)
        val violations = candidates.filter(_.score+1>currGoldState.score)
        val selectedViolations =
          if(updateMethod == AllUpdate) violations else
          if(violations.nonEmpty)       violations.maxBy(_.score)::Nil
          else                          Nil
        for(violation <- selectedViolations){
          loss += getSubLoss(currGoldState, violation, distanceMetric)
          subLossCount += 1
        }
      }else{
        // DECODING NOT FINISHED
        val candidates = beam.filterNot(_ == currGoldState)
        val violations = candidates.filter(_.score+1>currGoldState.score)

        val (fringeFinished, fringeUnfinished) = beam.partition(_.isFinished)
        finished ++= fringeFinished

        if(candidates.size == violations.size){
          // All entries are violations so we have to update because gold will fall out otherwise
          val selectedViolations =
            if(violations.isEmpty) Nil
            else if(updateMethod == AllUpdate  ) violations
            else if(updateMethod == LaSOUpdate ) violations.minBy(_.score)::Nil
            else if(updateMethod == EarlyUpdate) violations.maxBy(_.score)::Nil
            else ???
          for(violation <- selectedViolations){
            loss += getSubLoss(currGoldState, violation, distanceMetric)
            subLossCount += 1
          }
          beam = List(currGoldState)
        }else{
          // Not all entries are violations so we don't update yet
          beam = fringeUnfinished
        }
      }
      prevGoldState = currGoldState
    }
    if(subLossCount > 0)
      loss /= subLossCount
    loss
  }

  private def getSubLoss(gold           : PredictionState,
                         pred           : PredictionState,
                         distanceMetric : (PredictionState, PredictionState) => Float,
                        ) : Expression = {
    val distance = distanceMetric(pred, gold)
    if(distance<0){
      System.err.println(s"warning: distance is negative $distance")
      scalar(0f)
    }else{
      distance * ( pred.scoreExp+1-gold.scoreExp )
    }
  }

  @inline private
  def transitionOptions(beam:List[PredictionState]) : List[(PredictionState, Float, Int)] =
    for{
      state      <- beam
      (score, a) <- state.nextActionLogProbsTotalValues.zipWithIndex
    }
      yield (state, score, a)

  private
  def bestK(k : Int, beam : List[PredictionState]) : List[PredictionState] =
    transitionOptions(beam).
      sortBy(-_._2).
      take(k).
      map{ case (state, _, a) => state applyAction a }


}
