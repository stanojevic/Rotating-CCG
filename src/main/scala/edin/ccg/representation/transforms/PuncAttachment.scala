package edin.ccg.representation.transforms

import edin.ccg.representation.combinators._
import edin.ccg.representation.tree._

object PuncAttachment {

  def attachLeftPuncAtBottom(punc:TreeNode, node:TreeNode) : TreeNode = node match {
    case TerminalNode(_, _) => BinaryNode(RemovePunctuation(true), punc, node)
    case BinaryNode(c, l, r) => BinaryNode(c, attachLeftPuncAtBottom(punc, l), r)
    case UnaryNode(c, child) => UnaryNode(c, attachLeftPuncAtBottom(punc, child))
  }

  ///////////////////////////////// Transform: Attach Punctuation High To Left ///////////////////////
  def reattachPunctuationTopLeft(root:TreeNode) : TreeNode =
    RevealingTransforms.toLeftBranchingWithRevealing(root, withLeftBranching=false)

}
