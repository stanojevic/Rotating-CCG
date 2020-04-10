package edin.general

import java.io.PrintWriter

import scala.collection.mutable.{HashMap => MutMap}
import edin.algorithms.AutomaticResourceClosing.linesFromFile

abstract class Any2Int[T](
                           minCount       : Int            = 0,
                           maxVacabulary  : Int            = Int.MaxValue,
                           withUNK        : Boolean        = true,
                           withEOS        : Boolean        = true,
             protected val mapping        : MutMap[T, Int] = MutMap[T, Int](),
                       var locked         : Boolean        = false
                         ) extends Serializable {

  def frequency(x:T) : Int = frequencyStore.getOrElse(transformStr(x), 0)

  def frequencyIntMap : Map[Int, Float] = frequencyStore.toList.map{case (k, c) => (apply(k), c.toFloat)}.toMap

  private val frequencyStore: MutMap[T, Int] = MutMap()

  private val reverseMapping = MutMap[Int, T]()
  refreshReverseMapping()
  private def refreshReverseMapping():Unit = {
    for((w, c) <- mapping){
      reverseMapping(c) = w
    }
  }

  def contains(x:T) : Boolean = {
    assert(locked)
//    transformStr(x) match {
//      case `UNK_str` if withUNK => true
//      case `EOS_str` if withEOS => true
//      case xt => mapping contains xt
//    }
    val xt = transformStr(x)
    if(withUNK && xt == UNK_str){
      true
    }else if(withEOS && xt == EOS_str){
      true
    }else{
      mapping contains xt
    }
  }

  def lock() : Unit = {
    assert(! locked)
    buildMapping()
    assert(locked)
  }

  def all_non_UNK_values : List[T] = {
    assert(locked)
    var i1 = mapping.toList.
            sortBy(_._2).
            map(_._1)
    if(withEOS)
      i1 = i1.filterNot{ _ == EOS_str }
    if(withUNK)
      i1 = i1.filterNot{ _==UNK_str }
    i1
  }

  // for potential lowercasing if subclass wants to do that
  protected def transformStr(x:T): T = x

  val UNK_str:T
  val EOS_str:T
  lazy val UNK_i:Int = mapping(UNK_str)
  lazy val EOS_i:Int = mapping(EOS_str)

  def size : Int = {
    if(! locked)
      buildMapping()
    mapping.size
  }

  def apply(originalWord:T) : Int = {
    val word = transformStr(originalWord)
    if(! locked)
      buildMapping()
    mapping.getOrElse(word, UNK_i)
  }
  def apply(i:Int) : T = {
    if(! locked)
      buildMapping()
    reverseMapping.getOrElse(i, UNK_str)
  }

  def addToCounts(originalWord:T) : Unit = {
    assert(!locked)

    val word = transformStr(originalWord)
    assert(withUNK || word!=UNK_str)
    assert(withEOS || word!=EOS_str)
    frequencyStore(word) = frequencyStore.getOrElse(word, 0) + 1
  }

  private def buildMapping() : Unit = {
    assert(!withUNK || UNK_str!=null)
    assert(!withEOS || EOS_str!=null)
    assert(!locked)

    val eosCount_new = if(withEOS){
      val c = frequencyStore.getOrElse(EOS_str, 0)
      frequencyStore.remove(EOS_str)
      c
    }else{
      0
    }

    var unkCount_new:Int = if(withUNK){
      val c = frequencyStore.getOrElse(UNK_str, 0)
      frequencyStore.remove(UNK_str)
      c
    }else{
      0
    }

    val belowMinKeys = frequencyStore.keys.filter(frequencyStore(_)<minCount)
    val notTopKeys   = frequencyStore.toList.sortBy(-_._2).drop(maxVacabulary-2).map(_._1)
    for(k <- belowMinKeys ++ notTopKeys){
      if(frequencyStore.contains(k)){
        unkCount_new += frequencyStore(k)
        frequencyStore.remove(k)
      }
    }

    if(withUNK)
      storeMapping(UNK_str)

    if(withEOS)
      storeMapping(EOS_str)

    for(el <- frequencyStore.keys)
      storeMapping(el)

    if(withUNK){
      frequencyStore(UNK_str) = unkCount_new
    }
    if(withEOS){
      frequencyStore(EOS_str) = eosCount_new
    }

    assert(mapping.size <= maxVacabulary)
    refreshReverseMapping()
    locked = true
  }

  private var next_int:Int = 0
  private def storeMapping(word:T) : Unit = {
    mapping(word) = next_int
    next_int += 1
  }

}

class DefaultAny2Int[T >:Null <:AnyRef](
                    minCount      :Int = 0,
                    maxVocabulary :Int = Int.MaxValue,
                    withUNK : Boolean = false,
                    UNK : T = null
                  ) extends Any2Int[T](
                                     minCount       = minCount,
                                     maxVacabulary  = maxVocabulary,
                                     withUNK        = withUNK,
                                     withEOS        = false
                                   ){
  override val UNK_str: T = UNK
  override val EOS_str: T = null
}

class String2Int(
                  minCount:Int      = 0,
                  maxVacabulary:Int = Int.MaxValue,
                  withUNK:Boolean   = true,
                  withEOS:Boolean   = true,
                  lowercased:Boolean = false,
                  mapping : MutMap[String, Int] = MutMap[String, Int](),
                  locked : Boolean        = false
                ) extends Any2Int[String](
                                         minCount = minCount,
                                         maxVacabulary = maxVacabulary,
                                         withUNK   = withUNK,
                                         withEOS   = withEOS,
                                         mapping = mapping,
                                         locked = locked
                                         ) {
  val UNK_str: String = String2Int.UNK_str
  val EOS_str: String = String2Int.EOS_str

  override def transformStr(x: String): String = {
    if(x == EOS_str || x== UNK_str || !lowercased)
      x
    else
      x.toLowerCase()
  }

  def saveToText(fn:String) : Unit = {
    val pw = new PrintWriter(fn)
    if(lowercased){
      pw.println("LOWERCASED")
    }
    for((k, v) <- this.mapping.toList.sortBy(_._2)){
      pw.println(s"$v $k")
    }
    pw.close()
  }

}

object String2Int{
  val UNK_str = "<UNK>"
  val BOS_str = "<BOS>"
  val EOS_str = "<EOS>"

  def loadFromText(fn:String) : String2Int = {
    var lowercased = false
    val mapping = MutMap[String, Int]()
    for((line, line_i) <- linesFromFile(fn).zipWithIndex){
      if(line_i == 0 && line == "LOWERCASED"){
        lowercased = true
      }else{
        val fields = line.split(" ")
        val v = fields(0).toInt
        val k = fields(1)
        mapping(k)=v
      }
    }
    new String2Int(
      lowercased = lowercased,
      mapping = mapping,
      locked  = true
    )
  }

}

