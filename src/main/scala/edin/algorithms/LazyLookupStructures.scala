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

class LazyMap[K, V] private(mapp:Map[K, LazyBox[V]]){

  /** this slightly strange syntax for + is necessary because -> doesn't accept lazy evaluation */
  def + (k:K, v: =>V) : LazyMap[K, V] =
    new LazyMap(mapp + (k -> new LazyBox(v)))

  def apply(k:K) : V =
    mapp(k).value

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

