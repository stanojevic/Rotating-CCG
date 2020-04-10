package edin.ccg.transitions

import edin.ccg.representation.category.Category
import edin.ccg.representation.transforms.RightSpine
import edin.ccg.representation.tree.{BinaryNode, TreeNode, UnaryNode}


sealed case class RightAdjoinOption(
                                     catToModify : Category,
                                     spanToModify: (Int, Int)
                                     //                               nodeToModify      : TreeNode
                             ) extends TransitionOption {

  def findNodeToAdjoinTo(root:TreeNode) : TreeNode = root match {
    case node if node.category == catToModify && node.span == spanToModify =>
      node
    case BinaryNode(_, _, child) =>
      assert(child.span._2 == spanToModify._2)
      this.findNodeToAdjoinTo(child)
    case UnaryNode(_, child) =>
      assert(child.span._2 == spanToModify._2)
      this.findNodeToAdjoinTo(child)
    case _ =>
      sys.error("right adjunction node not found")
  }

  override def apply(conf:Configuration): Configuration = (conf : @unchecked) match {
    case Configuration(stack, s@RightAdjunction()) =>
      val left  = stack.second
      val right = stack.first

      val newNode = RightSpine.rightModify(left, right, catToModify, spanToModify)

      conf.neuralState.refreshNodeRepresentation(left)(newNode)

      conf.copy(
        lastTransitionOption = this,
        stack = stack.pop.pop.push(newNode),
        state = s.toNormalParsing,
        neuralState = conf.neuralState.addDependenciesForNode(newNode)
      )
  }

}

class TransitionRightAdjunction(parserProperties: ParserProperties) extends TransitionController {

  override def currentTransOptions(conf: Configuration): List[TransitionOption] = conf match {
    case Configuration(stack, RightAdjunction()) =>
      val left  = stack.second
      val right = stack.first
      RightSpine.extractRightAdjunctionCandidatesOfficial(left, right.category).map(node => RightAdjoinOption(node.category, node.span))
    case _ =>
      Nil
  }

  override val allPossibleTransOptions: List[TransitionOption] = Nil

}
