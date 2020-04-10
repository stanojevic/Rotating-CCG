package edin.search

import edin.nn.DyFunctions._

class NeuralBeamLatent(
                        beamSize               : Int,
                        maxExpansion           : Int,
                        stopWhenBestKfinished  : Int,

                        subBeamSize            : Int,
                        subMaxExpansion        : Int,
                        subApproximateInside   : Boolean,

                        earlyStop              : Boolean
                      ) extends NeuralBeam{

  def search(initStates : List[PredictionState]) : List[PredictionState] =
    searchAllLatent(initStates).map{_.maxBy(_.score)}

  def searchAllLatent(initStates : List[PredictionState]) : List[List[PredictionState]] = {
    new GeneralBeam[HypMain](
      beamSize               = beamSize,
      maxExpansion           = maxExpansion,
      stopWhenBestKfinished  = stopWhenBestKfinished,
      earlyStop              = earlyStop
    ).searchAllHyps(List(new HypMain(initStates))).map{_.packedStates}
  }

  private class HypMain(
                        val packedStates : List[PredictionState]  // all PredictionStates agree on the prefix
                       ) extends Hypothesis {

    lazy val bestState = packedStates.maxBy {
      _.score
    }

    override val score: Float = if (subApproximateInside) {
      packedStates.map {
        _.score
      }.sum
    } else {
      packedStates.map {
        _.score
      }.max
    }

    override val isFinished: Boolean = packedStates.head.isFinished

    private val nextEmittingStates = new GeneralBeam[HypSub](
      beamSize = subBeamSize,
      maxExpansion = subMaxExpansion,
      stopWhenBestKfinished = subBeamSize, // all latent should be expanded
      earlyStop = earlyStop
    ).searchFinishedHyps(packedStates.map(
      new HypSub(_, false)
    )).map (
      _.packedState
    )


    override def topActions(k: Int): List[(Int, Float)] = {
      val semiring = if (subApproximateInside) logSumExp(_) else max(_)
      argmaxWithScores(
        semiring(nextEmittingStates.map { _.nextActionLogProbsTotalExp }),
        k
      )
    }

    override def applyAction(a: Int): Hypothesis = {
      new HypMain(
        packedStates.map{_.applyAction(a)}
      )
    }
  }

  private class HypSub(
                         val packedState   : PredictionState,
                         val isReadyToEmit : Boolean
                       ) extends Hypothesis {
    override val isFinished: Boolean = ! isReadyToEmit
    override val score: Float = packedState.score

    override def topActions(k: Int): List[(Int, Float)] =
      argmaxWithScores(packedState.nextActionLogProbsTotalValues, k)

    override def applyAction(a: Int): Hypothesis = {
      val newState = packedState.applyAction(a)
      new HypSub(newState, ! newState.isReadyToEmit)
    }
  }

}
