package edin.ccg.representation.combinators

import edin.ccg.representation.category._

final case class B1fwd() extends CombinatorBinary with Forwards{

  override val order: Int = 1

  override def canApply(x: Category, y: Category): Boolean =
    (x, y) match {
      case (Functor2(_, Slash.FWD, x2), Functor2(y1, Slash.FWD, _)) if x2 matches y1 =>
        !(x2 matches Category.NP)
      case _ =>
        false
    }

  override def apply(x: Category, y: Category): Category = (x, y) match {
    case (_, _) if x.isModifier && x.isFeatureLess => y
    case (_, _) if y.isModifier && y.isFeatureLess => x
    case (Functor2(x1, _, x2), Functor2(y1, _, y2)) =>
      val s = x2.getSubstitution(y1)
      Functor2(x1.doSubstitution(s.onlyRight), Slash.FWD, y2.doSubstitution(s.onlyLeft))
  }

  override val toString: String = "B1>"

}


