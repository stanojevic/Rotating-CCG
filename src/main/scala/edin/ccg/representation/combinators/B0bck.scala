package edin.ccg.representation.combinators

import edin.ccg.representation.category._


final case class B0bck() extends CombinatorBinary with Backwards{

  override val order: Int = 0

  override def canApply(x: Category, y: Category): Boolean = {
    y match {
      case Functor2(_, Slash.BCK, right) if right.matches(x) => true
      case _ => false
    }
  }

  override def apply(x: Category, y: Category): Category =
    y match {
      case _ if y.isBackSlashAdjunctCategory && y.isFeatureLess =>
        x
      case Functor2(yLeft, Slash.BCK, yRight) =>
        yLeft.doSubstitution(yRight.getSubstitution(x))
    }

  override val toString: String = "B0<"

}

