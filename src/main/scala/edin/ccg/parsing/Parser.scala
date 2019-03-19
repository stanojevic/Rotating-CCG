package edin.ccg.parsing

import java.io.{File, PrintWriter}

import edin.ccg.representation.combinators.{CombinatorBinary, CombinatorUnary}
import edin.ccg.representation.tree._
import edin.ccg.transitions.ParsingException
import edin.ccg.transitions._
import edin.nn.DyFunctions._
import edin.search._
import edu.cmu.dynet.Expression

import scala.util.{Failure, Success, Try}

object Parser{

  def parse(sent:List[String], parsingBeamSize:Int, wordBeamSize:Int, fastTrackBeamSize:Int)(models:List[RevealingModel]) : TreeNode =
    parseMany(sent, parsingBeamSize, wordBeamSize, fastTrackBeamSize)(models).head._1

  def searcherForModel(model:RevealingModel, sent:List[String], parsingBeamSize:Int, wordBeamSize:Int, fastTrackBeamSize:Int) : List[PredictionState] => List[PredictionState] = {
    val beam = if(wordBeamSize > 0){
      new NeuralBeamWordSynchronous(
        k = parsingBeamSize,
        k_wd = wordBeamSize,
        k_ft = fastTrackBeamSize,
        maxWordExpansion       = sent.size*6,
        stopWhenBestKfinished  = 1
      )
    }else{
      new NeuralBeamStandard(
        beamSize               = parsingBeamSize,
        maxExpansion           = sent.size*6,
        stopWhenBestKfinished  = 1,
        earlyStop              = false
      )
    }
    if(model.isDiscriminative){
      beam.search
    }else{
      val w2i = model.allS2I.w2i_tgt_gen
      val surfaceActions = sent.map(w2i(_)) :+ w2i.EOS_i
      beam.searchConstrained(_, surfaceActions)
    }
  }

  def parseMany(sent:List[String], parsingBeamSize:Int, wordBeamSize:Int, fastTrackBeamSize:Int)(models:List[RevealingModel]) : List[(TreeNode, Double)] = {
    assert(models.head.isDiscriminative != (wordBeamSize > 0) )
    assert((fastTrackBeamSize <= 0) || (wordBeamSize > 0))
    val ensambleState = new EnsamblePredictionState(states = models.map{initParserState(sent)})
    val searcher = searcherForModel(models.head, sent, parsingBeamSize, wordBeamSize, fastTrackBeamSize)
    val finalEnsambleState = searcher(ensambleState::Nil)
    finalEnsambleState.map{x =>
      val conf = x.unwrapState[ParserState].conf
      val tree = Try(conf.extractTree) match {
        case Success(value) =>
          value
        case Failure(exception) =>
          conf.saveVisualStackState("parsing_failure")
          throw exception
      }
      (tree, x.score.toDouble)
    }
  }

  def initParserState( words:List[String])(model: RevealingModel) : ParserState = {
    new ParserState(
      aConf = Configuration.initConfig(
        neuralStuff          = model.neuralParameters,
        parserProperties     = model.parserProperties,
        allS2I               = model.allS2I,
        combinatorsContainer = model.combinatorsContainer,
        outsideRepr          = model.sequenceEmbedder.zeros
      ),
      aConstrainedInfo = Some(ConstrainedInfo(
        wordsLeftover = words,
        embsLeftover  = model.sequenceEmbedder.transduce(words),
      )),
      generativeInfo  = if (model.isDiscriminative) {
        None
      } else {
        Some(GenerativeInfo(
          isEOS = false,
          wordEmissionTotalScoreExp = scalar(0f),
          logSoftmax = model.vocabularyLogSoftmax
        ))
      }
    )
  }

  def loss(origTree:TreeNode)(model:RevealingModel) : Expression = {

    var words = origTree.words
    var embs = model.sequenceEmbedder.transduce(words)

    val deriv: List[TransitionOption] = model.parserProperties.findDerivation(origTree)

    var currConf = Configuration.initConfig(
                                             neuralStuff          = model.neuralParameters,
                                             parserProperties     = model.parserProperties,
                                             allS2I               = model.allS2I,
                                             combinatorsContainer = model.combinatorsContainer,
                                             outsideRepr          = model.sequenceEmbedder.zeros
                                           )
    var logProb = scalar(0)

    var derivLeftover = deriv
    lossComputationCage(origTree, model.parserProperties.prepareDerivationTreeForTraining(origTree)){
      while(derivLeftover.nonEmpty){

        // blocked state so we need to add a word
        if(currConf.isBlocked){
          if(!model.isDiscriminative)
            logProb += model.vocabularyLogSoftmax.computeWordLogProbTrainingTime(currConf.h, words.head)
          currConf = currConf.unblockWithWord(
            outsideRep = embs.head,
            word       = words.head,
            isFinal    = model.isDiscriminative && words.tail.isEmpty
          )
          words = words.tail
          embs = embs.tail
        }

        // next gold action
        val option = derivLeftover.head

        // adding logprob of best action
        option match {
          case _:RightAdjoinOption =>
            logProb += currConf.lookupLogProb(option)
            currConf = currConf.transitionLogDistribution._1.find(_==option).get.apply(currConf)
          case t:TaggingOption if ! model.allS2I.taggingOptions2i.contains(t) =>
            currConf = option(currConf)
          case BinaryReduceOption(c) if ! CombinatorBinary.isPredefined(c) =>
            currConf = option(currConf)
          case UnaryReduceOption(c)  if ! CombinatorUnary.isPredefined(c) =>
            currConf = option(currConf)
          case _ =>
            logProb += currConf.lookupLogProb(option)
            currConf = option(currConf)
        }

        // move to next derivation step
        derivLeftover = derivLeftover.tail
      }
      if(!model.isDiscriminative){
        logProb += currConf.lookupLogProb(ShiftOption())
        currConf = currConf.transitionLogDistribution._1.find(_==ShiftOption()).get.apply(currConf)
        logProb += model.vocabularyLogSoftmax.computeWordLogProbTrainingTime(currConf.h, model.vocabularyLogSoftmax.s2i.EOS_i)
      }
    }

    -logProb
  }

  ///////////////////////////////////////////////////////
  //////// This is for catching any potential errors
  ///////////////////////////////////////////////////////
  private var savedErrors = 0
  private def lossComputationCage(origTree: => TreeNode, transTree: => TreeNode)(f: => Unit) : Unit = {
    try{
      f
    }catch{
      case ParsingException(msg, left, right) =>
        new File(s"error_$savedErrors").mkdir()

        val pw = new PrintWriter(s"error_$savedErrors/message")
        pw.println(msg)
        pw.close()

        System.err.println()
        System.err.println("SKIPPING!!!")
        System.err.println(msg)
        if(left!=null)
          left.saveVisual(s"error_$savedErrors/left", "left")
        right.saveVisual(s"error_$savedErrors/right", "right")
        // origTree.saveVisual(s"error_$savedErrors/original_tree", "original")
        // transformed.saveVisual(s"error_$savedErrors/transformed_tree", "transformed")
        savedErrors += 1
      case e:Exception =>
        new File(s"error_$savedErrors").mkdir()
        origTree.saveVisual(s"error_$savedErrors/origTree", "origTree")
        transTree.saveVisual(s"error_$savedErrors/transTree", "transTree")
        val pw = new PrintWriter(s"error_$savedErrors/message")
        pw.println(e)
        pw.close()
        savedErrors += 1

    }
  }


}

