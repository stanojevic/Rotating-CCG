package edin.algorithms.evaluation

object FScore {

  private def safeRatio(matched:Double, total:Double) : Double = {
    assert(matched <= total)
    assert(matched >= 0)
    if(total == 0)
      1.0
    else
      matched/total
  }

  def f_score(p:Double, r:Double, beta:Double=1) : Double =
    if(p==0 && r==0)
      0
    else
      (1+beta*beta)*p*r/(beta*beta*p+r)

  private def computeScores[K, T](sys:List[K], ref:List[K])(f:K=>List[T]) : (Double, Double, Double) =
    computeScores(sys.map(f), ref.map(f))

  private def computeScores[T](sysProps:List[List[T]], refProps:List[List[T]]) : (Double, Double, Double) = {
    val scorer = new FScore[T]
    for((sys, ref) <- sysProps zip refProps){
      scorer.addToCounts(sys, ref)
    }
    scorer.prf
  }

  def prfToString(prf:(Double, Double, Double)) : String =
    f"f: ${prf._3}%.3f\tp: ${prf._1}%.3f\tr: ${prf._2}%.3f"

}

class FScore[T]{

  import FScore._

  private var overlapCount  = 0
  private var totalSys      = 0
  private var totalRef      = 0
  private var exactMatch    = 0
  private var examplesTotal = 0

  private var locked = false

  def addToCounts(sys:List[T], ref:List[T]) : Unit = {
    if(locked)
      throw new Exception("can't count with finished FScore")
    overlapCount += (sys intersect ref).size
    totalSys += sys.size
    totalRef += ref.size
    if(sys.groupBy(identity).mapValues(_.size) == ref.groupBy(identity).mapValues(_.size))
      exactMatch += 1
    examplesTotal += 1
  }

  def p : Double = {
    locked = true
    safeRatio(overlapCount, totalSys)
  }

  def r : Double = {
    locked = true
    safeRatio(overlapCount, totalRef)
  }

  def f : Double = {
    locked = true
    f_score(p, r)
  }

  def e : Double = {
    locked = true
    exactMatch.toDouble/examplesTotal
  }

  private def prf : (Double, Double, Double) = (p, r, f)

}

