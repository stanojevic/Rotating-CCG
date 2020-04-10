package edin.supertagger

import edin.algorithms.Zipper
import edin.general._
import edin.nn.embedder._
import edin.nn.layers.{Layer, MLPConfig}
import edu.cmu.dynet.{Expression, ParameterCollection}
import edin.nn.DyFunctions._
import edin.nn.DynetSetup
import edin.algorithms.AutomaticResourceClosing.linesFromFile

class SuperTaggingModel(
                         embeddingsFile         : String  = null,
                         embeddingsDim          : Int     = -1,
                         lowercased             : Boolean = false,
                         tagMinCount            : Int     = 0,
                         tagMaxVoc              : Int     = Int.MaxValue
                       ) extends ModelContainer[TrainInst]{

  def isWithAuxTags: Boolean = hyperParams("main-vars").getOrElse("aux-tag-rep-dim", 0)>0

  import SuperTaggingModel._

  var allS2I : AllS2I = _

  private var topNN:Layer = _
  private var wordSeqEmbedder : SequenceEmbedderGeneral[String] = _
  private var auxSeqEmbedder  : SequenceEmbedderGeneral[String] = _

  lazy val useAuxCats:Boolean = hyperParams("main-vars")("aux-tag-rep-dim").int > 0

  override protected
  def hyperParamTransformPermanently(hyperParam:  YamlConfig): YamlConfig = {
    val dim = if(embeddingsFile != null){
      EmbedderStandard.pretrainedEmb_loadDim(embeddingsFile)
    }else{
      embeddingsDim
    }

    hyperParam.mapAllTerminals(Map[String, AnyRef]( elems=
      "RESOURCE_EMB_DIM"      -> dim.asInstanceOf[AnyRef],
    ))
  }

  // DOC defining LSTMs and other parameters
  override protected
  def defineModelFromHyperFile()(implicit model:ParameterCollection) : Unit = {
    val config = hyperParams.mapAllTerminals(Map(
      "RESOURCE_C2I"           -> allS2I.c2i,
      "RESOURCE_W2I"           -> allS2I.w2i_of_embedder,
      "RESOURCE_AUX2I"         -> allS2I.aux_t2i,
      "RESOURCE_OUT_TAGS_SIZE" -> allS2I.t2i.size.asInstanceOf[AnyRef],
      "RESOURCE_EMB_LOC"       -> embeddingsFile
    ))

    topNN = MLPConfig.fromYaml(config("MLP")).construct()
    wordSeqEmbedder = SequenceEmbedderGeneralConfig.fromYaml(config("word-sequence-embedder"    )).construct()
    if(useAuxCats){
      auxSeqEmbedder  = SequenceEmbedderGeneralConfig.fromYaml(config("aux-tags-sequence-embedder")).construct()
    }

  }

  // DOC computes loss per instance after everything is ready
  override
  def computeLoss(instance:IndexedInstance[TrainInst]) : Expression = {
    val words    = instance.instance.words
    assert(words.nonEmpty)
    val tags     = instance.instance.tags
    val aux_tags = instance.instance.aux_tags
    val logsoftmaxes = findLogSoftmaxes(words, aux_tags)
    val loss = (tags zip logsoftmaxes).map{ case (t, lsf) =>
        -lsf(allS2I.t2i(t))
    }.esum
    loss
  }

  def findLogSoftmaxes(words:List[String], aux_tags:List[String]) : List[Expression] = {

    val wordEmbs = wordSeqEmbedder.transduce(words)
    val globalEmbeddings = if(auxSeqEmbedder == null){
      wordEmbs
    }else{
      val auxEmbs = auxSeqEmbedder.transduce(aux_tags)
      (wordEmbs zip auxEmbs).map{case (x, y) => concat(x, y)}
    }

    val logsoftmaxes = globalEmbeddings.map(topNN(_))
    logsoftmaxes
  }

  def predictBestTagSequence(words:List[String], aux_tags:List[String]) : List[String] = {
    DynetSetup.cg_renew()
    val logSoftmaxes = findLogSoftmaxes(words, aux_tags)
    SuperTaggingModel.predictBestTagSequenceGeneral(logSoftmaxes, allS2I.t2i)
  }

  // DOC validates
  override
  def validate(devData:Iterable[IndexedInstance[TrainInst]]) : (Double, Map[String, Double]) = {
    var correctTags = 0
    var totalTags   = 0
    for((IndexedInstance(_, instance), i) <- devData.zipWithIndex){
      if(i % 100 == 0)
        System.err.println(s"Validating instance $i")
      val words    = instance.words
      val goldTags = instance.tags
      val aux_tags = instance.aux_tags
      val predictedTags = predictBestTagSequence(words, aux_tags)
      correctTags += (goldTags zip predictedTags).count{case (g, p) => g == p}
      totalTags += goldTags.size
    }
    val precision = correctTags.toDouble/totalTags
    (precision, Map("precision" -> precision))
  }

  // DOC loads training instances
  override
  def loadTrainData(prefix:String):Iterator[TrainInst] = {
    val allWords = loadTokens(s"$prefix.words")
    val allTags  = loadTokens(s"$prefix.tags")
    val instances:Iterable[TrainInst] = if(useAuxCats){
      val allAuxTags = loadTokens(s"$prefix.aux_tags")
      Zipper.zip3(allWords, allTags, allAuxTags).map{ case (words, tags, auxTags) =>
        TrainInst(words=words, tags=tags, aux_tags=auxTags)
      }
    }else{
      Zipper.zip2(allWords, allTags).map{ case (words, tags) =>
        TrainInst(words=words, tags=tags, aux_tags=null)
      }
    }
    instances.iterator
  }

  override val toSentence: Option[TrainInst => List[String]] = Some(_.words)

  // DOC loads w2i and similar stuff
  override protected
  def loadExtra(modelDir:String): Unit =
    allS2I = AllS2I.load(modelDir)

  // DOC saves w2i and similar stuff
  override protected
  def saveExtra(modelDir:String): Unit =
    allS2I.save(modelDir)

  // DOC removes too long sentences and stuff like that
  override
  def filterTrainingData(trainData:Iterable[IndexedInstance[TrainInst]]) : Iterator[IndexedInstance[TrainInst]] =
    trainData.iterator.filter(_.instance.words.nonEmpty)

  // DOC computes s2i and other stuff
  override
  def prepareForTraining(trainData:Iterable[IndexedInstance[TrainInst]]) : Unit = {
    val w2i_of_embedder:Any2Int[String] = if(embeddingsFile != null){
      EmbedderStandard.pretrainedEmb_loadS2I(embeddingsFile, lowercased)
    }else{
      new String2Int(
        minCount          = 2,
        maxVacabulary     = Int.MaxValue
      )
    }
    val t2i = new String2Int(
      minCount          = tagMinCount,
      maxVacabulary     = tagMaxVoc,
      withEOS           = false
    )
    val aux_t2i = new String2Int(
      minCount          = tagMinCount,
      maxVacabulary     = tagMaxVoc,
      withEOS           = false
    )
    val c2i = new String2Int(
      minCount          = 10,
      maxVacabulary     = Int.MaxValue
    )
    trainData.foreach{ case IndexedInstance(_, instance) =>
      for(word <- instance.words){
        if(embeddingsFile == null)
          w2i_of_embedder.addToCounts(word)
        for(c <- word.toCharArray)
          c2i.addToCounts(c.toString)
      }
      for(tag <- instance.tags)
        t2i.addToCounts(tag)
      if(instance.aux_tags != null)
        for(auxTag <- instance.aux_tags)
          aux_t2i.addToCounts(auxTag)
    }
    System.err.println("you have "+(t2i.size-1)+" tags")
    allS2I = new AllS2I(
                  w2i_of_embedder = w2i_of_embedder,
                  t2i     = t2i,
                  c2i     = c2i,
                  aux_t2i = aux_t2i
    )
  }

}

object SuperTaggingModel{

  def loadTokens(fn:String) : Iterable[List[String]] =
    linesFromFile(fn).map( _.split(" +").toList ).toList

  def predictBestTagSequenceGeneral(logSoftmaxes:List[Expression], t2i:Any2Int[String]) : List[String] =
    logSoftmaxes.map{l =>
      val res = argmax(l, 1+1)
      val best = res.filterNot(_ == t2i.UNK_i).head
      t2i(best)
    }

  def predictKBestTagSequenceGeneral(logSoftmaxes:List[Expression], t2i:Any2Int[String], k:Int) : List[List[(String, Double)]] =
    logSoftmaxes.map{l =>
      val bestTagsWithScoresUnfiltered = argmaxWithScores(l, k+1).filterNot{_._1 == t2i.UNK_i}
      val bestTagsWithScores = bestTagsWithScoresUnfiltered.take(k)
      bestTagsWithScores.map{ case (tag_i:Int, logprob:Float) =>
        val tag_s = t2i(tag_i)
        val prob = math.exp(logprob)
        (tag_s, prob)
      }
    }

  def predictBetaBestTagSequenceGeneral(logSoftmaxes:List[Expression], t2i:Any2Int[String], beta:Float) : List[List[(String, Double)]] =
    logSoftmaxes.map{l =>
      val bestTagsWithScoresUnfiltered = argmaxBetaWithScores(l, beta).filterNot{_._1 == t2i.UNK_i}
      val bestTagsWithScores = if(bestTagsWithScoresUnfiltered.isEmpty) argmax(l,2).tail else bestTagsWithScoresUnfiltered
      bestTagsWithScores.map{ case (tag_i:Int, logprob:Float) =>
        val tag_s = t2i(tag_i)
        val prob = math.exp(logprob)
        (tag_s, prob)
      }
    }

}

