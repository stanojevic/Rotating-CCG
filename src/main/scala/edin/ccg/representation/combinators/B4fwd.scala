package edin.ccg.representation.combinators

import edin.ccg.representation.category._

final case class B4fwd() extends CombinatorBinary with Forwards{

  override val order: Int = 4

  override def canApply(x: Category, y: Category): Boolean =
    (x, y) match {
      case (Functor2(_, Slash.FWD, x2), Functor5(y1, Slash.FWD, _, _, _, _, _, _, _)) =>
        (x2 matches y1) && !(x2 matches Category.NP)
      case _ =>
        false
    }

  override def apply(x: Category, y: Category): Category =
    (x, y) match{
      case (_, _) if x.isModifier && x.isFeatureLess =>
        y
      case (Functor2(x1, Slash.FWD, x2), Functor5(y1, s2, y2, s3, y3, s4, y4, s5, y5)) =>
        val s = x2.getSubstitution(y1)
        Functor5(x1.doSubstitution(s.onlyRight), s2, y2.doSubstitution(s.onlyLeft), s3, y3.doSubstitution(s.onlyLeft), s4, y4.doSubstitution(s.onlyLeft), s5, y5.doSubstitution(s.onlyLeft))
        // Functor(s5, Functor(s4, Functor(s3, Functor(Slash.FWD, x1.doSubstitution(s.onlyRight), y2.doSubstitution(s.onlyLeft)), y3.doSubstitution(s.onlyLeft)), y4.doSubstitution(s.onlyLeft)), y5.doSubstitution(s.onlyLeft))
      case _ =>
        throw new RuntimeException
    }

  override val toString: String = "B4>"

}

