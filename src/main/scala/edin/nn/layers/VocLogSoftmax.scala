package edin.nn.layers

import edin.algorithms.Sampler
import edin.general.{Any2Int, YamlConfig}
import edin.nn.DyFunctions._
import edu.cmu.dynet.{Expression, ParameterCollection}


sealed case class VocLogSoftmaxConfig[W](
                                          s2i                      : Any2Int[W],
                                          sizes                    : List[Int], // layer sizes
                                          dropout                  : Float=0f,
                                          cssUse                   : Boolean, // importanceSamplingUse: false
                                          cssNegativeSamplesSize   : Int, // importanceSamplingNegatives: integer
                                          cssPositiveSamplesUseAll : Boolean, // importanceSamplingPositivesUseAll: true
                                          withLayerNorm            : Boolean,
                                          withWeightNorm           : Boolean
                                           ) {
  def construct()(implicit model: ParameterCollection) = new VocLogSoftmax[W](this)
}

object VocLogSoftmaxConfig{

  def fromYaml[W](conf:YamlConfig) : VocLogSoftmaxConfig[W] = VocLogSoftmaxConfig(
    s2i                      = conf("w2i").any2int,
    sizes                    = conf("sizes").intList,
    dropout                  = conf.getOrElse("dropout", default = 0f),
    cssUse                   = conf.getOrElse("css-use", default = false),
    cssNegativeSamplesSize   = conf.getOrElse("css-negative-samples-size", default = 150),
    cssPositiveSamplesUseAll = conf.getOrElse("css-positive-samples-use-all", default = true),
    withLayerNorm            = conf.getOrElse("with-layer-norm", default = false),
    withWeightNorm           = conf.getOrElse("with-weight-norm", default = false),
  )

}

class VocLogSoftmax[W](config:VocLogSoftmaxConfig[W])(implicit model:ParameterCollection) extends Layer {

  val s2i: Any2Int[W] = config.s2i
  private val cssUse                   = config.cssUse
  private val cssNegativeSamplesSize   = config.cssNegativeSamplesSize
  private val cssPositiveSamplesUseAll = config.cssPositiveSamplesUseAll

  private var samplesComputedAtLeastOnce = false

  private val layer: Layer = MLPConfig(
    activations    = config.sizes.tail.map(_ => "relu") :+ "linear",
    sizes          = config.sizes :+ s2i.size,
    dropouts       = config.sizes.tail.map(_ => 0f) :+ config.dropout,
    withLayerNorm  = config.withLayerNorm,
    withWeightNorm = config.withWeightNorm
  ).construct()

  inDim = layer.inDim
  outDim = layer.outDim

  // private val freq:List[(Int, Int)] = s2i.frequency.toList.map{case (k, c) => (s2i(k), c)}.sortBy(_._1)
  private val freq:Map[WordId, WordCorpusCount] = s2i.frequencyIntMap
  private val totalZ:Float = freq.values.sum

  // returns cssSupportSize neg samples with importance weights where the same sample doesn't repeat
  private def negSample(freq:Map[WordId, WordCorpusCount], numblerOfSamples:Int, excludedWords:Set[Int]) : List[(WordId, WordSampleCount, WordCorpusCount)] = {
    val filteredFreq = freq.toList
      .filterNot{case (id, _) => excludedWords.contains(id)}
      .map{case (id, corpuCount) => (id, corpuCount.toDouble)}
    val samples : List[(WordId, WordSampleCount)] = Sampler.groupSamples(Sampler.manySamples(filteredFreq, numblerOfSamples, withReplacement = true))
    samples.map{case (id, sampleCount) => (id, sampleCount, freq(id))}
  }

  // private var currImportanceSamples : List[(Int, Float)] = Nil
  private type WordId = Int
  private type WordSampleCount = Int
  private type WordCorpusCount = Float
  private var currMiniBatchWords   : List[WordId] = Nil
  private var currNegativeExamples : List[(WordId, WordSampleCount, WordCorpusCount)] = Nil

  def resampleImportaneSamples(miniBatchWords:Set[W]) : Unit = resampleImportaneSamplesInt(miniBatchWords.map(s2i(_)))

  def resampleImportaneSamplesInt(miniBatchWords:Set[WordId]) : Unit = {
    samplesComputedAtLeastOnce = true
    if(cssUse){
      currMiniBatchWords = miniBatchWords.toList
      if(cssPositiveSamplesUseAll){
        currNegativeExamples = negSample(freq=freq, numblerOfSamples=cssNegativeSamplesSize, excludedWords=miniBatchWords)
      }else{
        currNegativeExamples = negSample(freq=freq, numblerOfSamples=cssNegativeSamplesSize, excludedWords=Set())
      }
    }
  }

  def computeWordLogProbTrainingTime(input:Expression, word:W) : Expression = computeWordLogProbTrainingTime(input, s2i(word))

  def computeWordLogProbTrainingTime(input:Expression, word:Int) : Expression = {
    if(cssUse){
      assert(samplesComputedAtLeastOnce)

      val (positiveExamples:List[WordId], negativeExamples:List[(WordId, WordSampleCount, WordCorpusCount)]) = if(cssPositiveSamplesUseAll){
        (
          currMiniBatchWords,
          currNegativeExamples
        )
      }else{
        (
          List(word),
          currNegativeExamples.filterNot(_._1==word)
        )
      }

      val newZ:Float = totalZ - positiveExamples.map(freq).sum
      val n = negativeExamples.map(_._2).sum.toFloat // total number of samples
      val negativeWeights : List[Float] = negativeExamples.map{case (_, sampleCount, corpusCount) => sampleCount/(corpusCount/newZ)/n}

      val samplesAll : List[WordId] = positiveExamples ++ negativeExamples.map(_._1)

      val positiveWeights : List[Float] = positiveExamples.map{_=>1f}
      val weightsAll : List[Float] = positiveWeights ++ negativeWeights

      val logits = exp(layer(input, targets = samplesAll.map(_.toLong)))
      val weights = vector(weightsAll.toArray)
      val Z = Expression.sumElems(cmult(weights, logits))
      val goldPosition = samplesAll.zipWithIndex.find(_._1 == word).get._2
      log(logits(goldPosition)) - log(Z)
    }else{
      apply(input)(word)
    }
  }

  override def apply(input: Expression, targets: List[Long]): Expression = {
    logSoftmax(layer(input, targets))
  }
}

