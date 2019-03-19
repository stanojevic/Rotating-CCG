package edin.ccg.representation.tree

import edin.ccg.representation.combinators.CombinatorUnary
import edin.ccg.representation.predarg.PredArg

final case class UnaryNode(c:CombinatorUnary, child:TreeNode) extends TreeNode {
  override val category = c(child.category)

  override lazy val span:(Int, Int) = child.span

  override def toString: String = s"$c-$category"

}
