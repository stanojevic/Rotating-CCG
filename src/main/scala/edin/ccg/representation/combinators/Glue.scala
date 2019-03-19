package edin.ccg.representation.combinators

import edin.ccg.representation.category.Category

final case class Glue() extends CombinatorBinary{

  override val functorIsLeft = false // doesn't really matter for this one


  override def canApply(x: Category, y: Category): Boolean = true

  override def apply(x: Category, y: Category): Category = x

  override val toString: String = "Glue"

}


