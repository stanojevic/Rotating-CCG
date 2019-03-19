package edin.algorithms

class Pointer[T >: Null <: AnyRef] {

  def this(x:T) {
    this()
    this.content = x
  }

  var content:T = _

  def apply() : T = content

}
