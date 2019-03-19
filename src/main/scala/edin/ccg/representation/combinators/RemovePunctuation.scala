package edin.ccg.representation.combinators

import edin.ccg.representation.category.{Atomic, Category}

final case class RemovePunctuation(punctuationIsLeft:Boolean) extends CombinatorBinary{

  override val functorIsLeft: Boolean = ! punctuationIsLeft

  override def canApply(x: Category, y: Category): Boolean = {
    if(punctuationIsLeft){
      x match {
        case Atomic(xn, _) if xn.isPunctuation => true
        case _ => false
      }
    }else{
      y match {
        case Atomic(yn, _) if yn.isPunctuation => true
        case _ => false
      }
    }
  }

  override def apply(x: Category, y: Category): Category =
    if(punctuationIsLeft)
      y
    else
      x

  override def toString: String = "P"+(if(punctuationIsLeft) "<" else ">")

}


