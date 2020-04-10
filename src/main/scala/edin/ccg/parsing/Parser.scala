package edin.ccg.parsing

import java.io.{File, PrintWriter}

import edin.ccg.representation.combinators.{CombinatorBinary, CombinatorUnary}
import edin.ccg.representation.tree._
import edin.ccg.transitions.ParsingException
import edin.ccg.transitions._
import edin.nn.DyFunctions._
import edin.nn.embedder.SequenceEmbedderGlobal
import edin.nn.sequence.{BiRNN, MultiRNN}
import edin.search._
import edu.cmu.dynet.Expression

import scala.util.{Failure, Success, Try}

object Parser{

  var include_unholy_transitions_with_zero_cost = false

  def parse(sent:List[String], beamType:String, beamRescaled:Boolean, kMidParsing:Int, kOutParsing:Int, kOutWord:Int, kOutTagging:Int, maxStackSize:Int)(models:List[RevealingModel]) : TreeNode =
    parseKbest(
      sent = sent,
      beamType = beamType,
      beamRescaled = beamRescaled,
      kMidParsing = kMidParsing,
      kOutParsing = kOutParsing,
      kOutWord = kOutWord,
      kOutTagging = kOutTagging,
      maxStackSize = maxStackSize
    )(models).head._1

  private def searcherForModel(
                        model             : RevealingModel,
                        sent              : List[String],
                        beamType          : String,
                        kMidParsing       : Int,
                        kOutParsing       : Int,
                        kOutWord          : Int,
                        kOutTagging       : Int,
                      ) : List[PredictionState] => List[PredictionState] = beamType match {
    case "simple" =>
      require(kMidParsing == kOutParsing)
      new NeuralBeamSimple(kMidParsing   ).search
    case "original" =>
      require(kMidParsing == kOutParsing)
      new NeuralBeamStandard(
        beamSize               = kMidParsing   ,
        maxExpansion           = sent.size*6   ,
        stopWhenBestKfinished  = kMidParsing   ,
        earlyStop              = false
      ).search
    case "wordSync" =>
      new WordSynchronisedSearch(
        kMidParsing = kMidParsing,
        kOutParsing = kOutParsing,
        kOutTagging = kOutTagging,
        kOutWord    = kOutWord
      ).search
    case _ =>
      ???
  }

  def parseKbest(sent:List[String], beamType:String, beamRescaled:Boolean, kMidParsing:Int, kOutParsing:Int, kOutWord:Int, kOutTagging:Int, maxStackSize:Int)(models:List[RevealingModel]) : List[(TreeNode, Double)] = {
    var ensambleState: PredictionState = new EnsamblePredictionState( models map initParserState(sent, maxStackSize) )
    if(beamRescaled)
      ensambleState = RescaledParserState(ensambleState, if(models.head.isLearnedRescaled) Some(Expression.parameter(models.head.scalingWeights)) else None)
    val searcher = searcherForModel(
      model        = models.head,
      sent         = sent,
      beamType     = beamType,
      kMidParsing  = kMidParsing,
      kOutParsing  = kOutParsing,
      kOutWord     = kOutWord,
      kOutTagging  = kOutTagging
    )
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

  private def initVectorAndEmbeddings(words:List[String])(model: RevealingModel) : (Expression, List[Expression]) = {
    val encoder = model.sequenceEmbedder.asInstanceOf[SequenceEmbedderGlobal[String]]
    val embs    = encoder transduce words
    val init    = encoder.rnn match {
      case _ : MultiRNN                              => encoder.zeros
      case _ : BiRNN if ConfigurationState.TAG_FIRST => embs.head/2 + embs.last/2
      case _ : BiRNN                                 => encoder.zeros
      case _                                         => ???
    }
    (init, embs)
  }

  def initParserState( words:List[String], maxStackSize:Int)(model: RevealingModel) : ParserState = {
    val (initVector, embs) = initVectorAndEmbeddings(words)(model)

    new ParserState(
      conf = Configuration.initConfig(
        neuralStuff          = model.neuralParameters,
        parserProperties     = model.parserProperties,
        allS2I               = model.allS2I,
        combinatorsContainer = model.combinatorsContainer,
        outsideRepr          = initVector,
        maxStackSize         = maxStackSize
      ),
      constrainedInfo = Some(ConstrainedInfo(
        wordsLeftover = words,
        embsLeftover  = embs,
      )),
      generativeInfo  =
        if (model.isGenerative)
          Some(GenerativeInfo(
            wordEmissionTotalScoreExp = scalar(0f),
            logSoftmax = model.vocabularyLogSoftmax
          ))
        else
          None
    )
  }

  def logProb(origTree:TreeNode)(model:RevealingModel) : Expression = {
    include_unholy_transitions_with_zero_cost = true
    var words = origTree.words
    var (initVector, embs) = initVectorAndEmbeddings(words)(model)

    val deriv: List[TransitionOption] = model.parserProperties.findDerivation(origTree)

    var currConf = Configuration.initConfig(
      neuralStuff          = model.neuralParameters,
      parserProperties     = model.parserProperties,
      allS2I               = model.allS2I,
      combinatorsContainer = model.combinatorsContainer,
      outsideRepr          = initVector,
      maxStackSize         = Int.MaxValue
    )
    var logProb = scalar(0)

    var derivLeftover = deriv
    lossComputationCage(origTree, model.parserProperties.prepareDerivationTreeForTraining(origTree)){
      while(derivLeftover.nonEmpty){

        // blocked state so we need to add a word
        if(currConf.isBlocked){
          if(model.isGenerative)
            logProb += model.vocabularyLogSoftmax.computeWordLogProbTrainingTime(currConf.h, words.head)
          currConf = currConf.unblockWithWord(
            outsideRep = embs.head,
            word       = words.head
          )
          words = words.tail
          embs  = embs.tail
        }

        // next gold action
        val option = derivLeftover.head

        // adding logprob of best action
        option match {
          case t:TaggingOption if ! model.allS2I.taggingOptions2i.contains(t) => // ignore score of tags that are not frequent enough
            currConf = option(currConf)
          case BinaryReduceOption(c) if ! CombinatorBinary.isPredefined(c) => // ignore score of binary combinators that are not predefined
            currConf = option(currConf)
          case UnaryReduceOption(c)  if ! CombinatorUnary.isPredefined(c) => // ignore score of unary combinators that are not predefined
            currConf = option(currConf)
          case _ => // this is a normal state
            logProb += currConf.lookupLogProb(option)
            currConf = option(currConf)
        }

        // move to next derivation step
        derivLeftover = derivLeftover.tail
      }
//      if(model.isGenerative){
//        logProb += currConf.lookupLogProb(MoveToTaggingOption())
//        currConf = currConf.transitionLogDistribution._1.find(_==MoveToTaggingOption()).get.apply(currConf)
//        logProb += model.vocabularyLogSoftmax.computeWordLogProbTrainingTime(currConf.h, model.vocabularyLogSoftmax.s2i.EOS_i)
//      }
    }
    include_unholy_transitions_with_zero_cost = false

    logProb
  }

  def loss(origTree:TreeNode)(model:RevealingModel) : Expression = -logProb(origTree)(model)

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
        System.err.println("ERROR COMPUTING LOSS PARSING EXCEPTION ! ! !\n"*3)
        System.err.println("SKIPPING!!!")
        System.err.println(msg)
        if(left!=null)
          left.saveVisual(s"error_$savedErrors/left", "left")
        right.saveVisual(s"error_$savedErrors/right", "right")
        // origTree.saveVisual(s"error_$savedErrors/original_tree", "original")
        // transformed.saveVisual(s"error_$savedErrors/transformed_tree", "transformed")
        savedErrors += 1
      case e:Exception =>
        System.err.println("ERROR COMPUTING LOSS ! ! !\n"*3)
        System.err.println("SKIPPING!!!")
        new File(s"error_$savedErrors").mkdir()
        origTree.saveVisual(s"error_$savedErrors/origTree", "origTree")
        transTree.saveVisual(s"error_$savedErrors/transTree", "transTree")
        val pw = new PrintWriter(s"error_$savedErrors/message")
        pw.println(e)
        e.printStackTrace(pw)
        pw.close()
        savedErrors += 1

    }
  }


}

