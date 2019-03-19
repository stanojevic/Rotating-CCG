package edin.ccg.representation.combinators

import edin.ccg.representation.category._


final case class B0fwd() extends CombinatorBinary with Forwards{

  override val order: Int = 0

  override def canApply(x: Category, y: Category): Boolean =
    x match {
      case Functor2(_, Slash.FWD, right) if right.matches(y) => true
      case _ => false
    }

  override def apply(x: Category, y: Category): Category =
    x match {
      case Functor2(_, Slash.FWD, _) if x.isModifier && x.isFeatureLess =>
        y
      case Functor2(xLeft, Slash.FWD, xRight) =>
        val s = xRight.getSubstitution(y)
        xLeft.doSubstitution(s.onlyRight)
      case _ =>
        throw new RuntimeException
    }

  override val toString: String = "B0>"

}

