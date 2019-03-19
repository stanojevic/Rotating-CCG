package edin.ccg.transitions

import edin.ccg.representation.transforms.{RightSpine, Rebranching}
import edin.ccg.representation.tree._


sealed case class RightAdjoinOption(
                               nodeToModify      : TreeNode
                             ) extends TransitionOption {

  override def apply(conf:Configuration): Configuration = (conf : @unchecked) match {
    case Configuration(stack, s@RightAdjunction()) =>
      val left  = stack.second
      val right = stack.first

      val newNode = RightSpine.rightModify(left, right, nodeToModify.category, nodeToModify.span)

      conf.neuralState.refreshNodeRepresentation(left)(newNode)

//      if(conf.neuralState.recursiveNNEarly){
//        assert(! conf.parserProperties.useRevealing || ! conf.neuralState.isUseNodeContentInRevealing)
//        newNode.hierState = left.hierState
//      }else{
//        conf.neuralState.refreshEncoding(newNode)
//      }

      conf.copy(
        lastTransitionOption = this,
        stack = stack.pop.pop.push(newNode),
        state = s.toNormalParsing,
        neuralState = conf.neuralState.addDependenciesForNode(newNode)
      )
  }

  override def toString: String = "RightAdjoinOption("+nodeToModify.category + ", " + nodeToModify.span + ")"

  override def equals(obj: scala.Any): Boolean = obj match {
    case RightAdjoinOption(onodeToModify) =>
      nodeToModify.category == onodeToModify.category &&
      nodeToModify.span == onodeToModify.span
    case _ =>
      false
  }

  override def hashCode(): Int = (nodeToModify.category, nodeToModify.span).##
}

class TransitionRightAdjunction(parserProperties: ParserProperties) extends TransitionController {

  override def currentTransOptions(conf: Configuration): List[TransitionOption] = conf match {
    case Configuration(stack, RightAdjunction()) =>
      val left  = stack.second
      val right = stack.first
      RightSpine.extractRightAdjunctionCandidatesOfficial(left, right.category).map(RightAdjoinOption)
    case _ => List()
  }

  override val allPossibleTransOptions: List[TransitionOption] = List()

}
