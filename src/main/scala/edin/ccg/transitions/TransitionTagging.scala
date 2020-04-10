package edin.ccg.transitions

import edin.ccg.representation.category.Category
import edin.ccg.representation.tree.TerminalNode

final case class TaggingOption(tag:Category) extends TransitionOption{

  override def apply(conf:Configuration): Configuration = (conf : @unchecked) match {
    case Configuration (stack, s@Tagging() ) =>
      conf.copy(
        lastTransitionOption = this,
        state = s.toBlockedWaitingForWord(tag)
      )
    case Configuration (stack, s@TaggingWF(word) ) =>
      val node = TerminalNode(word, tag)
      node.position = if(stack.size == 0) 0 else stack.first.span._2
      val newNeuralState = conf.neuralState.encodeTerminal(node, s.outsideRepr)
      conf.copy(
        lastTransitionOption = this,
        stack                = stack.push(node),
        state                = s.toNormalParsing,
        neuralState          = newNeuralState
      )
  }

}
