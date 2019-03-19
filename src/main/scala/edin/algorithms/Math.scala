package edin.algorithms

import math.{max, min, log, log1p, exp, pow}

object Math {

  def logSumExp(a:Double, b:Double) : Double =
    if(a.isNegInfinity){
      b
    }else if(b.isNegInfinity){
      a
    }else{
      val x = max(a, b)
      val y = min(a, b)
      x + log1p(exp(y-x))
    }

  def logSumExp(logProbs:Seq[Double]) : Double =
    if (logProbs.tail.isEmpty)
      logProbs.head
    else
      logProbs.reduce(logSumExp)

  def crossEntropy(logProb:Double) : Double =
    -logProb/log(2)

  def perplexity(logProb:Double) : Double =
    pow(2, crossEntropy(logProb))

  def sigmoid(x:Double) : Double =
    if(x >= 0){
      val z = exp(-x)
      1/(1+z)
    }else{
      val z = exp(x)
      z/(1+z)
    }

  // this is NOT log_softmax
  def softmax(xs:List[Double]) : List[Double] = {
    val b = xs.max
    val ys = xs.map(x => exp(x-b))
    val s = ys.sum
    ys.map(y => y/s)
  }


}
