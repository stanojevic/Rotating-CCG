package edin.algorithms.evaluation

import edin.algorithms.Math.{perplexity, logSumExp}

class LogProbAggregator(name:String="logProb") {

  var logProb:Double = 0

  def realProb: Double = math.exp(logProb)

  def perpl : Double = perplexity(logProb)
  def perplPerWord : Double = if(words > 0) perpl/words else Double.PositiveInfinity

  def exposedScores : List[(String, Double)] = List(
    ("logProb", logProb),
    ("perplTotal", perpl),
    ("perplPerWord", perplPerWord)
  )

  var words = 0

  def addLogProb(l:Double, ws:Int ) : Unit = {
    words += ws
    addLogProb(l)
  }

  def addLogProb( l:Double) : Unit = logProb+=l

  def reportString : String = f"$name\nlogProb: $logProb%.3f\tprob: $realProb%.3f"

  def reportStringWithPerpl : String =
    if(words > 0)
      reportString+f"\tperplTotal: $perpl%.3f\tperplPerWord: $perplPerWord%.3f"
    else
      reportString+f"\tperplTotal: $perpl%.3f"
}

