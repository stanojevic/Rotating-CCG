package edin.ccg.representation.combinators

import edin.ccg.representation.category.{Category, ConjCat}

final case class ConjunctionTop() extends CombinatorBinary {

  override val functorIsLeft: Boolean = true

  override def canApply(x: Category, y: Category): Boolean = y match {
    case ConjCat(catR) if x == catR => true
    case _ => false
  }

  override def apply(x: Category, y: Category): Category = x

  override def toString: String = "ConjTop"

}
