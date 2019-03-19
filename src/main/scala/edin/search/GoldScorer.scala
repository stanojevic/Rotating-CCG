package edin.search

object GoldScorer {

  def score(initState:Hypothesis, actions:List[Int]) : Float = {
    var state = initState
    var as = actions
    while(as.nonEmpty){
      state = state.applyAction(as.head)
      as = as.tail
    }
    state.score
  }

  def score(initState:PredictionState, actions:List[Int]) : Float = {
    var state = initState
    var as = actions
    while(as.nonEmpty){
      state = state.applyAction(as.head)
      as = as.tail
    }
    state.score
  }

}
