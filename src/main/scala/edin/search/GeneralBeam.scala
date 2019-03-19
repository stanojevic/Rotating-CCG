package edin.search

import edin.algorithms.BestKElements._

class GeneralBeam[H <: Hypothesis](
                                    beamSize               : Int    ,
                                    maxExpansion           : Int    ,
                                    stopWhenBestKfinished  : Int =0 ,
                                    stopWhenKfinished      : Int =0 ,
                                    earlyStop              : Boolean
                                  ) {
  assert((stopWhenBestKfinished>0) != (stopWhenKfinished>0))

  def searchAllHyps(initHypos:List[H]) : List[H] = {
    searchBeamState(initHypos).hyps.asInstanceOf[List[H]]
  }

  def searchFinishedHyps(initHypos:List[H]) : List[H] = {
    searchAllHyps(initHypos).filter{_.isFinished}
  }

  private def searchBeamState(initHypos:List[H]) : GeneralBeamState = {
    var currState = new GeneralBeamState(initHypos, 0, beamSize)
    var break = false
    while(
      ! (stopWhenBestKfinished>0 && currState.hyps.sortBy(-_.score).take(stopWhenBestKfinished).forall(_.isFinished)) &&
      ! (stopWhenKfinished>0 && currState.finishedHyps.size >= stopWhenKfinished) &&
      currState.unfinishedHyps.nonEmpty                          &&
      !currState.allHypoIsFinished                               &&
      (currState.expansion <= maxExpansion)                      &&
      !break
    ){
      val nextState = currState.nextState
      if(earlyStop){
        if(nextState.hyps.exists(_.isGoldConsistent)){
          currState = nextState
        }else{
          break = true
        }
      }else{
        currState = nextState
      }
    }
    currState
  }

  private class GeneralBeamState(
                                  val hyps:List[H],
                                  val expansion:Int,
                                  beamSize:Int
                                ){

    val (finishedHyps, unfinishedHyps) = hyps.partition(_.isFinished)
    private val bestHypo   = hyps.maxBy(_.score)

    val bestHypoIsFinished = bestHypo.isFinished
    val allHypoIsFinished  = hyps.forall(_.isFinished)

    lazy val nextState : GeneralBeamState = {
      val newK = unfinishedHyps.flatMap{ hyp =>
                   hyp.topActions(beamSize).map{case (action, score) =>
                     (hyp, action, score)
                   }
                 }
                 .sortBy(-_._3)
                 .take(beamSize)
                 .map{ case (hyp, action, score) =>
                   hyp.applyAction(action).asInstanceOf[H]
                 }
      val selected = (newK ++ finishedHyps).bestKBy(beamSize)(_.score)
      new GeneralBeamState(selected, expansion+1, beamSize)
    }

  }

}

trait Hypothesis{

  val isFinished : Boolean
  val score      : Float

  def topActions(k:Int)  : List[(Int, Float)]
  def applyAction(a:Int) : Hypothesis

  val isGoldConsistent     : Boolean = false // for implementing EarlyStopping ala Collins

}


