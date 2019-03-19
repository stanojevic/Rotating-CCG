package edin.ccg.representation.transforms

import edin.ccg.representation.category._
import edin.ccg.representation.combinators._
import edin.ccg.representation.tree._

object AdjunctionGeneral {

  ///////////////////////////////// Transform: Lowering Adjunction /////////////////////

  def lowerAdjunction(node:TreeNode, lowerLeftAdjuncts:Boolean) : TreeNode = {
    lowerAdjunctionRec(List(), node, List())(lowerLeftAdjuncts)
  }
  private def lowerAdjunctionRec(leftAdjs:List[(CombinatorBinary, TreeNode)], node:TreeNode, rightAdjs:List[(CombinatorBinary, TreeNode)])(implicit lowerLeftAdjuncts:Boolean) : TreeNode = node match {
    case BinaryNode(c, l, r) =>
      if(isLeftAdjunctionPlace(node)){
        lowerAdjunctionRec(leftAdjs++List((c, l)), r, rightAdjs)
      }else if(isRightAdjunctionPlaceWithoutConjunction(node)){
        lowerAdjunctionRec(leftAdjs, l, (c, r)::rightAdjs)
      }else{
        val newL = lowerAdjunctionRec(List(), l, List())
        val newR = lowerAdjunctionRec(List(), r, List())
        val newNode = BinaryNode(c, newL, newR)
        attachAdjuncts(leftAdjs, newNode, rightAdjs)
      }
    case UnaryNode(c, child) =>
      val newNode = UnaryNode(c, lowerAdjunctionRec(List(), child, List()))
      attachAdjuncts(leftAdjs, newNode, rightAdjs)
    case TerminalNode(_, _) =>
      attachAdjuncts(leftAdjs, node, rightAdjs)
  }
  private def attachAdjuncts(leftAdjs:List[(CombinatorBinary, TreeNode)], node:TreeNode, rightAdjs:List[(CombinatorBinary, TreeNode)])(implicit lowerLeftAdjuncts:Boolean) : TreeNode ={
    if(lowerLeftAdjuncts){
      val z = leftAdjs.foldRight(node){case ((c, x), y) => BinaryNode(c, x, y)}
      rightAdjs.foldLeft(z){case (x, (c, y)) => BinaryNode(c, x, y)}
    }else{
      val z = rightAdjs.foldLeft(node){case (x, (c, y)) => BinaryNode(c, x, y)}
      leftAdjs.foldRight(z){case ((c, x), y) => BinaryNode(c, x, y)}
    }
  }

  ///////////////////////////////// Transform: add revealing  /////////////////////
  private def isForwardAdjunctCategory(cat:Category) : Boolean = cat match {
    case Functor(Slash.FWD, x, y) if x == y => true
    case _ => false
  }
  def isRightAdjunctionPlaceWithoutConjunction(node:TreeNode) : Boolean = node match {
    case BinaryNode(_, _, BinaryNode(Conjunction(), _, _)) =>
      false
    case BinaryNode(c, l, r) =>
      c.isRightAdjCombinator(l.category, r.category)
    case _ =>
      false
  }
  def isRightAdjunctionPlace(node:TreeNode) : Boolean = node match {
    case BinaryNode(c, l, r) =>
      c.isRightAdjCombinator(l.category, r.category)
    case _ => false
  }
  def isLeftAdjunctionPlace(node:TreeNode) : Boolean = node match {
    case BinaryNode(RemovePunctuation(true), _, _) =>
      true
    case BinaryNode(c, l, r) =>
      CombinatorBinary.allForward.contains(c) && isForwardAdjunctCategory(l.category) && node.category == r.category
    case _ => false
  }
  case class RightAdjoinCombinator(c:Category, span:(Int, Int)) extends CombinatorBinary {
    override val functorIsLeft: Boolean = false
    override def canApply(x: Category, y: Category): Boolean = true
    override def apply(x: Category, y: Category) : Category = x
    override val toString: String = s"rightAdjoin $c $span"
  }

}
