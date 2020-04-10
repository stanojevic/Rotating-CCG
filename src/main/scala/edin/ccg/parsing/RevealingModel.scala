package edin.ccg.parsing

import java.io.PrintWriter

import edin.algorithms.evaluation.LogProbAggregator
import edin.algorithms.Math
import edin.ccg.representation._
import edin.ccg.transitions._
import edin.ccg._
import edin.ccg.representation.category.Category
import edin.ccg.representation.combinators.{Combinator, TypeChangeUnary}
import edin.ccg.representation.tree._
import edin.general._
import edin.nn.DynetSetup
import edin.nn.embedder._
import edin.nn.layers.{VocLogSoftmax, VocLogSoftmaxConfig}
import edu.cmu.dynet.{Dim, Expression, Parameter, ParameterCollection}

import scala.util.Try

class RevealingModel(
                  var language       : String  = null,
                      embeddingsFile : String  = null,
                      embeddingsDim  : Int     = -1,
                      lowercased     : Boolean = false
                    ) extends ModelContainer[TrainInstance] {

  var currentlyProcessing: IndexedInstance[TrainInstance] = _

  var sequenceEmbedder       : SequenceEmbedderGeneral[String] = _
  var combinatorsContainer   : CombinatorsContainer            = _
  var allS2I                 : AllS2I                          = _
  var parserProperties       : ParserProperties                = _
  var neuralParameters       : NeuralParameters                = _
  var isDiscriminative       : Boolean                         = false
  def isGenerative           : Boolean                         = ! isDiscriminative
  var isLearnedRescaled      : Boolean                         = _
  var vocabularyLogSoftmax   : VocLogSoftmax[String]           = _

  var validBeamKMidParsing   : Int                             = 1
  var validBeamKOutWord      : Int                             = 1
  var validBeamRescaled      : Boolean                         = true
  var validBeamType          : String                          = _
  var validBeamKOutParsing   : Int                             = 1
  var validBeamKOutTagging   : Int                             = 1
  var scalingWeights         : Parameter                       = _

  override val toSentence: Option[TrainInstance => List[String]] = Some(_.words)

  override protected def hyperParamTransformPermanently(hyperParam: YamlConfig): YamlConfig = {
    val dim = Option(embeddingsFile) match {
      case Some(file) => EmbedderStandard.pretrainedEmb_loadDim(file)
      case None       => embeddingsDim
    }

    hyperParam.mapAllTerminals(Map[String, AnyRef]( elems=
      "RESOURCE_LANGUAGE"     -> this.language,
      "RESOURCE_EMB_DIM"      -> dim.asInstanceOf[AnyRef],
    ))
  }

  override protected def defineModelFromHyperFile()(implicit model: ParameterCollection): Unit = {

    scalingWeights           = model.addParameters(Dim(3))

    val config:YamlConfig = hyperParams.mapAllTerminals(Map[String, AnyRef](elems =
      "RESOURCE_C2I"                -> allS2I.c2i,
      "RESOURCE_W2I_TGT_GENERATION" -> allS2I.w2i_tgt_gen,
      "RESOURCE_W2I_TGT_EMBED"      -> allS2I.w2i_tgt_embed,
      "RESOURCE_CAT2I"              -> allS2I.cat2i,
      "RESOURCE_COMB2I"             -> allS2I.comb2i,
      "RESOURCE_TAGS_SIZE"          -> allS2I.taggingOptions2i.size.asInstanceOf[AnyRef],
      "RESOURCE_TRANS_SIZE"         -> allS2I.reduceOptions2i.size.asInstanceOf[AnyRef],
      "RESOURCE_EMB_LOC"            -> embeddingsFile
    ))

    parserProperties = ParserProperties.fromYaml(hyperParams("parser-properties"))

    this.language = hyperParams("parser-properties")("language").str
    Combinator.setLanguage(this.language, combinatorsContainer)

    sequenceEmbedder = SequenceEmbedderGeneralConfig.fromYaml(
      config("sequence-embedder")
    ).construct()

    isDiscriminative = config("main-vars").getOrElse("model-type", "discriminative") == "discriminative"
    assert(Set("discriminative", "generative") contains config("main-vars").getOrElse("model-type", "discriminative") )

    isLearnedRescaled = config("main-vars")("learned-rescaling").bool

    if(isGenerative)
      vocabularyLogSoftmax = VocLogSoftmaxConfig.fromYaml(config("word-softmax")).construct()

    validBeamRescaled    = config("main-vars").getOrElse("valid-beam-rescaled"        , false        )
    validBeamType        = config("main-vars").getOrElse("valid-beam-type"            , "simple"     )
    validBeamKMidParsing = config("main-vars").getOrElse("valid-beam-size-parsing"    , 1            )
    validBeamKOutParsing = config("main-vars").getOrElse("valid-beam-size-parsing-out", validBeamKMidParsing )
    validBeamKOutWord    = config("main-vars").getOrElse("valid-beam-size-word"       , 1            )
    validBeamKOutTagging = config("main-vars").getOrElse("valid-beam-size-tag"        , 1            )

    neuralParameters = NeuralParameters.fromYaml( config("neural-parameters") )
  }

  override def validate(devData: Iterable[IndexedInstance[TrainInstance]]): (Double, Map[String, Double]) = {
    val evaluator = MainEvaluate.newEvaluatorsPackage()
    val logProbAggregator = new LogProbAggregator()

    for((IndexedInstance(_, tree), i) <- devData.zipWithIndex){

      if(i%100==0 && i>0)
        System.err.println(s"validation $i")

      DynetSetup.cg_renew()

      val words = tree.words

      val finalBeam = Try(Parser.parseKbest(
          sent         = words,
          beamType     = validBeamType,
          beamRescaled = validBeamRescaled,
          kMidParsing  = validBeamKMidParsing,
          kOutParsing  = validBeamKOutParsing,
          kOutWord     = validBeamKOutWord,
          kOutTagging  = validBeamKOutTagging,
          maxStackSize = Int.MaxValue
        )(List(this))
      ).getOrElse{
        new PrintWriter(s"EXCEPTION").close()
        System.err.println("SOME EXPECTION")
        tree.saveVisual(s"origTree", "origTree")
        parserProperties.prepareDerivationTreeForTraining(tree).saveVisual(s"transTree", "transTree")
        throw new Exception("SOME EXCEPTION")
      }
      evaluator.addToCounts(sys = finalBeam.head._1, ref = tree)

      if(isGenerative) {
        val sentLogProb = Math.logSumExp(finalBeam.map(_._2))
        logProbAggregator.addLogProb(sentLogProb, words.size)
      }
    }

    (evaluator.mainScore, evaluator.exposedScores.toMap++logProbAggregator.exposedScores.toMap)
  }

  override def computeLoss(instance: IndexedInstance[TrainInstance]): Expression = {
    currentlyProcessing = instance
    val loss = Parser.loss(instance.instance)(this)
    loss
  }

  override def loadTrainData(file: String): Iterator[TrainInstance] =
    representation.DerivationsLoader.fromFile(file)

  override def loadExtra(modelDir: String): Unit = {
    allS2I = AllS2I.load(modelDir)
    combinatorsContainer = CombinatorsContainer.load(s"$modelDir/combinator_container.serialized")
  }

  override def saveExtra(modelDir: String): Unit = {
    allS2I.save(modelDir)
    combinatorsContainer.save(s"$modelDir/combinator_container.serialized")
  }

  override def filterTrainingData(trainData: Iterable[IndexedInstance[TrainInstance]]): Iterator[IndexedInstance[TrainInstance]] = trainData.iterator

  override def prepareForMiniBatch(miniBatch: List[IndexedInstance[TrainInstance]]): Unit = {
    if(isGenerative){
      val miniBatchWords: Set[String] = miniBatch.flatMap(_.instance.words).toSet
      vocabularyLogSoftmax.resampleImportaneSamples(miniBatchWords)
    }
  }

  override def prepareForTraining(trainData: Iterable[IndexedInstance[TrainInstance]]): Unit = {
    parserProperties = ParserProperties.fromYaml(hyperParams("parser-properties"))

    val w2i_tgt_gen:Any2Int[String] = new String2Int(
      minCount          = 2,
      maxVacabulary     = Int.MaxValue
    )

    val w2i_tgt_embed:Any2Int[String] = if(embeddingsFile != null){
      EmbedderStandard.pretrainedEmb_loadS2I(embeddingsFile, lowercased)
    }else{
      new String2Int(
        minCount          = 3,
        maxVacabulary     = Int.MaxValue
      )
    }
    val c2i = new String2Int()

    val minTagCount = if(language == "General")
      0
    else if (hyperParams("main-vars").contains("min-supertag-count"))
      hyperParams("main-vars")("min-supertag-count").int
    else
      10

    val cat2i            = new DefaultAny2Int[Category](         withUNK = true  , minCount=0 , UNK=Category("UNKNOWN"))
    val comb2i           = new DefaultAny2Int[Combinator](       withUNK = true  , minCount=0 , UNK=TypeChangeUnary(Category("UNKNOWN"), Category("UNKNOWN")))
    val taggingOptions2i = new DefaultAny2Int[TransitionOption]( withUNK = false , minCount=minTagCount)
    val reduceOptions2i  = new DefaultAny2Int[TransitionOption]( withUNK = false , minCount=0 )

    combinatorsContainer = new CombinatorsContainer()

    for(IndexedInstance(_, tree) <- trainData){

      val origTree = tree

      for(TerminalNode(word, category) <- origTree.leafs){
        taggingOptions2i.addToCounts(TaggingOption(category))
        if(embeddingsFile == null || w2i_tgt_embed.frequency(word)>0){
          w2i_tgt_embed.addToCounts(word)
        }else{
          w2i_tgt_embed.addToCounts(w2i_tgt_embed.UNK_str)
        }
        w2i_tgt_gen.addToCounts(word)
        word.toCharArray.foreach{ c => c2i.addToCounts(c.toString) }
      }
      w2i_tgt_gen.addToCounts(w2i_tgt_gen.EOS_str)
      w2i_tgt_embed.addToCounts(w2i_tgt_embed.EOS_str)

      val treeWithRevealing = parserProperties.prepareDerivationTreeForTraining(origTree)

      treeWithRevealing.allNodes.foreach{
        case UnaryNode(c, _)     => combinatorsContainer.add(c)
        case BinaryNode(c, _, _) => combinatorsContainer.add(c)
        case _                   =>
      }

      treeWithRevealing.allNodes.flatMap(_.getCombinator).withFilter(Combinator.isPredefined).foreach(comb2i.addToCounts)

      treeWithRevealing.allNodes.map(_.category).foreach(cat2i.addToCounts)
    }

    Combinator.setLanguage(language, combinatorsContainer)

    parserProperties.reduceOptions(combinatorsContainer).foreach(reduceOptions2i.addToCounts)

    w2i_tgt_gen.lock()
    w2i_tgt_embed.lock()
    c2i.lock()
    cat2i.lock()
    comb2i.lock()
    taggingOptions2i.lock()
    reduceOptions2i.lock()

    allS2I = new AllS2I(
      w2i_tgt_gen      = w2i_tgt_gen,      //: Any2Int[String],
      w2i_tgt_embed    = w2i_tgt_embed,    //: Any2Int[String],
      c2i              = c2i,              //: Any2Int[String],
      cat2i            = cat2i,            //: Any2Int[Category],
      comb2i           = comb2i,           //: Any2Int[Combinator],
      taggingOptions2i = taggingOptions2i, //: Any2Int[TransitionOption],
      reduceOptions2i  = reduceOptions2i   //: Any2Int[TransitionOption]
    )
  }

}

