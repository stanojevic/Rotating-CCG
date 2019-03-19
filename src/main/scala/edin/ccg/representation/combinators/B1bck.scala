package edin.ccg.representation.combinators

import edin.ccg.representation.category._

final case class B1bck(isCrossed:Boolean) extends CombinatorBinary with Backwards {

  private val leftSlash = if(isCrossed) Slash.FWD else Slash.BCK

  override val order: Int = 1

  override def canApply(x: Category, y: Category): Boolean =
    (x, y) match {
      case (Functor2(x1, `leftSlash`, _), Functor2(_, Slash.BCK, y2)) if x1 matches y2 =>
        !isCrossed || x1.isVerbal
      case _ =>
        false
    }

  override def apply(x: Category, y: Category): Category =
    (x, y) match{
      case (_, _) if y.isBackSlashAdjunctCategory && y.isFeatureLess =>
        x
      case (Functor2(x1, `leftSlash`, x2), Functor2(y1, Slash.BCK, y2)) =>
        val s = x1.getSubstitution(y2)
        Functor2(y1.doSubstitution(s.onlyLeft), leftSlash, x2.doSubstitution(s.onlyRight))
      case _ =>
        throw new RuntimeException()
    }

  override val toString: String = leftSlash match {
    case Slash.BCK => "B1<"
    case Slash.FWD => "B1<x"
  }

}

