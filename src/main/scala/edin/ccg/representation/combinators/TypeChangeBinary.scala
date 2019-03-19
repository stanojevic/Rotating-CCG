package edin.ccg.representation.combinators

import edin.ccg.representation.category._

final case class TypeChangeBinary(l:Category, r:Category, p:Category) extends CombinatorBinary{

  override val functorIsLeft: Boolean = false // this doesn't make sense but whatever

  override def canApply(x: Category, y: Category): Boolean = this.l == x && this.r == y

  override def apply(x: Category, y: Category): Category = p

  override val toString: String = s"TC2___${l}___${r}___==>___${p}"

  val isCoordinationOfNotAlikes : Boolean = p.isInstanceOf[ConjCat]
//    isCoordinationOfNotAlikesGeneral(rightAdjunction = true, rightBalanced = true) ||
//    isCoordinationOfNotAlikesGeneral(rightAdjunction = true, rightBalanced = false) ||
//    isCoordinationOfNotAlikesGeneral(rightAdjunction = false, rightBalanced = true) ||
//    isCoordinationOfNotAlikesGeneral(rightAdjunction = false, rightBalanced = false)
//
//
//  private def isCoordinationOfNotAlikesGeneral(rightAdjunction:Boolean, rightBalanced:Boolean) : Boolean = {
//    val slash = if(rightAdjunction) Slash.BCK else Slash.FWD
//    val conj = if(rightBalanced) l else r
//    (conj, p) match {
//      case (Atomic(nt, _), Functor(`slash`, a, b)) if nt.isConjunct && a==b => true
//      case _ => false
//    }
//
//  }

}

