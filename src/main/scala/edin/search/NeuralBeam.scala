package edin.search

trait NeuralBeam {

  def search(initState : PredictionState) : List[PredictionState] = search(List(initState))

  def search(initStates : List[PredictionState]) : List[PredictionState]

  def searchConstrained(initStates : List[PredictionState], constraints : List[Int]) : List[PredictionState] = {
    throw new NotImplementedError()
  }

}

object NeuralBeamFactory{

  def construct(
               beamSize               : Int    ,
               maxExpansion           : Int    ,
               stopWhenBestKfinished  : Int    ,
               earlyStop              : Boolean,

               subBeamSize            : Int    , // if subBeamSize is 0 then standard beam search is used
               subMaxExpansion        : Int    ,
               subApproximateInside   : Boolean
               ) : NeuralBeam = {
    if(subBeamSize > 0){
      new NeuralBeamLatent(
        beamSize               = beamSize,
        maxExpansion           = maxExpansion,
        stopWhenBestKfinished = stopWhenBestKfinished,

        subBeamSize            = subBeamSize,
        subMaxExpansion        = subMaxExpansion,
        subApproximateInside   = subApproximateInside,

        earlyStop              = earlyStop
      )
    }else{
      new NeuralBeamStandard(
        beamSize               = beamSize,
        maxExpansion           = maxExpansion,
        stopWhenBestKfinished = stopWhenBestKfinished,

        earlyStop              = earlyStop
      )
    }
  }

}



