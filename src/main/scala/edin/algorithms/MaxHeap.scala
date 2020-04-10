package edin.algorithms

import java.lang.{Double => DoubleBox}

import org.jheaps.AddressableHeap
import org.jheaps.AddressableHeap.Handle
import org.jheaps.array.{BinaryArrayAddressableHeap, DaryArrayAddressableHeap}
import org.jheaps.tree.{FibonacciHeap, LeftistHeap, PairingHeap}

import scala.collection.JavaConverters._
import scala.collection.mutable.{Map => MutMap}
import scala.reflect.ClassTag

class MaxHeap[E <: AnyRef](efficientHandleMap:MutMap[E, AnyRef]=null, heapType:String="Fibonacci")(implicit eClassTag: ClassTag[E]) {

  private val handleMap: Option[MutMap[E, Handle[DoubleBox, E]]] = Option(efficientHandleMap) match {
    case Some(m) => Some(m.asInstanceOf[MutMap[E, Handle[DoubleBox, E]]])
    case None => None // MutMap[E, Handle[DoubleBox, E]]()
  }

  private var heap:AddressableHeap[DoubleBox, E] = heapType match {
    case "Fibonacci"                    => new              FibonacciHeap()
    case "Binary"   | "BinaryNoHeapify" => new BinaryArrayAddressableHeap()
    case "4ary"     | "4aryNoHeapify"   => new   DaryArrayAddressableHeap(4)
    case "8ary"     | "8aryNoHeapify"   => new   DaryArrayAddressableHeap(8)
    case "Pairing"                      => new                PairingHeap()
    case "Leftist"                      => new                LeftistHeap()
    case _                              => throw new Exception(s"heap $heapType unsupported")
  }

  def size : Long = heap.size()

  def isEmpty : Boolean = heap.isEmpty

  def nonEmpty : Boolean = ! isEmpty

  // requires handleMap
  def contains(el:E) : Boolean = handleMap.get.contains(el)

  // requires handleMap
  def getSameElement(el:E) : Option[E] = handleMap.get.get(el).map(_.getValue)

  // requires handleMap
  def remove(el:E) : Unit = handleMap.get.remove(el).get.delete()

  // requires handleMap
  def insertSmart(el:E, priority:Double) : Unit =
    handleMap.get.get(el) match {
      case Some(handle) =>
        if(priorityTransform(priority) > priorityTransform(handle.getKey)){
          // delete+insert is better then decreaseKey because not only score but also backpointer might change
          // it's two timpes slower than decreaseKey but safer from potential future bugs
          handle.delete()
          insert(el, priority)
        }
      case None =>
        insert(el, priority)
    }

  // requires handleMap
  def increaseKey(el:E, newPriority:Double) : Unit =
    handleMap.foreach(_(el).decreaseKey(priorityTransform(newPriority)))

  @inline
  private def priorityTransform(priority:Double) : Double = -priority

  @inline
  private def toBoxedArray : List[Double] => Array[DoubleBox] = _.map(Double.box).toArray

  def insertChunk(els:List[E], priorities:List[Double]) : Unit = (size, heapType) match {
    case (0, "Binary") =>
      val h = BinaryArrayAddressableHeap.heapify(toBoxedArray(priorities map priorityTransform), els.toArray)
      heap = h
      // requires handleMap
      if(handleMap.nonEmpty)
        for(handler <- h.handlesIterator().asScala)
          handleMap.foreach(_(handler.getValue) = handler)
    case (0, "4ary") =>
      val h = DaryArrayAddressableHeap.heapify(4, toBoxedArray(priorities map priorityTransform), els.toArray)
      heap = h
      // requires handleMap
      if(handleMap.nonEmpty)
        for(handler <- h.handlesIterator().asScala)
          handleMap.foreach(_(handler.getValue) = handler)
    case (0, "8ary") =>
      val h = DaryArrayAddressableHeap.heapify(8, toBoxedArray(priorities map priorityTransform), els.toArray)
      heap = h
      // requires handleMap
      if(handleMap.nonEmpty)
        for(handler <- h.handlesIterator().asScala)
          handleMap.foreach(_(handler.getValue) = handler)
    case (_, _) =>
      for((el, priority) <- els zip priorities)
        insert(el, priority)
  }

  @inline
  def insert(el:E, priority:Double) : Unit = {
    val handle = heap.insert(priorityTransform(priority), el)
    // requires handleMap
    handleMap.foreach(_(el) = handle)
  }

  def extractMaxElement() : E = extractMax()._1

  def extractMax() : (E, Double) = {
    val handle = heap.deleteMin()
    val el = handle.getValue
    val priority = priorityTransform(handle.getKey)
    handleMap.foreach(_.remove(el))
    (el, priority)
  }

}

object MaxHeap{

  def main(args:Array[String]) : Unit = {

//    val myHeap = new MaxHeap[String](efficientHandleMap = MutMap[String, AnyRef]())
//    myHeap.insertOrIncreaseKey("value10", 1.0)
//    myHeap.insertOrIncreaseKey("value2", 2.0)
//    myHeap.insertOrIncreaseKey("value10", 10.0)
//    myHeap.remove("value10")
//
//    val (el, priority) = myHeap.extractMax()
//
//    println((el, priority))

    class MyItem[T](startI:Int, endI:Int, el:T) extends CKYItem {
      override val start: Int = startI
      override val end: Int = endI
    }

    val myMap = new CKYMap[MyItem[String], AnyRef](10)
    val myHeap = new MaxHeap[MyItem[String]](efficientHandleMap = myMap, heapType = "BinaryArray")
    myHeap.insertSmart(new MyItem(0, 2, "value10"), 1.0)
    myHeap.insertSmart(new MyItem(0, 4, "value2"), 2.0)
    myHeap.insertSmart(new MyItem(0, 2, "value10"), 10.0)
    // myHeap.remove((0, 2, "value10"))
    for((item, cont) <- myMap){
      print("element   ")
      println((item, cont))
    }

    val (el, priority) = myHeap.extractMax()

    println((el, priority))
  }
}
