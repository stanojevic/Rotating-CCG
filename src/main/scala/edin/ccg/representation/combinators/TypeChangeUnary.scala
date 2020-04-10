package edin.ccg.representation.combinators

import edin.ccg.representation.category.{Category, ConjCat}

final case class TypeChangeUnary(
                                  from:Category,
                                  to:Category
                                ) extends CombinatorUnary {

  override def canApply(x: Category): Boolean = x == from

  override def apply(x: Category): Category = to

  override val toString: String = if(from == Category.N && to == Category.NP){
    s"TC_BNP"
  }else{
    s"TC__${from}__=>__$to"
  }

  override def isUnaryCoordination: Boolean = to match {
    case ConjCat(`from`) => true
    case _ => false
  }
}

