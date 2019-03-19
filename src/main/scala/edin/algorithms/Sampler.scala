package edin.algorithms

import scala.util.Random

object Sampler {

  // bigger alpha means more peaked
  def rescale[T](support:List[(T, Double)], alpha:Double) : List[(T, Double)] =
    if(alpha == 1.0){
      support
    }else{
      val realSupport = support.filter(_._2>0)
      val min = realSupport.minBy(_._2)._2
      realSupport.map{case (id, score) => (id, math.pow(score/min, alpha))}
    }

  def sample[T](support:List[(T, Double)]) : T = sample(Random, support)
  def sample[T](r:Random, support:List[(T, Double)]) : T = {
    assert(support.nonEmpty)
    val Z = support.map(_._2).sum
    val goalPoint = Z*r.nextDouble()
    var currPoint = 0.0
    var supportLeftover = support
    while(supportLeftover.nonEmpty){
      currPoint += supportLeftover.head._2
      if(currPoint>goalPoint)
        return supportLeftover.head._1
      supportLeftover = supportLeftover.tail
    }
    throw new Exception("something is wrong here")
  }

  /*
  sampling without replacement -- samples cannot repeat
  sampling with    replacement -- samples can    repeat
   */
  def manySamples[T](support:List[(T, Double)], numberOfSamples:Int, withReplacement:Boolean) : List[T] = {
    assert(withReplacement || support.size >= numberOfSamples)
    var samples = List[T]()
    var currSupport = support
    var count = 0
    while(count < numberOfSamples){
      val aSample = sample(currSupport)
      if(! withReplacement)
        currSupport = currSupport.filterNot(_._1== aSample)
      samples ::= aSample
      count += 1
    }
    samples
  }

  def groupSamples[T](samples:List[T]) : List[(T, Int)] = {
    samples.groupBy(identity).mapValues(_.size).toList
  }

  def main(args:Array[String]) : Unit = {
    val support:List[(String, Double)] = List( ("milos", 0.2), ("dusan", 2.0 ), ("goca", 1), ("boza", 1) )

    val single = sample(support)
    println(single)
    val multi1 = manySamples(support, 10, withReplacement = true)
    println(multi1)
    println(groupSamples(multi1))
    val multi2 = manySamples(support, 4, withReplacement = false)
    println(multi2)
    println(groupSamples(multi2))
    println("hello")
  }

}
