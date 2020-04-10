package edin.ccg.parsing

import edin.algorithms.MeanStdIncrementalState
import edin.ccg.representation.tree.TreeNode
import edin.ccg.{MainEvaluate, TrainInstance}
import edin.general.{IndexedInstance, ModelContainer}
import edin.search.NeuralSampler
import edu.cmu.dynet.{Expression, ParameterCollection}

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}
import edin.nn.DyFunctions._

class RevealingModelReinforce( originalModelDir : String ) extends ModelContainer[TrainInstance] {

  System.err.println("Loading original model START")
  private val originalModel : RevealingModel = new RevealingModel()
  originalModel.loadFromModelDir(originalModelDir)

  System.err.println("Loading original model END")

  override val negLossIsOk: Boolean = true

  override lazy val model: ParameterCollection = originalModel.model

  override val toSentence: Option[TrainInstance => List[String]] = originalModel.toSentence

  override def loadTrainData(dir: String): Iterator[TrainInstance] =
    originalModel.loadTrainData(dir)

  override def filterTrainingData(trainData: Iterable[IndexedInstance[TrainInstance]]): Iterator[IndexedInstance[TrainInstance]] =
    originalModel.filterTrainingData(trainData)

  override def prepareForTraining(trainData: Iterable[IndexedInstance[TrainInstance]]): Unit = {}

  private var samples     : Int     = _
  private var useBaseline : Boolean = _
  private var includeGold : Boolean = _
  private var alpha       : Double  = _

  override protected def defineModelFromHyperFile()(implicit model: ParameterCollection): Unit = {
    samples       = hyperParams("reinforce")("samples").int
    useBaseline   = hyperParams("reinforce")("use-baseline").bool
    includeGold   = hyperParams("reinforce")("include-gold").bool
    alpha         = hyperParams("reinforce")("alpha").float
    val beamSizeValid = hyperParams("reinforce")("beam-size-valid").int
    originalModel.validBeamKMidParsing = beamSizeValid
    originalModel.validBeamKOutParsing = beamSizeValid
    originalModel.validBeamKOutTagging = beamSizeValid
    originalModel.validBeamKOutWord    = beamSizeValid
    originalModel.validBeamType        = "simple"
    assert(samples>0 || includeGold)
  }

  override def validate(devData: Iterable[IndexedInstance[TrainInstance]]): (Double, Map[String, Double]) =
    originalModel.validate(devData)

  override protected def saveExtra(modelDir: String): Unit =
    originalModel.save(modelDir+"/real_model")

  override protected def loadExtra(modelDir: String): Unit =
    sys.error("you were not supposed to call this")

  @tailrec
  private def produceDiscSample(words:List[String]) : (Expression, TreeNode) = {
    assert(originalModel.isDiscriminative)
    val discParserState = Parser.initParserState(words, maxStackSize = Int.MaxValue)(originalModel)
    val (predictedPaserState, _, _) = NeuralSampler.sample(discParserState, maxExpansion = words.size*10)
    val conf = predictedPaserState.unwrapState[ParserState].conf
    Try(conf.extractTree) match {
      case Success(tree) =>
        (predictedPaserState.scoreExp, tree)
      case Failure(_) =>
        produceDiscSample(words)
    }
  }

  private def computeCost(gold:TreeNode, pred:TreeNode) : Double =
    - MainEvaluate.computeLabelledDepsFscore(gold = gold, pred = pred)

  private var costStats = MeanStdIncrementalState()

  private def renormalizeCosts(costs:List[Double]) : List[Double] = {
    for(cost <- costs)
      costStats = costStats.addNewPoint(cost)
    if(costStats.stdDev > 0)
      costs.map(x => (x-costStats.mean)/costStats.stdDev)
    else
      costs
  }

  override def computeLoss(instance: IndexedInstance[TrainInstance]): Expression = {
    val goldTree = instance.instance

    val words = goldTree.words

    var samples:List[(Expression, TreeNode)] = (0 until this.samples).toList.map(_ => produceDiscSample(words))

    if(includeGold)
      samples ::= (Parser.logProb(goldTree)(model = originalModel), goldTree)

    var costs = samples.map{case (_, tree) => computeCost(gold = goldTree, pred = tree)}

    if(useBaseline)
      costs = renormalizeCosts(costs)

    (samples zip costs).map{ case ((logProb, _), cost) => logProb*cost}.esum
  }

}
