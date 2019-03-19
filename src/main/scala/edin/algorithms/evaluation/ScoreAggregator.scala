package edin.algorithms.evaluation

trait ScoreAggregator[T]{

  val name:String

  val mainScoreName:String

  def mainScore:Double = exposedScores.find(_._1==mainScoreName).get._2

  def addToCounts(sys:T, ref:T) : Unit

  def exposedScores : List[(String, Double)]

  def reportString : String =
    s"$name\n"+exposedScores.map{
      case (n,v:Double)=>f"$n: $v%.3f"
    }.mkString("\t")

}
