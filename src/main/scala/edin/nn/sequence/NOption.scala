package edin.nn.sequence

import edin.nn.{StateClosed, State}
import edin.nn.DyFunctions._
import edu.cmu.dynet.Expression

sealed abstract class NOption[T<:State](dim:Int) extends StateClosed{

  def isDefined : Boolean

  def isEmpty : Boolean = ! isDefined

  // these two are helpe functions so that I don't have to distribute NOptionConfig all over the code
  def none : NNone[T] = NNone()(dim)
  def some(x:T) : NSome[T] = NSome(x)(dim)
}

case class NNone[T<:State]()(dim:Int) extends NOption[T](dim){
  override lazy val h: Expression = zeros(dim)
  override def isDefined: Boolean = false
}

case class NSome[T<:State](x:T)(dim:Int) extends NOption[T](dim){
  override lazy val h: Expression = x.h
  override def isDefined: Boolean = true
}
