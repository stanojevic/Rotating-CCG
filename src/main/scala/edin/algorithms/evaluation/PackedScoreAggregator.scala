package edin.algorithms.evaluation

class PackedScoreAggregator[T](mainAggName:String, aggregators:List[ScoreAggregator[T]]) extends ScoreAggregator[T] {

  private val mainAgg = {
    val aggregators = this.aggregators
    val mainAggName = this.mainAggName
    aggregators.find(_.name == mainAggName).get
  }

  override val mainScoreName: String = mainAggName+"_"+mainAgg.mainScoreName

  override def addToCounts(sys: T, ref:T): Unit = aggregators.foreach(ag => ag.addToCounts(sys, ref))

  override def reportString: String = aggregators.map(ag => ag.reportString).mkString("\n\n")

  override val name: String = "Packed"

  override def exposedScores: List[(String, Double)] =
    // aggregators.flatMap(ag => ag.exposedScores.map{case (n, v)=>(ag.name+"_"+n, v)})
    for{
      ag <- aggregators
      (n, v) <- ag.exposedScores
    }
      yield (ag.name+"_"+n, v)

}
