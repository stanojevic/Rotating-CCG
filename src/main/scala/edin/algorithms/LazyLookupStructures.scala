package edin.algorithms

/**
  * Contains:
  * LazyLookupMap
  * and
  * LazyLookupVector
  *
  * that don't evaluate their values unless they have to (all values are wrapped in LazyBox)
  * they will evaluate keys though
  */

private class LazyBox[V](f: => V){
  lazy val value:V = f
}

trait KeyValueStore[K, V]{

  val keys: Iterable[K]

  def toList: List[(K, V)] = keys.toList.map(k => (k, apply(k)))

  override def toString: String = "KeyValueStore("+toList.map{case (k, v) => s"$k -> $v"}.mkString(", ")+")"

  def + (k:K, v: =>V) : KeyValueStore[K, V]

  def apply(k:K) : V

  def contains(k:K) : Boolean

  def get(k:K) : Option[V]

  def size: Int

  def map[L](f: V => L): KeyValueStore[K, L] =
    new ValueView(cache = LazyMap(), subStore = this, f = f)

}

class ValueView[K, V, L] private[algorithms](var cache:LazyMap[K, L], subStore:KeyValueStore[K, V], f: V => L) extends KeyValueStore [K, L]{

  lazy val keys = subStore.keys

  override def +(k: K, v: => L): KeyValueStore[K, L] =
    new ValueView(cache + (k, v), subStore, f)

  override def apply(k: K): L = get(k).get

  override def get(k: K): Option[L] = {
    cache.get(k) match {
      case None =>
        subStore.get(k).map(f) match {
          case None =>
            None
          case Some(v) =>
            cache += (k, v)
            Some(v)
        }
      case Some(v) =>
        Some(v)
    }
  }

  override def contains(k: K): Boolean = get(k).nonEmpty

  override lazy val size: Int = subStore.size

}

class LazyMap[K, V] private(mapp:Map[K, LazyBox[V]]) extends KeyValueStore[K, V] {

  lazy val keys = mapp.keys

  /** this slightly strange syntax for + is necessary because -> doesn't accept lazy evaluation */
  def + (k:K, v: =>V) : LazyMap[K, V] =
    new LazyMap(mapp + (k -> new LazyBox(v)))

  def apply(k:K) : V =
    mapp(k).value

  def get(k:K) : Option[V] =
    mapp.get(k).map(_.value)

  def contains(k:K) : Boolean =
    mapp contains k

  def size: Int =
    mapp.size

}

object LazyMap{
  def apply[K, V]() : LazyMap[K, V] = new LazyMap[K, V](Map())
}

class LazyVector[V] private(vector:Vector[LazyBox[V]]){

  def :+(value: =>V) : LazyVector[V] =
    new LazyVector(vector :+ new LazyBox(value))

  def apply(i:Int) : V =
    vector(i).value

  def size: Int=
    vector.size

}

object LazyVector{
  def apply[V]() : LazyVector[V] =
    new LazyVector[V](Vector())
}

object LazyLookupStructurs{
  // this is just for testing
  def main(args:Array[String]) : Unit = {
    var mapp = LazyMap[String, Double]()

    mapp = mapp + ("rain", {
      println("first")
      4.0
    })

    mapp = mapp + ("sisters of mercy", {
      println("second")
      6.0
    })

    println(mapp("sisters of mercy"))
    println(mapp("sisters of mercy"))
    println(mapp("sisters of mercy"))

    println(mapp("rain"))

    println("hello")
  }

}

