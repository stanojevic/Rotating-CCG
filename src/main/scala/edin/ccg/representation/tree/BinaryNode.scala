package edin.ccg.representation.tree

import edin.ccg.representation.category.Category
import edin.ccg.representation.combinators.CombinatorBinary

final case class BinaryNode(c:CombinatorBinary, leftChild:TreeNode, rightChild:TreeNode) extends TreeNode {

  if(! c.canApply(leftChild.category, rightChild.category))
    throw new Exception(s"cannot form a binary node with combinator $c\nand left child ${leftChild.toCCGbankString}\nand right child ${rightChild.toCCGbankString}")

  override val category:Category = c(leftChild.category, rightChild.category)

  override lazy val span: (Int, Int) = (leftChild.span._1, rightChild.span._2)

  override def toString: String = s"$c-$category"

}
