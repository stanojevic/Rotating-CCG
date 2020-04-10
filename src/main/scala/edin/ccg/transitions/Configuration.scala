package edin.ccg.transitions

import edin.ccg.parsing.Parser
import edin.ccg.representation._
import edin.ccg.representation.combinators._
import edin.ccg.representation.transforms.{PuncAttachment, TypeRaisingTransforms}
import edin.ccg.representation.tree.{BinaryNode, TerminalNode, TreeNode}
import edin.nn.sequence.NeuralStack
import edu.cmu.dynet.Expression
import edin.nn.DyFunctions._
import edin.nn.{DyFunctions, State}
import edin.nn.embedder.Embedder

import scala.util.Random

final case class ParsingException(msg:String, left:TreeNode, right:TreeNode) extends Exception {
  override def toString: String = msg
}

final case class Configuration(
                          stack        : NeuralStack[TreeNode],
                          state        : ConfigurationState
                        )(
                          val totalScoreExpression   : Expression,
                          val neuralState            : NeuralState,
                          val parserProperties       : ParserProperties,
                          val allS2I                 : AllS2I,
                          val combinatorsContainer   : CombinatorsContainer,
                          val lastTransitionOption   : Option[TransitionOption],
                          val prevConf               : Option[Configuration],
                          val maxStackSize           : Int
                        ) extends State {

  override def myEquals(o: Any): Boolean = o match {
    case that : Configuration => this.lastTransitionOption == that.lastTransitionOption && this.prevConf == that.prevConf
    case _ => false
  }
  override def myHash(): Int = throw new Exception("not supposed to call this")

  lazy val totalScore: Float = totalScoreExpression.toFloat

  def extractTree : TreeNode =
    stack.bottomToTop.
      map( TypeRaisingTransforms.removeSubjectTypeRaising ).
      map( PuncAttachment.reattachPunctuationTopLeft      ).
      reduceRight(BinaryNode(Glue(), _, _))

  def saveVisualStackState(prefix:String) : Unit = {
    val r = Random.nextInt()
    System.err.println(s"saving stack state to ${prefix}_$r")
    stack.bottomToTop.zipWithIndex.foreach{case (node, i) =>
      node.saveVisual(s"${prefix}_${r}_stack_$i")
    }
  }

  def allConfsFromBeginning:List[Configuration] = prevConf match {
    case Some(prevC) => prevC.allConfsFromBeginning :+ this
    case None => List(this)
  }

  def copy(
            lastTransitionOption  : TransitionOption              ,
            prevConf              : Configuration                 = this,
            stack                 : NeuralStack[TreeNode]         = this.stack,
            state                 : ConfigurationState            = this.state,
            neuralState           : NeuralState                   = this.neuralState
          ) : Configuration = {
    val lastTrans  = Option(lastTransitionOption)
    val transScoreExpression = this.lookupLogProb(lastTransitionOption)
    val totalScoreExpression = this.totalScoreExpression + transScoreExpression
    Configuration(
      stack        = stack,
      state        = state
    )(
      totalScoreExpression = totalScoreExpression,
      neuralState          = neuralState,
      parserProperties     = parserProperties,
      allS2I               = allS2I,
      combinatorsContainer = combinatorsContainer,
      lastTransitionOption = lastTrans,
      prevConf             = Some(prevConf),
      maxStackSize         = maxStackSize
    )
  }

  override lazy val h: Expression =
    neuralState.configurationCombiner(
        stack.h,
        state.outsideRepr,
        if(neuralState.depsSetState == null) null else neuralState.depsSetState.h
    )

  def lookupLogProb(x:TransitionOption) : Expression =
    state match {
      case Tagging() | TaggingWF(_) =>
        if(allS2I.taggingOptions2i.contains(x)){
          val i = allS2I.taggingOptions2i(x)
          transitionLogDistribution._2(i)
        }else{
          // THIS IS FOR DUMMY TRANSITIONS THAT WE WANT TO IGNORE DURING TRAINING
          scalar(0f)
        }
      case RightAdjunction() | NormalParsing()      =>
        transitionLogDistribution._1.zipWithIndex.find(_._1 == x) match {
          case Some((_, i)) =>
            transitionLogDistribution._2(i)
          case None =>
            val right = stack.first
            val left = if(stack.size>=2) stack.second else null
            throw ParsingException(
              msg = s"couldn't parse with\nstate:$state\ntransition:$x\noffered options:${transitionLogDistribution._1}\nstack size:${stack.size}",
              left = left,
              right = right
            )
        }
//        if(!transitionLogDistribution._1.zipWithIndex.exists(_._1 == x)){
//          // System.err.println(x)
//          val right = stack.first
//          val left = if(stack.size>=2) stack.second else null
////          val reduceOptions = new TransitionReduce(parserProperties, combinatorsContainer).currentTransOptions(this)
////          val revealingOptions = new TransitionRightAdjunction(parserProperties).currentTransOptions(this)
//          throw ParsingException(
//            msg = s"couldn't parse with\nstate:$state\ntransition:$x\noffered options:${transitionLogDistribution._1}\nstack size:${stack.size}",
//            left = left,
//            right = right
//          )
//        }
//        val i = transitionLogDistribution._1.zipWithIndex.find(_._1 == x).get._2
//        transitionLogDistribution._2(i)
      case BlockedWaitingForWord(_) | BlockedWaitingForWordWF() =>
        x match {
          case UnlockingOption(_) =>
            scalar(0f)
          case _=>
            throw new IllegalArgumentException
        }
    }

  lazy val transitionLogDistribution:(List[TransitionOption], Expression) =
    state match {
      case NormalParsing() =>
        var allOptions = new TransitionReduce(parserProperties, combinatorsContainer).currentTransOptions(this)
        if(stack.size >= maxStackSize && allOptions.size > 1){
          allOptions = allOptions.filter(_ != MoveToNextWordOption())
        }

        // not necessarily only holy
        val (holyOptions, unholyOptions) = allOptions.partition(allS2I.reduceOptions2i.contains)

        val holyOptionsI = holyOptions.map( allS2I.reduceOptions2i(_).toLong )
        var dist = neuralState.logSoftmaxTrans(h, targets = holyOptionsI)
        if(neuralState.locallyNormalized){
          dist = DyFunctions.logSoftmax(dist)
        }
        if(stack.size >= maxStackSize && allOptions == List(MoveToNextWordOption())){
          dist = vector(Array(-21f))
        }

        if(dropoutIsEnabled && unholyOptions.nonEmpty && Parser.include_unholy_transitions_with_zero_cost){
          //Training time so let the unholy transitions in
          (unholyOptions++holyOptions, concat(zeros(unholyOptions.size), dist))
        }else{
          (holyOptions, dist)
        }
      case Tagging() | TaggingWF(_) =>
        val taggingOptions = allS2I.taggingOptions2i.all_non_UNK_values
        var dist = neuralState.logSoftmaxTags(h)

        if(neuralState.locallyNormalized)
          dist = DyFunctions.logSoftmax(dist)

        (taggingOptions, dist)
      case RightAdjunction()            =>
        val allOptions = new TransitionRightAdjunction(parserProperties).currentTransOptions(this).asInstanceOf[List[RightAdjoinOption]]
        val allOptionsSize =  allOptions.size
        val tdEmbedder:Embedder[Int] = neuralState.embedderPositionTopDown
        val buEmbedder:Embedder[Int] = neuralState.embedderPositionBottomUp
        val nodeEmbs = allOptions.zipWithIndex.map{case (adjOption, i_top_down) =>
          val i_bottom_up = allOptionsSize - i_top_down
          val tdEmb:Expression = tdEmbedder.apply(i_top_down )
          val buEmb:Expression = buEmbedder.apply(i_bottom_up)
          val ndEmb:Expression = if(neuralState.isUseNodeContentInRevealing) adjOption.findNodeToAdjoinTo(stack.second).h else null
          concatWithNull(ndEmb, tdEmb, buEmb)
        }
        var dist = neuralState.attentionRightAdjunction.unnormalizedAlign(nodeEmbs, h)
        if(neuralState.locallyNormalized){
          dist = logSoftmax(dist)
        }
        (allOptions, dist)
      case BlockedWaitingForWord(_) | BlockedWaitingForWordWF()  =>
        (List(), null)
    }

  case class UnlockingOption(word:String) extends TransitionOption {
    override def apply(conf:Configuration): Configuration = throw new IllegalStateException
    override def equals(o: Any): Boolean =
      o.isInstanceOf[UnlockingOption] &&
      o.asInstanceOf[UnlockingOption].word == word
  }

  def isBlocked : Boolean = state match {
    case BlockedWaitingForWord(_)  => true
    case BlockedWaitingForWordWF() => true
    case _                         => false
  }

  def unblockWithWord(outsideRep:Expression, word:String) : Configuration = state match {
    case s@BlockedWaitingForWord(tag) =>
      val node = TerminalNode(word, tag)
      node.position = if(stack.size == 0) 0 else stack.first.span._2
      val newNeuralState = neuralState.encodeTerminal(node, s.outsideRepr)
      this.copy(
        lastTransitionOption = UnlockingOption(word),
        stack                = stack.push(node),
        state                = s.toNormalParsing(outsideRep),
        neuralState          = newNeuralState
      )
    case s@BlockedWaitingForWordWF() =>
      this.copy(
        lastTransitionOption = UnlockingOption(word),
        state = s.toTagging(word, outsideRep)
      )
    case _ =>
      throw new IllegalArgumentException
  }

}

object Configuration{

  def initConfig(
                  neuralStuff          : NeuralParameters,
                  parserProperties     : ParserProperties,
                  allS2I               : AllS2I,
                  combinatorsContainer : CombinatorsContainer,
                  outsideRepr          : Expression,
                  maxStackSize         : Int
                ) : Configuration = {
    Configuration(
      stack = neuralStuff.stackEncoder.empty,
      state = ConfigurationState.initState(outsideRepr)
    )(
      totalScoreExpression   = 0.0f,
      neuralState            = neuralStuff.initState,
      parserProperties       = parserProperties,
      allS2I                 = allS2I,
      combinatorsContainer   = combinatorsContainer,
      lastTransitionOption   = None,
      prevConf               = None,
      maxStackSize           = maxStackSize
    )
  }

}
