package edin.ccg.representation.transforms

import edin.ccg.representation.category.{Category, Functor}
import edin.ccg.representation.combinators._
import edin.ccg.representation.tree._

object Rebranching {

  def toRightBranching(node:TreeNode) : TreeNode = node match {
    case BinaryNode(c, l, r) =>
      sinkForwardRightward(BinaryNode(c, toRightBranching(l), toRightBranching(r)))
    case UnaryNode(c, child) =>
      sinkForwardRightward(UnaryNode(c, toRightBranching(child)))
    case TerminalNode(_, _) =>
      node
  }

  def toLeftBranchingSimplistic(node:TreeNode) : TreeNode = node match {
    case BinaryNode(c, l, r) =>
      sinkForwardLeftward(BinaryNode(c, toLeftBranchingSimplistic(l), toLeftBranchingSimplistic(r)))
    case UnaryNode(c, child) =>
      UnaryNode(c, toLeftBranchingSimplistic(child))
    case _ =>
      node
  }

  ///////////////////////////////// Transform: sink  /////////////////////
  private def attachRightPuncAtBottom(node:TreeNode, punc:TreeNode) : TreeNode = node match {
    case TerminalNode(_, _) => BinaryNode(RemovePunctuation(false), node, punc)
    case BinaryNode(c, l, r) => BinaryNode(c, l, attachRightPuncAtBottom(r, punc))
    case UnaryNode(c, child) => UnaryNode(c, attachRightPuncAtBottom(child, punc))
  }
  def sinkForwardRightward(node:TreeNode) : TreeNode = node match {
    case UnaryNode(c, BinaryNode(RemovePunctuation(true), l, r)) =>
      BinaryNode(RemovePunctuation(true), l, UnaryNode(c, r))
    case BinaryNode(Glue(), BinaryNode(Glue(), a1, a2), a3) =>
      BinaryNode(Glue(), a1, sinkForwardRightward(BinaryNode(Glue(), a2, a3)))
    case BinaryNode(Glue(), l, r) =>
      BinaryNode(Glue(), l, sinkForwardRightward(r))
    case BinaryNode(c, BinaryNode(RemovePunctuation(true), a1, a2), a3) =>
      BinaryNode(RemovePunctuation(true), a1, sinkForwardRightward(BinaryNode(c, a2, a3)))
    case BinaryNode(RemovePunctuation(false), l, r) =>
      attachRightPuncAtBottom(l, r)
    case BinaryNode(high:Forwards, BinaryNode(low:Forwards, a1, a2), a3) =>
      val x = high.order
      val y = low.order
      val m = Forwards.maxB
      if(y>0 && x+y-1<=m)
        BinaryNode(Forwards.b(x+y-1), a1, sinkForwardRightward(BinaryNode(Forwards.b(x), a2, a3)))
      else
        node
    case BinaryNode(RemovePunctuation(true), l, r) =>
      BinaryNode(RemovePunctuation(true), l, sinkForwardRightward(r)) // needed because of the "imperfections" of the algorithm (not enough combinators)
    case _ =>
      node
  }

  def sinkForwardLeftward(node:TreeNode) : TreeNode = node match {
    case BinaryNode(high:Forwards, left, right) =>
      def rebuild(parentOrder: Int, node:TreeNode) : Option[TreeNode] = node match {
        case BinaryNode(c:Forwards, l, r) if parentOrder>=c.order =>
          (c, l.category) match {
            case (B0fwd(), Functor(_, subcat, _)) if subcat matches Category.NP =>
              None
            case _ =>
              val newOrder = parentOrder-c.order+1
              rebuild(newOrder, l) match {
                case Some(newL) =>
                  Some(BinaryNode(c, newL, r))
                case None if newOrder <= Forwards.maxB =>
                  Some(BinaryNode(c, BinaryNode(Forwards.b(newOrder), left, l), r))
                case None =>
                  None
              }
          }
        case _ =>
          None
      }
      rebuild(high.order, right) match {
        case Some(x) => x
        case None => node
      }
    case BinaryNode(RemovePunctuation(true), punc, BinaryNode(c, l, r)) =>
      BinaryNode(c, PuncAttachment.attachLeftPuncAtBottom(punc, l), r)
    case BinaryNode(RemovePunctuation(true), punc, UnaryNode(c, child)) =>
      UnaryNode(c, PuncAttachment.attachLeftPuncAtBottom(punc, child))
    case _ =>
      node
  }


}
