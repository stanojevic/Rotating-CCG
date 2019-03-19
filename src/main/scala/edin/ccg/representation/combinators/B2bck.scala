package edin.ccg.representation.combinators

import edin.ccg.representation.category._


final case class B2bck(isCrossed:Boolean) extends CombinatorBinary with Backwards {

  private val leftSlash = if(isCrossed) Slash.FWD else Slash.BCK

  override val order: Int = 2

  override def canApply(x: Category, y: Category): Boolean =
    (x, y) match {
      case (Functor3(x1, `leftSlash`, _, _, _), Functor2(_, Slash.BCK, y2)) if x1 matches y2 =>
        !isCrossed || x1.isVerbal
         // && (xLeftNT.isNoun || xLeftNT.isNounPhrase)
      case _ =>
        false
    }

  override def apply(x: Category, y: Category): Category =
    (x, y) match {
      case (_, _) if y.isBackSlashAdjunctCategory && y.isFeatureLess =>
        x
      case (Functor3(x1, `leftSlash`, x2, xSlash, x3), Functor2(y1, _, y2)) =>
        val s = x1.getSubstitution(y2)
        Functor3(y1.doSubstitution(s.onlyLeft), leftSlash, x2.doSubstitution(s.onlyRight), xSlash, x3.doSubstitution(s.onlyRight))
      case _ =>
        throw new RuntimeException()
    }

  override val toString: String = leftSlash match {
    case Slash.BCK => "B2<"
    case Slash.FWD => "B2<x"
  }

}

