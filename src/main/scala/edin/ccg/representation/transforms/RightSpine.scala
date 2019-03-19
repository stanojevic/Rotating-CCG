package edin.ccg.representation.transforms

import edin.ccg.representation.category._
import edin.ccg.representation.combinators._
import edin.ccg.representation.tree._

object RightSpine {

  def extractRightAdjunctionCandidatesOfficial(leftNode:TreeNode, rightCategory:Category, topK:Int, bottomK:Int) : List[TreeNode] = {
    val opts = extractRightAdjunctionCandidatesOfficial(leftNode, rightCategory)
    (opts.take(topK) ++ opts.takeRight(bottomK)).distinct
  }

  def extractRightAdjunctionCandidatesOfficial(leftNode:TreeNode, rightCategory:Category) : List[TreeNode] =
    if(rightCategory.isBackSlashAdjunctCategory || rightCategory.isInstanceOf[ConjCat]) {
      rightSpineNoLeftPunc(leftNode).filter { n =>
        !isConjNodeStrict(n) && CombinatorBinary.allBackwardAndCrossed.exists(_.isRightAdjCombinator(n.category, rightCategory))
      }
    }else{
      Nil
    }

  private def isConjNodeStrict: TreeNode => Boolean = {
    case BinaryNode(CombinatorBinary.conj, _, _) =>
      true
    case _ =>
      false
  }

  private def isConjNode : TreeNode => Boolean = {
    case BinaryNode(CombinatorBinary.conj, _, _) =>
      true
    case BinaryNode(c:TypeChangeBinary, _, _) if c.isCoordinationOfNotAlikes =>
      true
    case UnaryNode(c, _) if c.isUnaryCoordination =>
      true
    case _ =>
      false
  }

  def rightSpineAll(node:TreeNode) : List[TreeNode] =
    node match {
      case BinaryNode(_, _, r) => node::rightSpineAll(r)
      case UnaryNode(_, r)     => node::rightSpineAll(r)
      case TerminalNode(_, _)  => node::Nil
    }

  def rightSpineNoLeftPunc(node:TreeNode) : List[TreeNode] = node match {
    case BinaryNode(RemovePunctuation(true), _, r) => rightSpineNoLeftPunc(r)
    case BinaryNode(_, _, r) => node::rightSpineNoLeftPunc(r)
    case UnaryNode(_, r)     => node::rightSpineNoLeftPunc(r)
    case TerminalNode(_, _)  => node::Nil
  }

  def rightSpineNoLeftAdjunction(node:TreeNode) : List[TreeNode] = node match {
    case BinaryNode(_, _, r) if AdjunctionGeneral.isLeftAdjunctionPlace(node) => rightSpineNoLeftAdjunction(r)
    case BinaryNode(_, _, r) => node::rightSpineNoLeftAdjunction(r)
    case UnaryNode(_, r)     => node::rightSpineNoLeftAdjunction(r)
    case TerminalNode(_, _)  => node::Nil
  }

  def rightSpineIncludingLeftAdjunction(node:TreeNode) : List[TreeNode] =
    node match {
      case BinaryNode(_, _, r) if AdjunctionGeneral.isLeftAdjunctionPlace(node) => node :: rightSpineIncludingLeftAdjunction(r).tail
      case BinaryNode(_, _, r) => node::rightSpineIncludingLeftAdjunction(r)
      case UnaryNode(_, r)     => node::rightSpineIncludingLeftAdjunction(r)
      case TerminalNode(_, _)  => node::Nil
    }

  ///////////////////////////////// Transform: right modify  /////////////////////
  def rightModify(leftRoot:TreeNode, rightModifier:TreeNode, cat:Category, span:(Int, Int)) : TreeNode = {
    if(leftRoot.span == span && leftRoot.category == cat){
      val comb = CombinatorBinary.allBackwardAndCrossed.find(_.isRightAdjCombinator(leftRoot.category, rightModifier.category)).get
      BinaryNode(comb, leftRoot, rightModifier)
    }else{
      leftRoot match {
        case UnaryNode(c, child) => UnaryNode(c, RightSpine.rightModify(child, rightModifier, cat, span))
        case TerminalNode(_, _) => throw new Exception("failed to find the node for right adjunction")
        case BinaryNode(c, l, r) => BinaryNode(c, l, RightSpine.rightModify(r, rightModifier, cat, span))
      }
    }
  }
}
