package edin.algorithms

import scala.collection.mutable.{PriorityQueue => MutPriorityQueue}

object BestKElements {

  // TODO implement usage of quickSelect if the number of required best elements is big K
  // TODO - useful link 1 https://en.wikipedia.org/wiki/Median_of_medians
  // TODO - useful link 2 https://en.wikipedia.org/wiki/Selection_algorithm

  implicit class IterableBestKBy[T](xs:Iterable[T]){

    @inline
    def bestKOrdering(k:Int)(ordering:Ordering[T]) : List[T] = {
      val q = new MutPriorityQueue[T]()( -ordering.compare(_, _) )
      for(x <- xs){
        q.enqueue(x)
        if(q.size > k){
          q.dequeue()
        }
      }
      var res = List[T]()
      while(q.nonEmpty){
        res ::= q.dequeue()
      }
      res
    }

    @inline
    def worstKOrdering(k:Int)(ordering:Ordering[T]) : List[T] = bestKOrdering(k)(ordering.reverse)

    @inline
    def bestKBy[X](k:Int)(key: T => X)(implicit cv: X => Ordered[X]) : List[T] = bestKOrdering(k)(buildComparator(key))

    @inline
    def worstKBy[X](k:Int)(key: T => X)(implicit cv: X => Ordered[X]) : List[T] = worstKOrdering(k)(buildComparator(key))

    @inline
    private def buildComparator[X](key: T => X)(implicit cv: X => Ordered[X]) : Ordering[T] = key(_) compare key(_)

  }

  implicit class IterableBestK[T](xs:Iterable[T])(implicit cv: T => Ordered[T]){
    @inline
    def bestK(k:Int) : List[T] = xs.bestKBy(k)(identity)

    @inline
    def worstK(k:Int) : List[T] = xs.worstKBy(k)(identity)
  }

}

