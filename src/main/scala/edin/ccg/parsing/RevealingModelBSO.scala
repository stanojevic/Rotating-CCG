package edin.ccg.parsing

import java.io.File

import edin.ccg.transitions.{Configuration, TransitionOption}
import edin.search.{BSO, PredictionState, RankingSearchOptimisation}

import scala.annotation.tailrec
import edin.ccg.{MainEvaluate, TrainInstance}
import edin.general.{IndexedInstance, ModelContainer, YamlConfig}
import edu.cmu.dynet.{Expression, ParameterCollection}
import ammonite.ops._
import edin.ccg.representation.tree.TreeNode
import edin.nn.DyFunctions._

/**
  * Beam Search Optimization (BSO) for the pretrained Revealing model
  * it can train ether:
  * 1) scaling of the locally normalized model or
  * 2) completely non-normalized model
  */
class RevealingModelBSO(originalModelDir : String ) extends ModelContainer[TrainInstance] {

  import RevealingModelBSO._

  private       var beamSizeValid : Int                                         = _
  private       var beamSizeTrain : Int                                         = _
  private       var trainingType  : String                                      = _
  private       var distance      : (PredictionState, PredictionState) => Float = _
  private  lazy val originalModel : RevealingModel                              = new RevealingModel()
  override lazy val model         : ParameterCollection                         = originalModel.model

  override val toSentence: Option[TrainInstance => List[String]] = originalModel.toSentence

  override def loadTrainData(dir: String): Iterator[TrainInstance] =
    originalModel loadTrainData dir

  override def filterTrainingData(trainData: Iterable[IndexedInstance[TrainInstance]]): Iterator[IndexedInstance[TrainInstance]] =
    originalModel filterTrainingData trainData

  override def prepareForTraining(trainData: Iterable[IndexedInstance[TrainInstance]]): Unit = {}

  override def validate(devData: Iterable[IndexedInstance[TrainInstance]]): (Double, Map[String, Double]) =
    originalModel validate devData

  override protected def saveExtra(modelDir: String): Unit =
    originalModel save s"$modelDir/real_model"

  override protected def loadExtra(modelDir: String): Unit =
    sys error "BANG BANG BANG ! ! ! you were not supposed to call this"

  private def loadOriginalModel() : Unit = {
    val tmpModelDir: Path = tmp()
    val cfg = YamlConfig.fromFile(s"$originalModelDir/hyperParams.yaml")
    val newCfg = trainingType match {
      case "global"    => cfg.replace("neural-parameters" :: "locally-normalized" :: false.asInstanceOf[AnyRef] :: Nil)
      case "rescaling" => cfg.replace("main-vars"         :: "learned-rescaling"  :: true .asInstanceOf[AnyRef] :: Nil)
      case _           => ???
    }
    rm(tmpModelDir)
    cp(Path(new File(originalModelDir).getAbsoluteFile), tmpModelDir)
    newCfg.save(s"$tmpModelDir/hyperParams.yaml")

    System.err.println("Loading original model START")
    originalModel.loadFromModelDir(tmpModelDir.toString())
    assert(originalModel.language=="General", "You can't train on search based objective if you can't produce all derivations")
    originalModel.validBeamKMidParsing = beamSizeValid
    originalModel.validBeamKOutParsing = beamSizeValid
    originalModel.validBeamKOutTagging = beamSizeValid
    originalModel.validBeamKOutWord    = beamSizeValid
    originalModel.validBeamType        = "simple"
    System.err.println("Loading original model END")
  }

  private type LossFunction = List[PredictionState] => Expression
  private var lossFunction: LossFunction = _

  override protected def defineModelFromHyperFile()(implicit model: ParameterCollection): Unit = {
    beamSizeTrain = hyperParams("bso")("beam-size-train").int
    beamSizeValid = hyperParams("bso")("beam-size-valid").int
    assert(beamSizeTrain>0)
    trainingType = hyperParams("bso")("training-type").str
    assert(Set("global", "rescaling") contains trainingType)
    distance = hyperParams("bso")("distance").str.toLowerCase match {
//      case "f-score" => distanceFscore
      case "mixed"   => distanceMixedScore
      case "one"     => distanceDummy
      case "ranking" => distanceRankingScore
    }
    lossFunction = hyperParams("bso")("update-method").str.toLowerCase match {
      case "early"   =>
        BSO.loss(_, beamSizeTrain, distanceMetric = distance, updateMethod = BSO.EarlyUpdate)
      case "laso"    =>
        BSO.loss(_, beamSizeTrain, distanceMetric = distance, updateMethod = BSO.LaSOUpdate)
      case "all"     =>
        BSO.loss(_, beamSizeTrain, distanceMetric = distance, updateMethod = BSO.AllUpdate)
      case "ranking" =>
        assert(hyperParams("bso")("distance").str.toLowerCase == "ranking")
        RankingSearchOptimisation.loss(_, beamSizeTrain, distanceMetric = distance)
      case _         =>
        ???
    }
    loadOriginalModel()
  }

  private def distanceFscore(sys:PredictionState, ref:PredictionState) : Float =
    1- fDepsAndTagsScore(sys, ref)._1

  private def distanceMixedScore(sys:PredictionState, ref:PredictionState) : Float = {
    val (fDepsScore, fTagScore) = fDepsAndTagsScore(sys, ref)
    (1-fDepsScore)/3 + (1-fTagScore)/3 + 1f/3
  }

  private def fDepsAndTagsScore(sys:PredictionState, ref:PredictionState) : (Float, Float) = {
    val sysConf = sys.unwrapState[ParserState].conf
    val refConf = ref.unwrapState[ParserState].conf
    if(sysConf.stack.nonEmpty){
      val sysTree = sys.unwrapState[ParserState].conf.extractTree
      val refTree = ref.unwrapState[ParserState].conf.extractTree
      val fDepsScore = MainEvaluate.computeLabelledDepsFscore(gold = refTree, pred = sysTree).toFloat
      val fTagScore  = MainEvaluate.computeSupertagFscore(    gold = refTree, pred = sysTree).toFloat
      (fDepsScore, fTagScore)
    }else if(refConf.stack.nonEmpty){
      (0f, 0f)
    }else{
      (1f, 1f)
    }
  }

  private def distanceRankingScore(sys:PredictionState, ref:PredictionState) : Float = {
    val (fDepsScore, fTagScore) = fDepsAndTagsScore(sys, ref)

    val sysTrans = extractTransitions(sys.unwrapState[ParserState].conf)
    val refTrans = extractTransitions(ref.unwrapState[ParserState].conf)
    val transOverlap = (sysTrans zip refTrans).takeWhile{case (s, r) => s == r}
    val pTrans = transOverlap.size.toFloat/sysTrans.size
    val rTrans = transOverlap.size.toFloat/refTrans.size
    val fTrans = 2*pTrans*rTrans/(pTrans+rTrans)

    (1-fDepsScore)/3 + (1-fTagScore)/3 + (1-fTrans)
  }

  private def extractTransitions(conf: Configuration) : List[TransitionOption] = conf.prevConf match {
    case Some(prevConf) => extractTransitions(prevConf) :+ conf.lastTransitionOption.get
    case None           => Nil
  }

  private def distanceDummy(sys:PredictionState, ref:PredictionState) : Float = 1f

  override def prepareForEpoch(trainData: Iterable[IndexedInstance[TrainInstance]], epoch: Int): Unit = trainingEnd()

  override def trainingEnd(): Unit =
    if(trainingType == "rescaling"){
      val scalings = parameter(originalModel.scalingWeights).toArray
      val tagWeight = scalings(0)
      val wordWeight = scalings(1)
      val parseWeight = scalings(2)
      System.err.println(s"scaling weights (parse, tag, word) = ( $parseWeight,\t$tagWeight,\t$wordWeight )")
    }

  override def computeLoss(instance: IndexedInstance[TrainInstance]): Expression = {
    val tree         = instance.instance
    val words        = tree.words

    val parserState = Parser.initParserState(words, Int.MaxValue)(originalModel)
    val scalingParams = if(originalModel.isLearnedRescaled) Some(Expression.parameter(originalModel.scalingWeights)) else None
    val rescaledParserState = RescaledParserState(parserState, scalingParams)

    val transitions = originalModel.parserProperties.findDerivation(tree)

    val goldSequence = extractGoldStates(transitions, rescaledParserState::Nil)

    disableAllDropout()
    val l = lossFunction(goldSequence)
    enableAllDropout()
    l
  }

}

object RevealingModelBSO{

  def computeTreeScore(tree:TreeNode)(model:RevealingModel) : Expression = {
    val transitions         = model.parserProperties.findDerivation(tree)
    val words               = tree.words
    val parserState         = Parser.initParserState(words, Int.MaxValue)(model)
    val scalingParams       = if(model.isLearnedRescaled) Some(Expression.parameter(model.scalingWeights)) else None
    val rescaledParserState = RescaledParserState(parserState, scalingParams)
    val goldSequence        = extractGoldStates(transitions, rescaledParserState::Nil)
    goldSequence.last.scoreExp
  }

  @tailrec
  private def extractGoldStates(transitions: Seq[TransitionOption], acc:List[PredictionState]) : List[PredictionState] =
    if(transitions.isEmpty){
      acc.reverse
    }else{
      val conf = acc.head.unwrapState[ParserState].conf
      if(conf.state.isBlocked){
        assert(acc.head.nextActionLogProbsTotalValues.size == 1)
        extractGoldStates(transitions, acc.head.applyAction(0) :: acc)
      }else{
        val t = transitions.head
        val a = conf.transitionLogDistribution._1.zipWithIndex.find(_._1==t).get._2
        val newState = acc.head.applyAction(a)
        extractGoldStates(transitions.tail, newState :: acc)
      }
    }

}
