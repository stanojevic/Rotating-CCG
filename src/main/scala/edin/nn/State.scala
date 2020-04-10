package edin.nn

import edu.cmu.dynet.Expression

trait State {

  val h:Expression

  protected def myEquals(o: Any): Boolean

  override final def equals(o: Any): Boolean = myEquals(o)

  protected def myHash(): Int

  override def hashCode(): Int = myHash()

}

trait StateClosed extends State{
  override final def myEquals(o: Any) : Boolean = throw new Exception("not supposed to call this")
  override final def myHash()         : Int     = throw new Exception("not supposed to call this")
}

class SimpleStateLazy(hh: => Expression) extends StateClosed{
  lazy val h : Expression = hh
}

object SimpleStateLazy{
  def apply(h: => Expression) : SimpleStateLazy = new SimpleStateLazy(h)
}

class WrappedStateLazy[T](hh: => Expression, val el:T) extends State {
  lazy val h: Expression = hh
  override protected def myEquals(o: Any): Boolean = o match {
    case that:WrappedStateLazy[T] => this.el == that.el
    case _ => false
  }
  override protected def myHash(): Int = el.##
}

object WrappedStateLazy{
  def apply[T](hh: => Expression, el:T) : WrappedStateLazy[T] =
    new WrappedStateLazy(hh, el)
}

sealed case class SimpleState(h:Expression) extends StateClosed
sealed case class WrappedState[T](h:Expression, el:T) extends State{
  override protected def myEquals(o: Any): Boolean = o match {
    case WrappedState(_, `el`) => true
    case _ => false
  }
  override protected def myHash(): Int = el.##
}

