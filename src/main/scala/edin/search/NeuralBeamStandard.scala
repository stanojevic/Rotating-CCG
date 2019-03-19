package edin.search

import edin.nn.DyFunctions.argmaxWithScores

class NeuralBeamStandard(
                          beamSize               : Int    ,
                          maxExpansion           : Int    ,
                          stopWhenBestKfinished  : Int    ,
                          earlyStop              : Boolean
                        ) extends NeuralBeam{

  def search(initStates : List[PredictionState]) : List[PredictionState] =
    new GeneralBeam[HypMain](beamSize, maxExpansion, stopWhenBestKfinished = stopWhenBestKfinished, earlyStop = earlyStop)
      .searchAllHyps(initStates.map{new HypMain(_)}).map{_.state}

  private class HypMain(val state:PredictionState) extends Hypothesis {

    override val isFinished: Boolean = state.isFinished

    override val score: Float = state.score

    override def topActions(k: Int): List[(Int, Float)] =
      argmaxWithScores(state.nextActionLogProbsTotalValues, k)

    override def applyAction(a: Int): Hypothesis =
      new HypMain(state.applyAction(a))

  }

}
