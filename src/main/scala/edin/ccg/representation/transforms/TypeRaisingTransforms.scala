package edin.ccg.representation.transforms

import edin.ccg.representation.category._
import edin.ccg.representation.combinators._
import edin.ccg.representation.tree._

object TypeRaisingTransforms {

  def addSubjectTypeRaising(node:TreeNode) : TreeNode = node match {
    case UnaryNode(comb, child) =>
      UnaryNode(comb, addSubjectTypeRaising(child))
    case x@TerminalNode(_, _) =>
      x
    case BinaryNode(B0bck(), leftChild, rightChild) if leftChild.category.matches(Category.NP) && rightChild.category.matches(Category.S_back_NP) =>
      BinaryNode(B0fwd(), UnaryNode(CombinatorUnary.typeRaiserSubject, addSubjectTypeRaising(leftChild)), addSubjectTypeRaising(rightChild))
    case BinaryNode(comb, leftChild, rightChild) =>
      BinaryNode(comb, addSubjectTypeRaising(leftChild), addSubjectTypeRaising(rightChild))
  }

  def removeSubjectTypeRaising(node:TreeNode) : TreeNode = {
    removeSubjectTypeRaisingRec(raiseTRinConj(node))
  }
  private def removeSubjectTypeRaisingRec(node:TreeNode) : TreeNode = node match {
    case BinaryNode(B0fwd(), UnaryNode(tr, a), b) if a.category == Category.NP && tr.isInstanceOf[TypeRaiser] =>
      BinaryNode(B0bck(), removeSubjectTypeRaisingRec(a), removeSubjectTypeRaisingRec(b))
    case BinaryNode(c, a, b) =>
      BinaryNode(c, removeSubjectTypeRaisingRec(a), removeSubjectTypeRaisingRec(b))
    case UnaryNode(comb, child) =>
      UnaryNode(comb, removeSubjectTypeRaisingRec(child))
    case TerminalNode(_, _) =>
      node
  }

  ///////////////////////////////// Transform: Type Raising in Conjunstion  /////////////////////

  def lowerTRinConj(node:TreeNode) : TreeNode = {
    import CombinatorBinary.{b0b, conj}
    node match {
      case UnaryNode(raiser, BinaryNode(`b0b`, a, BinaryNode(`conj`, b, c))) if raiser.isInstanceOf[TypeRaiser] =>
        BinaryNode(b0b, lowerTRinConj(UnaryNode(raiser, a)), BinaryNode(conj, b, lowerTRinConj(UnaryNode(raiser, c))))
      case UnaryNode(comb, child) =>
        UnaryNode(comb, lowerTRinConj(child))
      case BinaryNode(comb, l, r) =>
        BinaryNode(comb, lowerTRinConj(l), lowerTRinConj(r))
      case TerminalNode(_, _) =>
        node
    }
  }

  def raiseTRinConj(node:TreeNode) : TreeNode = {
    import CombinatorBinary.{b0b, conj}
    node match {
      case BinaryNode(comb, l, r) =>
        BinaryNode(comb, raiseTRinConj(l), raiseTRinConj(r)) match {
          case BinaryNode(`b0b`, UnaryNode(tr1, a), BinaryNode(`conj`, b, UnaryNode(tr2, c))) if tr1.isInstanceOf[TypeRaiser] && tr1 == tr2 =>
            UnaryNode(tr1, BinaryNode(b0b, a, BinaryNode(conj, b, c)))
          case x =>
            x
        }
      case UnaryNode(comb, child) =>
        UnaryNode(comb, raiseTRinConj(child))
      case TerminalNode(_, _) =>
        node
    }
  }


}
