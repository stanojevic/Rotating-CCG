package edin.ccg.representation.combinators

import edin.ccg.representation.category.{Category, Functor, Slash}

final case class TypeRaiser(
                             fromMatcher:Category,
                             functionResult:Category,
                             slash:Slash
                           ) extends CombinatorUnary {

  def canApply(x: Category): Boolean = fromMatcher matches x

  def apply(x: Category): Category   = Functor(slash, functionResult, Functor(slash.invert, functionResult, x))

  override val toString: String = {
    val dir = if(slash == Slash.FWD) ">" else "<"
    s"TR${dir}___${Functor(slash.invert, functionResult, fromMatcher)}"
  }

  override def isUnaryCoordination: Boolean = false
}


