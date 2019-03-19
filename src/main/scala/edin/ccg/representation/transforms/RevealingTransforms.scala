package edin.ccg.representation.transforms

import edin.ccg.representation.combinators._
import edin.ccg.representation.transforms.AdjunctionGeneral.RightAdjoinCombinator
import edin.ccg.representation.tree._

object RevealingTransforms {

  def addRevealing(node:TreeNode) : TreeNode = addRevealingRec(Rebranching.toRightBranching(node))

  private def addRevealingRec(node:TreeNode) : TreeNode = node match {
    case TerminalNode(_, _) =>
      node
    case UnaryNode(comb, child) =>
      UnaryNode(comb, addRevealing(child))
    case BinaryNode(comb, l, r) =>
      if(AdjunctionGeneral.isRightAdjunctionPlace(node))
        BinaryNode(RightAdjoinCombinator(l.category, l.span), addRevealing(l), addRevealing(r))
      else
        BinaryNode(comb, addRevealing(l), addRevealing(r))
  }

  private def extractRightAdjunctAndPuncSubtrees(node:TreeNode) : (TreeNode, List[(CombinatorBinary, TreeNode)]) = node match {
    case BinaryNode(comb, left, right) =>
      val (coreLeft, leftRightAdjuncts) = extractRightAdjunctAndPuncSubtrees(left)
      val (coreRight, rightRightAdjuncts) = extractRightAdjunctAndPuncSubtrees(right)
      comb match {
        case RightAdjoinCombinator(_, _) =>
          (coreLeft                             , leftRightAdjuncts ++ rightRightAdjuncts :+ (comb, coreRight) )
        case RemovePunctuation(false) if coreRight.span._2-coreRight.span._1==1  =>
          (coreLeft                             , leftRightAdjuncts ++ rightRightAdjuncts :+ (comb, coreRight) )
        case RemovePunctuation(true)  if coreLeft.span._2-coreLeft.span._1==1    =>
          (coreRight                            , leftRightAdjuncts ++ rightRightAdjuncts :+ (comb, coreLeft ) )
        case _ =>
          (BinaryNode(comb, coreLeft, coreRight), leftRightAdjuncts ++ rightRightAdjuncts)
      }
    case UnaryNode(comb, child) =>
      val (coreChild, rightAdjs) = extractRightAdjunctAndPuncSubtrees(child)
      (UnaryNode(comb, coreChild), rightAdjs)
    case TerminalNode(_, _) =>
      (node, Nil)
  }

  private def reattachAdjunctOrPunc(core:TreeNode, comb: CombinatorBinary, adj:TreeNode) : TreeNode =
    if(core.span._2 == adj.span._1)
      comb match {
        case RemovePunctuation(_) =>
          BinaryNode(RemovePunctuation(false), core, adj)
        case _ =>
          BinaryNode(comb, core, adj)
      }
    else
      core match {
        case BinaryNode(c, l, r) =>
          if(r.span._1<adj.span._1)
            BinaryNode(c, l, reattachAdjunctOrPunc(r, comb, adj))
          else
            BinaryNode(c, reattachAdjunctOrPunc(l, comb, adj), r)
        case UnaryNode(c, child) =>
          UnaryNode(c, reattachAdjunctOrPunc(child, comb, adj))
        case _ =>
          throw new Exception("should be here")
      }

  def toLeftBranchingWithRevealing(root:TreeNode, withLeftBranching:Boolean) : TreeNode = {
    val (coreRoot, rightAdjsAndPuncs) = extractRightAdjunctAndPuncSubtrees(root)
    val coreRootLefted = if(withLeftBranching) Rebranching.toLeftBranchingSimplistic(coreRoot) else coreRoot
    val rightAdjsAndPuncsLeftedSorted = rightAdjsAndPuncs.map{case (comb, node) => (comb, Rebranching.toLeftBranchingSimplistic(node))}.sortBy(_._2.span)
    val leftPuncCorner:Option[TreeNode] = rightAdjsAndPuncsLeftedSorted.headOption match {
      case Some((RemovePunctuation(_), first)) if first.span._1 == root.span._1 =>
        val corner = rightAdjsAndPuncsLeftedSorted.sliding(2).takeWhile {
          case List((RemovePunctuation(_), x), (RemovePunctuation(_), y)) if x.span._2 == y.span._1 => true
          case _ => false
        }.map {
          _(1)
        }.foldLeft(first) { case (l, (_, r)) => BinaryNode(RemovePunctuation(false), l, r) }
        Some(corner)
      case _ =>
        None
    }
    val rightAdjsAndPuncsLeftedSortedUncornered = leftPuncCorner match {
      case None =>
        rightAdjsAndPuncsLeftedSorted
      case Some(corner) =>
        rightAdjsAndPuncsLeftedSorted.dropWhile(_._2.span._2 <= corner.span._2)
    }

    val coreRootLeftedCornered = leftPuncCorner match {
      case None =>
        coreRootLefted
      case Some(puncCorner) =>
        PuncAttachment.attachLeftPuncAtBottom(puncCorner, coreRootLefted)
      // BinaryNode(RemovePunctuation(true), puncCorner, coreRootLefted)
    }

    val newRoot = rightAdjsAndPuncsLeftedSortedUncornered.foldLeft(coreRootLeftedCornered){case (core, (comb, adj)) => reattachAdjunctOrPunc(core, comb, adj)}
    assert(root.words == newRoot.words)
    newRoot
  }


}
