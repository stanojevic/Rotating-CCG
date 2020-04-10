package edin.algorithms

/**
  * Welford's Online algorithm for computing mean and standard deviation
  * warning: in some descriptions of the algorithm what I call M is called S
  */

class MeanStdIncrementalState private (val mean:Double, M:Double, n:Int) {

  lazy val variance: Double = M/n

  lazy val stdDev  : Double = math.sqrt(variance)

  def addNewPoint(x:Double) : MeanStdIncrementalState = {
    val newN = n + 1
    val newMean = mean + (x - mean)/newN
    val newM = M + (x - mean)*(x - newMean)
    // val newM = M + math.pow(x - mean, 2)*n/(n+1) // this should be equivalent
    new MeanStdIncrementalState(newMean, newM, newN)
  }

}

object MeanStdIncrementalState{

  def apply() : MeanStdIncrementalState = new MeanStdIncrementalState(mean = 0, M = 0, n = 0)

}
