package edin.ccg.representation.combinators

import edin.ccg.representation.category._


final case class B2fwd() extends CombinatorBinary with Forwards {

  override val order: Int = 2

  override def canApply(x: Category, y: Category): Boolean =
    (x, y) match {
      case (Functor2(_, Slash.FWD, x2), Functor3(y1, Slash.FWD, _, _, _)) if x2 matches y1 =>
        !(x2 matches Category.NP)
      case _ =>
        false
    }

  override def apply(x: Category, y: Category): Category =
    (x, y) match{
      case (_, _) if x.isModifier && x.isFeatureLess =>
        y
      case (Functor2(x1, _, x2), Functor3(y1, _, y2, s3, y3)) =>
        val s = x2.getSubstitution(y1)
        Functor3(x1.doSubstitution(s.onlyRight), Slash.FWD, y2.doSubstitution(s.onlyLeft), s3, y3.doSubstitution(s.onlyLeft))
      case _ =>
        throw new RuntimeException()
    }

  override val toString: String = """B2>"""

}

