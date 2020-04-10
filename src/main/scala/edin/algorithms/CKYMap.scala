package edin.algorithms

import scala.collection.mutable.{Map => MutMap}

trait CKYItem {
  val start : Int
  val end : Int
}

/**
  * Effieicent lookup map where keys are tuples with (spanStart, spanEnd, nonTerminal)
  * useful for implementing different structures needed for (projective) CKY parsing
  * and more efficient then using a generic Map with the same keys
  */
class CKYMap[Item <: CKYItem, V](val n:Int) extends MutMap[Item, V] {

  private val chart : Array[Array[MutMap[Item, V]]] = new Array(n+1)
  for(i <- 0 until n+1){
    chart(i) = new Array[MutMap[Item, V]](n+1)
  }

  override def +=(kv: (Item, V)): CKYMap.this.type = {
    var span = chart(kv._1.start)(kv._1.end)
    if(span == null){
      span = MutMap()
      chart(kv._1.start)(kv._1.end) = span
    }
    span(kv._1) = kv._2
    this
  }

  override def -=(key: Item): CKYMap.this.type = {
    val span = chart(key.start)(key.end)
    if(span == null){
      this
    }else{
      span.remove(key)
      this
    }
  }

  override def get(key: Item): Option[V] = {
    val span = chart(key.start)(key.end)
    if(span == null){
      None
    }else{
      span.get(key)
    }
  }

  def itemsStartingAt(start:Int) : Seq[(Item, V)] =
    for {
      j <- start + 1 to n
      if chart(start)(j) != null
      (item, v) <- chart(start)(j)
    }
      yield (item, v)

  def itemsEndingAt(end:Int) : Seq[(Item, V)] =
    for {
      i <- 0 to end
      if chart(i)(end) != null
      (item, v) <- chart(i)(end)
    }
      yield (item, v)

  def itemsInSpan(start:Int, end:Int) : Seq[(Item, V)] =
    if(chart(start)(end) == null)
      List()
    else
      chart(start)(end).toSeq

  override def iterator: Iterator[(Item, V)] =
    {
      for {
        i <- 0 to n
        j <- 0 to n
        if chart(i)(j) != null
        (item, v) <- chart(i)(j)
      }
        yield (item, v)
    }.iterator

}

