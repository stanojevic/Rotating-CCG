package edin.algorithms.evaluation

class FScoreAggregator[T](val name:String, mapper:T => List[AnyRef]) extends ScoreAggregator[T] {

  private val fScoreState = new FScore[AnyRef]

  override val mainScoreName: String = "f"

  override def addToCounts(sys: T, ref:T): Unit = fScoreState.addToCounts(mapper(sys), mapper(ref))

  // override def reportString: String = name+"\n"+FScore.prfToString(fScoreState.prf)

  override def exposedScores: List[(String, Double)] = List(
    ("p", fScoreState.p),
    ("r", fScoreState.r),
    ("f", fScoreState.f),
    ("e", fScoreState.e),
  )


}
