package edin.algorithms

class MathArray(val array:Array[Float]) extends Serializable {

  val length : Int = array.length

  @inline
  def apply(pos:Int) : Float = array(pos)

  @inline
  def update(pos:Int, v:Float) : Unit = array(pos) = v

  def +=(other:MathArray) : Unit = {
    val y = other.array
    assert(array.length == y.length)
    var i = 0
    while(i < array.length){
      array(i)+=y(i)
      i += 1
    }
  }

  def /=(y:Float) : Unit = {
    var i = 0
    while(i < array.length){
      array(i)/=y
      i += 1
    }
  }

  def +(other:MathArray) : Array[Float] = {
    val y = other.array
    assert(array.length == y.length)
    val z = new Array[Float](array.length)
    var i = 0
    while(i < array.length){
      z(i) = array(i)+y(i)
      i += 1
    }
    z
  }

  def map(f:Double => Double) : MathArray = {
    val xs = this.copy()
    val xsA = xs.array
    var i = 0
    while(i < xs.length){
      xsA(i)=f(xsA(i).toDouble).toFloat
      i += 1
    }
    xs
  }

  def inplaceMap(f:Float => Float) : Unit = {
    var i = 0
    while(i < array.length){
      array(i)=f(array(i))
      i += 1
    }
  }

  def copy() : MathArray = {
    val z = new Array[Float](array.length)
    var i = 0
    while(i < array.length){
      z(i) = array(i)
      i += 1
    }
    new MathArray(z)
  }

  def toArray : Array[Float] = array.clone()

  def toList : List[Float] = array.toList

}

object MathArray{

  @inline
  def apply(array:Array[Float]): MathArray = new MathArray(array)

  @inline
  def apply(size:Int): MathArray = MathArray(Array.ofDim[Float](size))

}

