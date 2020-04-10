package edin.ccg.representation.combinators

import edin.ccg.representation.category._

final case class Conjunction() extends CombinatorBinary{

  override val functorIsLeft = false

  override def canApply(x: Category, y: Category): Boolean =
  // note: EasyCCG has constraint that y cannot be NP\NP
    x match {
      case Atomic(xn, _) if xn.isConjunct =>
        true
      case _ =>
        false
    }

  override def apply(x: Category, y: Category): Category = ConjCat(y)

  override def toString: String = "Φ>" // "Conj"

}

