package edin.general

import java.io.File
import edu.cmu.dynet.{Expression, ModelLoader, ModelSaver, ParameterCollection}

trait ModelContainer[I]{

  // useful for debugging
  var currentAvgLoss : Double = Double.MaxValue

  private var _lastMiniBatchLoss = Double.MaxValue
  def lastMiniBatchLoss_=(x:Double) : Unit = {
    _lastMiniBatchLoss = x
  }
  def lastMiniBatchLoss : Double = {
    _lastMiniBatchLoss
  }

  var trainingStatsDir : String = _

  private val PARAMS_FN = "parameters"
  private val HYPER_PARAMS_FN = "hyperParams.yaml"
  private val KEY = "/SOME_KEY" // unimportant

  final def loadFromModelDir(modelDir: String): Unit = {
    System.err.println(s"LOADING the model START from $modelDir")
    loadHyperFile(s"$modelDir/$HYPER_PARAMS_FN")
    loadExtra(modelDir)

    defineModelFromHyperFileCaller()

    val modelLoader = new ModelLoader(s"$modelDir/$PARAMS_FN")
    modelLoader.populateModel(model, KEY)
    modelLoader.done()
    System.err.println("LOADING the model END")
  }

  final def save(modelDir: String): Unit = {
    System.err.println("Saving the model START")
    val f = new File(modelDir)
    if(!f.exists())
      f.mkdir()
    hyperParams.save(s"$modelDir/$HYPER_PARAMS_FN")
    saveExtra(modelDir)
    val modelSaver = new ModelSaver(s"$modelDir/$PARAMS_FN")
    modelSaver.addModel(model, KEY)
    modelSaver.done()
    System.err.println("Saving the model END")
  }

  final var hyperParams: YamlConfig = _
  final lazy val model:ParameterCollection = new ParameterCollection()

  final def defineModelFromHyperFileCaller() : Unit = {
    defineModelFromHyperFile()(model)
  }

  // called order 1. // doesn't need to be implemented
  final def loadHyperFile(hyperFile: String): Unit = {
    hyperParams = hyperParamTransformPermanently(YamlConfig.fromFile(hyperFile))
  }

  protected def hyperParamTransformPermanently(hyperParam:YamlConfig) : YamlConfig = hyperParam

  // called order 2
  // DOC loads training instances
  def loadTrainData(dir:String):Iterator[I]

  // called order 3
  // DOC removes too long sentences and stuff like that
  def filterTrainingData(trainData:Iterable[IndexedInstance[I]]) : Iterator[IndexedInstance[I]]

  // called order 4
  // DOC computes s2i and other stuff
  def prepareForTraining(trainData:Iterable[IndexedInstance[I]]) : Unit

  // called order 5
  // DOC defining LSTMs and other parameters
  protected def defineModelFromHyperFile()(implicit model:ParameterCollection) : Unit

  // called order 6
  // DOC precomputes the embeddings or other necessary things just before training starts
  def prepareJustBeforeTrainingStarts(trainData:Iterable[IndexedInstance[I]], devData:Iterable[IndexedInstance[I]]) : Unit = {}

  // called order 7
  // DOC called before training starts
  def prepareForEpoch(trainData:Iterable[IndexedInstance[I]], epoch:Int) : Unit = {}

  // called order 8
  // DOC called for precomputation
  def precomputeChunk(chunk:Iterable[IndexedInstance[I]]) : Unit = {}

  // called order 9
  // DOC called before minibatch starts (useful for better softmaxes)
  def prepareForMiniBatch(miniBatch:List[IndexedInstance[I]]) : Unit = {}

  // called order 10
  // DOC computes loss per instance after everything is ready
  def computeLoss(instance:IndexedInstance[I]) : Expression

  // called order 11
  // DOC validates
  def validate(devData:Iterable[IndexedInstance[I]]) : (Double, Map[String, Double])

  // called order 12
  // DOC saves w2i and similar stuff
  protected def saveExtra(modelDir:String)

  // called order 13 // not called during training
  // DOC loads w2i and similar stuff
  protected def loadExtra(modelDir:String)

  // called order 14
  // DOC to close all the necessary files
  def trainingEnd() : Unit = {}

}

