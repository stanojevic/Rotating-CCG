package edin.algorithms

class TimingStatsAggregator(val name:String) {

  private var stats = List[(Int, Double)]()

  private var accumulatedTime : Long = 0
  private var timeStarted : Long = -1
  private var currDifficulty : Int = -1

  def startTiming(difficulty:Int) : Unit = {
    assert(currDifficulty<0)
    currDifficulty = difficulty
    continueTiming()
  }

  def pauseTiming() : Unit = {
    assert(timeStarted>=0)
    accumulatedTime += System.currentTimeMillis() - timeStarted
    timeStarted = -1
  }

  def continueTiming() : Unit = {
    assert(timeStarted<0)
    timeStarted = System.currentTimeMillis()
  }

  def endTiming() : Unit = {
    assert(currDifficulty>=0)
    pauseTiming()
    stats ::= (currDifficulty, accumulatedTime/1000d)
    currDifficulty = -1
  }

  /** in seconds */
  def averageTime : Double = stats.map(_._2).sum/stats.size

  def averageTimePerDifficulty : Double = stats.map(x => x._1*x._2).sum/stats.map(_._1).sum

  def allStats : List[(Int, Double)] = stats

  def printInfo() : Unit = System.err.println("average time of "+name+" "+averageTime)

  def printCsvInfo() : Unit = System.err.println(allStats.groupBy(_._1).mapValues(x => x.map(_._2).sum/x.size).toList.sorted.map(x => s"$name\t${x._1}\t${x._2}").mkString("\n"))

}
