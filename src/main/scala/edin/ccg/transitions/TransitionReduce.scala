package edin.ccg.transitions

import edin.ccg.representation._
import edin.ccg.representation.category.{Category, Functor}
import edin.ccg.representation.hockendeps.HockenCatHelper
import edin.ccg.representation.combinators._
import edin.ccg.representation.transforms.{AdjunctionGeneral, Rebranching, RightSpine}
import edin.ccg.representation.tree._

sealed case class BinaryReduceOption(comb:CombinatorBinary) extends TransitionOption {

  def apply(conf:Configuration): Configuration = (conf: @unchecked) match {
    case Configuration(stack, NormalParsing(_)) =>
      val left = stack.second
      val right = stack.first
      val newNode = BinaryNode(comb, left, right)

      val newNodeTransformed = Rebranching.sinkForwardRightward(
         Rebranching.sinkForwardRightward(
           Rebranching.sinkForwardRightward(
             newNode
           )
         )
      )

      conf.neuralState.refreshNodeRepresentation(newNode)(newNodeTransformed)

      conf.copy(
        lastTransitionOption = this,
        stack = stack.pop.pop.push(newNodeTransformed),
        neuralState = conf.neuralState.addDependenciesForNode(newNodeTransformed)
      )
  }

}

sealed case class UnaryReduceOption(comb:CombinatorUnary) extends TransitionOption {

  def apply(conf:Configuration): Configuration = (conf : @unchecked) match {
    case Configuration(stack, NormalParsing(_)) =>
      val const = stack.first
      val newNode = UnaryNode(comb, const)
      val newNodeTransformed = Rebranching.sinkForwardRightward(newNode)

      conf.neuralState.refreshNodeRepresentation(newNode)(newNodeTransformed)

      conf.copy(
        lastTransitionOption = this,
        stack = stack.pop.push(newNodeTransformed)
      )
  }

}

sealed case class RevealingOption() extends TransitionOption {

  def apply(conf:Configuration): Configuration = conf.state match {
    case s@NormalParsing(_) =>
      conf.copy(
        lastTransitionOption = this,
        state = s.toRightAdjunction
      )
    case _ => throw new RuntimeException
  }

}

sealed case class ShiftOption() extends TransitionOption {

  def apply(conf:Configuration): Configuration = conf.state match {
    case s@NormalParsing(ShiftIncluded) =>
      conf.copy(
        lastTransitionOption = this,
        state = s.toBlocked
      )
    case _ =>
      throw new IllegalArgumentException
  }

}

class TransitionReduce(
                        parserProperties: ParserProperties,
                        combinatorsContainer: CombinatorsContainer
                      ) extends TransitionController {

  override def currentTransOptions(conf: Configuration): List[TransitionOption] = {
    conf match {
      case Configuration(stack, NormalParsing(shifting)) =>
        val container = combinatorsContainer

        var options = List[TransitionOption]()

        shifting match {
          case ShiftIncluded => options ::= ShiftOption()
          case ShiftExcluded =>
        }

        if(stack.size >= 1){
          val top = stack.first
          var combs = container.unaryLookup(top.category)
          if(parserProperties.withObservedCatsOnly)
            combs = combs.filter{c => conf.allS2I.cat2i contains c(top.category)}
          if(! lowTypeRaiseNormalFormOk(conf))
            combs = combs.filterNot(_.isInstanceOf[TypeRaiser])
          options ++= combs.map( UnaryReduceOption )
        }

        if(stack.size >= 2 && puncNormalFormOk(conf)){
          val left = stack.second
          val lCat = left.category
          val right = stack.first
          val rCat = right.category

          var combsOkBinary = List[CombinatorBinary]()

          combsOkBinary ++= container.forwardLookup(lCat, rCat).
                                      filter(forwardEisnerNFOk(_, conf)).
                                      filter(forwardAntiEisnerNFOk(_, conf)).
                                      filter(lowLeftAdjNFOk(_, conf))
          combsOkBinary ++= container.puncLookup(   lCat, rCat)
          if(highTypeRaiseNormalFormOk(conf)){
            combsOkBinary ++= container.conjLookup( lCat, rCat)
          }

          if(parserProperties.useRevealing) {
            combsOkBinary ++= container.backwardAndCrossedNonRightAdjunctionLookup(lCat, rCat).filter(backwardEisnerNFOk(_, conf))

            if(RightSpine.extractRightAdjunctionCandidatesOfficial(left, rCat, parserProperties.revealingDepth, parserProperties.revealingDepth).nonEmpty){
              options ::= RevealingOption()
            }

//            if(rCat.isBackSlashAdjunctCategory || rCat.isInstanceOf[ConjCat]){
//              val rightSpine = RightSpine.rightSpineAll(left)
//
//              if(rightSpine.exists{leftNode =>
//                container.backwardAndCrossedRightAdjunctionLookup(leftNode.category, rCat).exists{ comb =>
//                  ! parserProperties.withHockenCatNormal ||
//                    HockenCatHelper.binaryCombine(comb, leftNode, right).isDefined
//                }
//              }){
//                options ::= RevealingOption()
//              }
//            }
          }else{
            combsOkBinary ++= container.backwardAndCrossedLookup(lCat, rCat).filter(backwardEisnerNFOk(_, conf)).filter(lowRightAdjNFOk(_, conf))
          }

          combsOkBinary ++= container.unholyLookup( lCat, rCat)

          if(parserProperties.withObservedCatsOnly)
            combsOkBinary = combsOkBinary.filter(c => conf.allS2I.cat2i contains c(lCat, rCat))

          if(parserProperties.withHockenCatNormal)
            combsOkBinary = combsOkBinary.filter{ comb => HockenCatHelper.binaryCombine(comb, left, right).isDefined }

          options ++= combsOkBinary.map(BinaryReduceOption)
        }

        options
      case _ => List()
    }
  }

  private def lowLeftAdjNFOk(topc:CombinatorBinary, configuration: Configuration) : Boolean =
    if(!configuration.parserProperties.withLowLeftAdjNF){
      true
    }else{
      val l = configuration.stack.second
      val r = configuration.stack.first
      if(topc.isLeftAdjCombinator(l.category, r.category)){
        ! AdjunctionGeneral.isRightAdjunctionPlaceWithoutConjunction(r)
      }else{
        true
      }
    }

  private def lowRightAdjNFOk(topc:CombinatorBinary, configuration: Configuration) : Boolean = {
    if(!configuration.parserProperties.withLowRightAdjNF){
      true
    }else{
      val l = configuration.stack.second
      val r = configuration.stack.first
      r match {
        case BinaryNode(Conjunction(), _, _) => true
        case _ =>
          if(topc.isRightAdjCombinator(l.category, r.category)){
            ! AdjunctionGeneral.isLeftAdjunctionPlace(l)
          }else{
            false
          }
      }
    }
  }

  private def forwardAntiEisnerNFOk(high:CombinatorBinary, configuration: Configuration) : Boolean =
    if(!configuration.parserProperties.withForwardAntiEisnerNF)
      true
    else
      (high, configuration.stack.first) match {
        case (high:Forwards, BinaryNode(low:Forwards, _, _)) =>
          val x = high.order
          val y = low.order
          val m = Forwards.maxB
          x < y || x-y >= m || {configuration.stack.second.category match {
            case Functor(_, _, a) if a matches Category.NP => true
            case _ => false
          }}
        case _ =>
          true
      }


  /** prevents backward application */
  private def backwardEisnerNFOk(highc:CombinatorBinary, configuration: Configuration) : Boolean =
    if(!configuration.parserProperties.withBackwardEisnerNF)
      true
    else
      (highc, configuration.stack.first) match {
        case (highc:Backwards, BinaryNode(bottomc:Backwards, _, _)) =>
          // bottomc.order == 0   /// this should be more complicated to include a bound on the max combinator order
          // in other words =>   bottomc.order == 0 || highc.order + bottomc.order - 1 > maxc
          bottomc.order == 0 || highc.order + bottomc.order - 1 > Forwards.maxB
        case _ =>
          true
      }

  /** prevents forward application */
  private def forwardEisnerNFOk(highc:CombinatorBinary, configuration: Configuration) : Boolean =
    if(!configuration.parserProperties.withForwardEisnerNF)
      true
    else
      (highc, configuration.stack.second) match {
        case (highc:Forwards, BinaryNode(bottomc:Forwards, _, _)) =>
          // bottomc.order == 0   /// this should be more complicated to include a bound on the max combinator order
          // in other words =>   bottomc.order == 0 || highc.order + bottomc.order - 1 > maxc
          bottomc.order == 0 || highc.order + bottomc.order - 1 > Forwards.maxB
        case _ =>
          true
      }

  /** prevents conjunction */
  private def highTypeRaiseNormalFormOk(configuration: Configuration) : Boolean =
    if(!configuration.parserProperties.withHighTRconjNF){
      true
    }else{
      configuration.stack.first match {
        case BinaryNode(Conjunction(), _, UnaryNode(c, _)) if c.isInstanceOf[TypeRaiser] => false
        case _ => true
      }
    }

  /** prevents unary TR */
  private def lowTypeRaiseNormalFormOk(configuration: Configuration) : Boolean =
    if(!configuration.parserProperties.withLowTRconjNF){
      true
    }else{
      configuration.stack.first match {
        case BinaryNode(B0bck(), _, BinaryNode(Conjunction(), _, _)) => false
        case _ => true
      }
    }

  private def puncNormalFormOk(configuration: Configuration) : Boolean =
    if(! configuration.parserProperties.withPuncNF){
      true
    }else{
      val span = configuration.stack.first.span
      if(span._2-span._1 == 1){
        true
      }else{
        ! RightSpine.rightSpineAll(configuration.stack.first).exists{
          case BinaryNode(RemovePunctuation(false), _, _) =>
            true
          case _ =>
            false
        }
      }
    }

  override val allPossibleTransOptions: List[TransitionOption] =
      parserProperties.reduceOptions(combinatorsContainer)

}


