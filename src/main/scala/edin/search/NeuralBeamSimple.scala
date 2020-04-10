package edin.search

import scala.annotation.tailrec

class NeuralBeamSimple(k:Int) extends NeuralBeam {

  override def search(initStates: List[PredictionState]): List[PredictionState] = recursiveBeam(initStates, Nil)

  @tailrec private
  def recursiveBeam(unfinished:List[PredictionState], finished:List[PredictionState]) : List[PredictionState] =
    if(finished.size >= k || unfinished.isEmpty){
      finished.sortBy(-_.score).take(k)
    }else{
      val (fringeFinished, fringeUnfinished) = bestK(k, unfinished).partition(_.isFinished)
      recursiveBeam(fringeUnfinished, fringeFinished ++ finished)
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
