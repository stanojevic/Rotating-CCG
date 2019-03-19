package edin.ccg.parsing

import java.io.PrintWriter

import edin.algorithms.evaluation.LogProbAggregator
import edin.algorithms.{Math, Pointer, Zipper}
import edin.ccg.representation._
import edin.ccg.transitions._
import edin.ccg._
import edin.ccg.representation.category.Category
import edin.ccg.representation.combinators.{Combinator, TypeChangeUnary}
import edin.ccg.representation.tree._
import edin.general._
import edin.nn.DynetSetup
import edin.nn.embedder.{EmbedderStandard, SequenceEmbedderELMo, SequenceEmbedderGeneral, SequenceEmbedderGeneralConfig}
import edin.nn.layers.{VocLogSoftmax, VocLogSoftmaxConfig}
import edu.cmu.dynet.{Expression, ParameterCollection}

import scala.util.{Failure, Success, Try}

class RevealingModel(
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
  var validBeamSizeParsing   : Int                             = 1
  var validBeamSizeWord      : Int                             = 1
  var validBeamSizeFastTrack : Int                             = 0
  var vocabularyLogSoftmax   : VocLogSoftmax[String]           = _

  private val elmoPointer = new Pointer[SequenceEmbedderELMo]

  override protected def hyperParamTransformPermanently(hyperParam: YamlConfig): YamlConfig = {
    val dim = if(embeddingsFile != null){
      EmbedderStandard.pretrainedEmb_loadDim(embeddingsFile)
    }else{
      embeddingsDim
    }

    hyperParam.mapAllTerminals(Map[String, AnyRef]( elems=
      "RESOURCE_EMB_DIM"      -> dim.asInstanceOf[AnyRef],
      "RESOURCE_EMB_LOC"      -> embeddingsFile
    ))
  }

  override protected def defineModelFromHyperFile()(implicit model: ParameterCollection): Unit = {
    val config:YamlConfig = hyperParams.mapAllTerminals(Map[String, AnyRef](elems =
      "RESOURCE_C2I"                -> allS2I.c2i,
      "RESOURCE_W2I_TGT_GENERATION" -> allS2I.w2i_tgt_gen,
      "RESOURCE_W2I_TGT_EMBED"      -> allS2I.w2i_tgt_embed,
      "RESOURCE_CAT2I"              -> allS2I.cat2i,
      "RESOURCE_COMB2I"             -> allS2I.comb2i,
      "RESOURCE_TAGS_SIZE"          -> allS2I.taggingOptions2i.size.asInstanceOf[AnyRef],
      "RESOURCE_TRANS_SIZE"         -> allS2I.reduceOptions2i.size.asInstanceOf[AnyRef],
      "RESOURCE_ELMo_POINTER"       -> elmoPointer
    ))

    parserProperties = ParserProperties.fromYaml(hyperParams("parser-properties"))

    sequenceEmbedder = SequenceEmbedderGeneralConfig.fromYaml(
      config("sequence-embedder")
    ).construct()

    isDiscriminative = config("main-vars").getOrElse("model-type", "discriminative") == "discriminative"
    assert(Set("discriminative", "generative") contains config("main-vars").getOrElse("model-type", "discriminative") )
    validBeamSizeParsing = config("main-vars").getOrElse("valid-beam-size-parsing", 1)
    if(isDiscriminative){
      validBeamSizeWord = 0
      validBeamSizeFastTrack = 0
    }else {
      validBeamSizeWord = config("main-vars").getOrElse("valid-beam-size-word", 1)
      validBeamSizeFastTrack = config("main-vars").getOrElse("valid-beam-size-fasttrack", 0)
    }
    if(!isDiscriminative)
      vocabularyLogSoftmax = VocLogSoftmaxConfig.fromYaml(config("word-softmax")).construct()

    neuralParameters = NeuralParameters.fromYaml(
      config("neural-parameters")
    )
  }

  override def validate(devData: Iterable[IndexedInstance[TrainInstance]]): (Double, Map[String, Double]) = {
    val evaluator = MainEvaluate.newEvaluatorsPackage()
    val logProbAggregator = new LogProbAggregator()

    for((IndexedInstance(_, inst), i) <- devData.zipWithIndex){

      if(i%100==0 && i>0)
        System.err.println(s"validation $i")

      setEmbedding(inst.tree.words, inst.emb)
      DynetSetup.cg_renew()

      val words = inst.tree.words

      val finalBeam = Try(Parser.parseMany(words, parsingBeamSize = validBeamSizeParsing, wordBeamSize = validBeamSizeWord, fastTrackBeamSize = validBeamSizeFastTrack)(List(this))) match {
        case Success(value) => value
        case Failure(e) =>
          val pw = new PrintWriter(s"EXCEPTION")
          pw.println(e)
          pw.close()

          inst.tree.saveVisual(s"origTree", "origTree")
          parserProperties.prepareDerivationTreeForTraining(inst.tree).saveVisual(s"transTree", "transTree")
          throw e
      }
      evaluator.addToCounts(sys = finalBeam.head._1, ref = inst.tree)

      if(!isDiscriminative) {
        val sentLogProb = Math.logSumExp(finalBeam.map(_._2))
        logProbAggregator.addLogProb(sentLogProb, words.size)
      }

      unsetEmbedding(inst.tree.words)
    }

    (evaluator.mainScore, evaluator.exposedScores.toMap++logProbAggregator.exposedScores.toMap)
  }

  private def setEmbedding(sent:List[String], emb:SentEmbedding) : Unit = {
    if(emb != null && elmoPointer() != null){
      // turn on precomputed
      elmoPointer().cachedEmbeddings(sent) = emb
    }
  }

  private def unsetEmbedding(sent:List[String]) : Unit = {
    if(elmoPointer() != null){
      // turn off precomputed
      elmoPointer().cachedEmbeddings.remove(sent)
    }
  }

  override def computeLoss(instance: IndexedInstance[TrainInstance]): Expression = {
    currentlyProcessing = instance

    // System.err.println(s"proc ${instance.index} ${instance.instance.tree.words.mkString(" ")}")

    setEmbedding(instance.instance.tree.words, instance.instance.emb)
    val loss = Parser.loss(instance.instance.tree)(this)
    unsetEmbedding(instance.instance.tree.words)

    loss
  }

  override def loadTrainData(file: String): Iterator[TrainInstance] = {
    val instances = representation.DerivationsLoader.fromFile(file)
    val useElmo = hyperParams("sequence-embedder").deepSearch("ELMo").nonEmpty
    val precomputeEmbs = hyperParams("trainer").getOrElse("precomputation-all-ELMo-embeddings", default= false)
    if(useElmo && precomputeEmbs){
      System.err.println(s"Loading precomputed ELMo embeddings for $file")
      val embeddingType = hyperParams("sequence-embedder").deepSearch("ELMo-type").head.str
      val r : String => Iterator[List[String]] = f => DerivationsLoader.fromFile(f).map{_.words}
      SequenceEmbedderELMo.precomputeEmbsSafe(file, r, embeddingType)
      val embs = new ObjectIteratorFromFile[List[Array[Float]]](s"$file.elmo.$embeddingType")
      Zipper.zip2(instances, embs).map{case (x, y) => Inst(x, y)}
    }else{
      Zipper.zip2(instances, Stream.from(0).map(_=>null.asInstanceOf[SentEmbedding]).iterator).map{
        case (x:TreeNode, y:SentEmbedding) => Inst(x, y)
        case (x:TreeNode, null) => Inst(x, null)
      }
    }
  }

  override protected def loadExtra(modelDir: String): Unit = {
    allS2I = AllS2I.load(modelDir)
    combinatorsContainer = CombinatorsContainer.load(s"$modelDir/combinator_container.serialized")
  }

  var firstIteration = true
  override protected def saveExtra(modelDir: String): Unit = {
    if(firstIteration){
      allS2I.save(modelDir)
      combinatorsContainer.save(s"$modelDir/combinator_container.serialized")
      firstIteration = false
    }
  }

  override def filterTrainingData(trainData: Iterable[IndexedInstance[TrainInstance]]): Iterator[IndexedInstance[TrainInstance]] = trainData.iterator

  override def precomputeChunk(chunk: Iterable[IndexedInstance[TrainInstance]]): Unit = {
    if(hyperParams("main-vars")("precompute-embeddings").bool){
      sequenceEmbedder.cleanPrecomputedCache()
      chunk.filter(_.instance.emb != null).foreach{inst =>
        val sent = inst.instance.tree.words
        if(elmoPointer() != null)
          elmoPointer().cachedEmbeddings(sent) = inst.instance.emb
      }
      sequenceEmbedder.precomputeEmbeddings(chunk.map{_.instance.tree.leafs.map{_.word}}.toList)
    }
  }

  override def prepareForMiniBatch(miniBatch: List[IndexedInstance[TrainInstance]]): Unit = {
    if(!isDiscriminative){
      val miniBatchWords: Set[String] = miniBatch.flatMap(_.instance.tree.words).toSet
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

    val cat2i            = new DefaultAny2Int[Category](         withUNK = true  , minCount=0 , UNK=Category("UNKNOWN"))
    val comb2i           = new DefaultAny2Int[Combinator](       withUNK = true  , minCount=0 , UNK=TypeChangeUnary(Category("UNKNOWN"), Category("UNKNOWN")))
    val taggingOptions2i = new DefaultAny2Int[TransitionOption]( withUNK = false , minCount=hyperParams("main-vars").getOrElse("min-supertag-count", 10) )
    val reduceOptions2i  = new DefaultAny2Int[TransitionOption]( withUNK = false , minCount=0 )

    combinatorsContainer = new CombinatorsContainer()

    trainData.foreach{ case IndexedInstance(_, inst) =>

      val origTree = inst.tree

      origTree.leafs.foreach{ case TerminalNode(word, category) =>
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
        case _ =>
      }

      treeWithRevealing.allNodes.flatMap(_.getCombinator).withFilter(Combinator.isPredefined).foreach(comb2i.addToCounts)

      treeWithRevealing.allNodes.map(_.category).foreach(cat2i.addToCounts)
    }

    // w2i(0) // to lock the w2i
    w2i_tgt_gen.lock()
    w2i_tgt_embed.lock()
    cat2i.lock()
    comb2i.lock()

    parserProperties.reduceOptions(combinatorsContainer).foreach(reduceOptions2i.addToCounts)

    allS2I = new AllS2I(
      w2i_tgt_gen      = w2i_tgt_gen, //: Any2Int[String],
      w2i_tgt_embed    = w2i_tgt_embed, //: Any2Int[String],
      c2i              = c2i, //: Any2Int[String],
      cat2i            = cat2i, //: Any2Int[Category],
      comb2i           = comb2i, //: Any2Int[Combinator],
      taggingOptions2i = taggingOptions2i, //: Any2Int[TransitionOption],
      reduceOptions2i  = reduceOptions2i //: Any2Int[TransitionOption]
    )
  }

  override def trainingEnd(): Unit = {
    SequenceEmbedderELMo.endServer()
  }


}

