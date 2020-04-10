package edin.ccg.parsing

import edin.search.{NeuralBeam, PredictionState}

import scala.annotation.tailrec

/**
  * fast tracking is not needed because expensive operations are factored out
  * @param kOutTagging what comes out after tagging
  * @param kOutWord    what comes out after word emission
  * @param kMidParsing what is used while normal parsing
  * @param kOutParsing what comes out after parsing
  */
class WordSynchronisedSearch(kMidParsing:Int, kOutParsing:Int, kOutTagging:Int, kOutWord:Int) extends NeuralBeam {
  require(kMidParsing>0)
  require(kOutParsing>0)
  require(kOutTagging>0)
  require(kOutWord>0)

  @tailrec override final
  def search(initStates: List[PredictionState]): List[PredictionState] =
    if(initStates.head.isFinished)
      initStates
    else
      search(processSingleWord(initStates))

  private
  def processSingleWord(wordBeam:List[PredictionState]) : List[PredictionState] = {
    val taggingBeam = bestK(kOutTagging, wordBeam)
    val parsingBeam = bestK(kOutWord, taggingBeam)
    recursiveBeam(parsingBeam, Nil)
  }

  @tailrec private
  def recursiveBeam(unfinished:List[PredictionState], finished:List[PredictionState]) : List[PredictionState] =
    if(finished.size >= kMidParsing || unfinished.isEmpty){
      finished.sortBy(-_.score).take(kOutParsing)
    }else{
      val (fringeFinished, fringeUnfinished) = bestK(kMidParsing, unfinished).partition(isWordFinished)
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

  @inline private
  def isWordFinished(x:PredictionState) : Boolean = ! x.unwrapState[ParserState].conf.state.isParsing

}

