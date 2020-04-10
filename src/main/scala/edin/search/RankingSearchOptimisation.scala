package edin.search

import edu.cmu.dynet.Expression
import edin.nn.DyFunctions._

object RankingSearchOptimisation {

  type Predicted      = PredictionState
  type Gold           = PredictionState
  type DistanceMetric = (Predicted, Gold) => Float

  def loss(
            goldStates     : List[PredictionState],
            beamSize       : Int,
            distanceMetric : DistanceMetric
          ) : Expression = {
    val golds = goldStates.tail
    val beams = allBeams(beamSize, goldStates.head::Nil).take(golds.size).toList
    val loss: Expression = (golds zip beams).flatMap{
      case (gold, beam) => lossPerBeam(gold, beam, distanceMetric)
    }.eavgOrElse(0)
    loss
  }

  private
  def lossPerBeam(gold:PredictionState, beam:List[PredictionState], distanceMetric: DistanceMetric) : List[Expression] =
    goldLoss(gold, beam)++rankingLoss(gold, beam, distanceMetric)

  private
  def rankingLoss(gold:PredictionState, beam:List[PredictionState], distance: DistanceMetric) : List[Expression] =
    allUniquePairs(beam).flatMap{ case (pred1, pred2) =>
      val (better, worse) = if(distance(pred1, gold) < distance(pred2, gold)) (pred1, pred2) else (pred2, pred1)
      if(worse.score+1-better.score>0){
        Some(worse.scoreExp+1-better.scoreExp)
      }else{
        None
      }
    }

  private
  def goldLoss(gold:PredictionState, beam:List[PredictionState]) : List[Expression] =
    beam.flatMap{ pred =>
      if(pred != gold && pred.score+1-gold.score>0){
        Some(pred.scoreExp+1-gold.scoreExp)
      }else{
        None
      }
    }

  private
  def allUniquePairs[A] : List[A] => List[(A, A)] = {
    case Nil => Nil
    case x::xs => xs.map((x, _)) ++ allUniquePairs(xs)
  }

  private
  def allBeams(k : Int, currBeam: List[PredictionState]) : Stream[List[PredictionState]] = {
    val newBeam = bestK(k, currBeam)
    val unfinished = newBeam.filterNot(_.isFinished)
    newBeam #:: allBeams(k, unfinished)
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
