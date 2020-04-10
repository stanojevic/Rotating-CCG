package edin.general

import java.io._

import edu.cmu.dynet.{ComputationGraph, Expression, ParameterCollection, Trainer}
import scalax.chart.api._

import scala.util.{Failure, Random, Success, Try}
import edin.nn.DynetSetup
import edin.nn.DyFunctions._
import edin.nn.embedder.SequenceEmbedderPrecomputable

// train start (model_definition_json, model_dir)
//    hyper = load_hyper(model_definition_json)
//    param = define_model(hyper) # can modify hyper
//    if(train start){
//       init(param)
//    }else{
//       load(param, (model_dir+last_epoch))
//    }
//    epoch = 0
//    ...
//    save(model_dir, param)
//    save(model_dir, hyper)
// test(model_dir)
//    hyper = load_hyper(model_definition_json)
//    param = define_model(hyper) # can modify hyper
//    load(param, (model_dir))
//    ...

case class IndexedInstance[T](index:Int, instance:T)

class TrainingController[I](
                             continueTraining : Boolean,
                             epochs           : Int,
                             trainFile        : String,
                             devFile          : String,
                             modelDir         : String,
                             hyperFile        : String,
                             modelContainer   : ModelContainer[I],
                             allInMemory      : Boolean
                           ){

  private val MIN_LEARN_RATE = 0.0001

  private def iterator2iterable[K](iterator:Iterator[K]) : TypedObjectReader[K] = {
    val tmpF = File.createTempFile("translator.",".tmp")
    tmpF.deleteOnExit()
    val tmpFn = tmpF.toString
    val fhw = new TypedObjectWriter[K](tmpFn)
    for(i <- iterator)
      fhw.write(i)
    fhw.close()
    new TypedObjectReader[K](tmpFn)
  }

  private def isLocalhost = new File(Global.homeDir + "/.localhost").exists()

  private def prepareDataReaderOnHardDrive(
                                    file:String,
                                    withFiltering:Boolean
                                  ) : TypedObjectReader[IndexedInstance[I]] = {
    val unFiltered :TypedObjectReader[IndexedInstance[I]] = iterator2iterable(loadData(file))
    if(withFiltering)
      iterator2iterable(modelContainer.filterTrainingData(unFiltered))
    else
      unFiltered
  }

  private def loadData(file:String) : Iterator[IndexedInstance[I]] =
    modelContainer.loadTrainData(file).zipWithIndex.map{case (x, i) => IndexedInstance(i, x)}

  private def prepareDataReaderInMemory(
                                    file:String,
                                    withFiltering:Boolean
                                  ) : List[IndexedInstance[I]] =
    if(withFiltering)
      modelContainer.filterTrainingData(loadData(file).toList).toList
    else
      loadData(file).toList

  private def prepareDataReader(
                                  file:String,
                                  withFiltering:Boolean
                                  ) : Iterable[IndexedInstance[I]] =
    if(allInMemory)
      prepareDataReaderInMemory(file, withFiltering)
    else
      prepareDataReaderOnHardDrive(file, withFiltering)

  private def shuffle(data:Iterable[IndexedInstance[I]]) : Iterable[IndexedInstance[I]] =
    data match {
      case is: TypedObjectReader[I] =>
        is.reshuffle()
        is
      case _: List[I] =>
        randomizer.shuffle(data)
      case _ =>
        throw new Exception("unknown type of training data container")
    }

  private def log(prefix:String, m:String) : Unit = logLines(prefix, List(m))
  private def logLines(prefix:String, ms:List[String]) : Unit = {
    System.err.println()
    for(m <- ms)
      System.err.println(prefix.toUpperCase()+" "+m)
  }

  private var trainData:Iterable[IndexedInstance[I]] = _
  private var devData  :Iterable[IndexedInstance[I]] = _
  private var trainer  :Trainer = _
  private var decaying_lr : Boolean = _
  private var earlyStop   : Boolean = _
  private var precomputeEmbs : Boolean = _

  private var updatesInBatches   = 0
  private var updatesInInstances = 0
  private var updatesInInstancesSinceLastReport     = 0
  private var updatesInInstancesSinceLastValidation = 0
  private var timeSinceLastValidation = System.currentTimeMillis()
  private var updatesInInstancesSinceLastEpoch      = 0
  private var validationCount = 0
  private var lossSinceLastValidation = 0.0
  private var plottingSeriesLoss       = List[(Int, Double)]()
  private var plottingSeriesValidScore = List[(Int, Double)]()

  /**
    * if vlaidationFrequency == 0 save model after every epoch without running validation
    * if vlaidationFrequency  < 0 run validation after every epoch and save if the validation got better scores
    * if vlaidationFrequency  > 0 run validation after every validationFrequency training instances and save if the validation got better
    */
  private var validationFrequency = 0

  private var nonImprovementCountForLR = 0
  private var nonImprovementCountForEarlyStop = 0
  private val maxAllowedNonImprovementCountForEarlyStop = 3
  private val maxAllowedNonImprovementCountForDecay = 2
  private var bestValidationScore = Double.MinValue

  private val randomizer = new Random(seed = 42)

  private lazy val trainingStatsDir : String = {
    val statsDir  = s"$modelDir/stats"
    if(! new File(statsDir).exists()) new File(statsDir).mkdirs()
    statsDir
  }

  private
  object StopEncountered extends Exception {
    val fn = s"$modelDir/STOP"
    def refresh() : Unit = {
      new File(fn).delete()
    }
    var lastCheck = 0l
    def check() : Unit = {
      val period = (System.currentTimeMillis()-lastCheck)/1000
      if(period > 20){
        lastCheck = System.currentTimeMillis()
        if(new File(fn).exists()){
          System.err.println("STOP")
          throw StopEncountered
        }
      }
    }
  }

  private
  object PauseEncountered {
    val fn = s"$modelDir/PAUSE"
    var lastCheck = 0l
    def refresh() : Unit = {
      new File(fn).delete()
    }
    def check() : Unit = {
      val period = (System.currentTimeMillis()-lastCheck)/1000
      if(period > 20) {
        lastCheck = System.currentTimeMillis()
        while (new File(fn).exists()) {
          StopEncountered.check()
          System.err.println("PAUSE")
          val seconds = 10
          Thread.sleep(seconds * 1000)
        }
      }
    }
  }

  def embeddingsPrecomputation() : Unit = {
    if (precomputeEmbs) {
      SequenceEmbedderPrecomputable.precompute(
        trainData.toList.map(x => modelContainer.toSentence.get(x.instance)),
        trainFile
      )
      SequenceEmbedderPrecomputable.precompute(
        devData.toList.map(x => modelContainer.toSentence.get(x.instance)),
        devFile
      )
    }
  }

  def train() : Unit = {
    modelContainer.trainingStatsDir = trainingStatsDir
    modelContainer.loadHyperFile(hyperFile)
    trainData = prepareDataReader(trainFile, withFiltering = true )
    devData   = prepareDataReader(devFile  , withFiltering = false)
    if(continueTraining){
      log("PREPARATION", "Continuing training")
      modelContainer.loadFromModelDir(modelDir)
    }else{
      log("PREPARATION", "Preparing for training")
      modelContainer.prepareForTraining(trainData)
      log("PREPARATION", "Defining the model")
      modelContainer.defineModelFromHyperFileCaller()
    }

    implicit val model:ParameterCollection = modelContainer.model
    val hyper = modelContainer.hyperParams

    val epochSavingFreq         : Int     = hyper("trainer").getOrElse("epoch-saving-freq", -1)
    val trainerType             : String  = hyper("trainer")("type").str
    val init_lr                 : Float   = hyper("trainer")("init-learning-rate").float
    val weightDecay             : Float   = hyper("trainer")("weight-decay").float
    val sparseUpdate            : Boolean = hyper("trainer")("sparse-update").bool
    val clipping                : Boolean = hyper("trainer")("gradient-clipping").bool
    val miniBatchSize           : Int     = hyper("trainer")("mini-batch-size").int
    val reportingFrequency      : Int     = hyper("trainer")("reporting-frequency").int
    validationFrequency                   = hyper("trainer")("validation-frequency").int // -1 if validation at epoch level and 0 for no validation
    earlyStop                             = hyper("trainer").getOrElse("early-stopping", default=false)
    decaying_lr                           = hyper("trainer")("decay-learning-rate").bool
    trainer                               = trainerFactory(trainerType, init_lr, clipping=clipping)
    precomputeEmbs                        = hyper("trainer").getOrElse("precomputation-embeddings", default= false)

    model.setWeightDecayLambda(weightDecay)
    if(sparseUpdate)
      trainer.enableSparseUpdates()
    else
      trainer.disableSparseUpdates()

    var closses = 0.0

    updatesInBatches   = 0
    updatesInInstances = 0
    updatesInInstancesSinceLastReport     = 0
    updatesInInstancesSinceLastValidation = 0
    timeSinceLastValidation = System.currentTimeMillis()
    updatesInInstancesSinceLastEpoch      = 0
    validationCount = 0


    PauseEncountered.refresh()
    StopEncountered.refresh()

    modelContainer.prepareJustBeforeTrainingStarts(trainData, devData)

    // For BERT and ELMo
    embeddingsPrecomputation()

    try{
      for(epoch <- 0 until epochs){
        var crashesWithinEpoch = 0
        val timeEpochStart = System.currentTimeMillis()
        log(s"EPOCH $epoch", s"epoch started at ${Global.currentTimeHumanFormat}")
        trainData = shuffle(trainData)
        modelContainer.prepareForEpoch(trainData, epoch)
        updatesInInstancesSinceLastEpoch = 0

        for(batch <- makeChunks(trainData, miniBatchSize)){
          modelContainer.prepareForMiniBatch(batch)
          enableAllDropout()
          DynetSetup.cg_renew()

          val miniLosses = batch.map{ instance =>
            System.err.print(".")
            modelContainer.computeLoss(instance)
          }
          val batchLoss = Expression.average(miniLosses)

          val batchLossVal = Try(batchLoss.toFloat) match {
            case Success(v) =>
              v
            case Failure(e) =>
              crashesWithinEpoch += 1
              System.err.println(s"failing forward pass with exception: $e")
              0.0
          }

          if(!batchLossVal.isNaN && !batchLossVal.isInfinite && (modelContainer.negLossIsOk || batchLossVal>=0.0)){
            modelContainer.lastMiniBatchLoss = batchLossVal * miniLosses.size // for debugging purposes
            closses += batchLossVal * miniLosses.size
            lossSinceLastValidation += batchLossVal * miniLosses.size
            try{
              ComputationGraph.backward(batchLoss)
              trainer.update()
            }catch {
              case e:RuntimeException =>
                crashesWithinEpoch += 1
                log(s"EPOCH $epoch", s"failing backward pass with RException: $e when batch loss is $batchLossVal")
              case e:Exception =>
                crashesWithinEpoch += 1
                log(s"EPOCH $epoch", s"failing backward pass with Exception : $e when batch loss is $batchLossVal")
            }
          }else{
            log(s"EPOCH $epoch", s"LOSS IS WEIRD !!! WARNING !!! LOSS: $batchLossVal")
            log(s"EPOCH $epoch", s"consider reducing learning rate")
          }

          updatesInBatches += 1
          updatesInInstances += batch.size
          updatesInInstancesSinceLastReport     += batch.size
          updatesInInstancesSinceLastValidation += batch.size
          updatesInInstancesSinceLastEpoch      += batch.size

          if(updatesInInstancesSinceLastReport >= reportingFrequency){
            val avgClosses = closses/updatesInInstancesSinceLastReport
            modelContainer.currentAvgLoss = avgClosses
            log(s"EPOCH $epoch", f"instances since last epoch $updatesInInstancesSinceLastEpoch avg_loss=$avgClosses%.3f")
            reportPeriodPassed(s"EPOCH $epoch", "took", timeEpochStart)
            updatesInInstancesSinceLastReport = 0
            closses = 0.0
          }
          if(updatesInInstancesSinceLastValidation > validationFrequency && validationFrequency > 0){
            validationStep()
          }
          StopEncountered.check()
          PauseEncountered.check()
          if(crashesWithinEpoch>=5){
            System.err.println(s"WARNING: This epoch had $crashesWithinEpoch backprop crashes so it may be corrupt")
            System.err.println(s"WARNING: I will reload the last best model and halve the learning rate")
            crashesWithinEpoch = 0
            modelContainer.loadFromModelDir(modelDir)
            embeddingsPrecomputation()
            val newLR = trainer.learningRate/2.0f
            if(newLR>MIN_LEARN_RATE){
              log(s"EPOCH $epoch", s"!!! halving learning rate to $newLR")
              trainer.restart(newLR)
            }
          }
        }
        if(validationFrequency <= 0){
          validationStep()
        }
        if(epochSavingFreq > 0 && epoch%epochSavingFreq==0 && !isLocalhost){
          Thread sleep 1000
          modelContainer save s"$modelDir/model_epoch_$epoch"
        }
      }
    } catch {
      case StopEncountered =>
        System.err.println("ending gracefully with normal STOP condition")
        modelContainer.trainingEnd()
    }

  }

  private def reportPeriodPassed(prefix:String, message:String, eventTime:Long):Unit = {
    val periodOfMinsTotal = (System.currentTimeMillis() - eventTime)/60000
    val periodPartHours = periodOfMinsTotal/60
    val periodPartMin   = periodOfMinsTotal%60
    log(prefix, s"$message ${periodPartHours}h ${periodPartMin}m")
  }

  private def validationStep() : Unit = {
    log(s"VALID $validationCount", "START")
    reportPeriodPassed(s"VALID $validationCount", "period since last validation", timeSinceLastValidation)
    DynetSetup.cg_renew()
    disableAllDropout()
    val timeValidStart = System.currentTimeMillis()
    val (validScore:Double, allScores) = if(validationFrequency != 0) modelContainer.validate(devData) else (-lossSinceLastValidation / updatesInInstancesSinceLastValidation, Map[String, Double]())
    reportValidation(s"VALID $validationCount", validScore, allScores)
    if(validScore > bestValidationScore){
      modelContainer.save(modelDir)
      bestValidationScore = validScore
      log(s"VALID $validationCount", f"NEW_BEST -> $bestValidationScore%.3f")
      nonImprovementCountForLR = 0
      nonImprovementCountForEarlyStop = 0
    }else{
      nonImprovementCountForLR += 1
      if(decaying_lr && nonImprovementCountForLR>=maxAllowedNonImprovementCountForDecay){
        nonImprovementCountForLR = 0
        val newLR = trainer.learningRate/2.0f
        if(newLR>MIN_LEARN_RATE){
          log(s"VALID $validationCount", s"!!! halving learning rate to $newLR")
          trainer.restart(newLR)
        }
      }
      nonImprovementCountForEarlyStop += 1
      if(earlyStop && nonImprovementCountForEarlyStop>= maxAllowedNonImprovementCountForEarlyStop){
        log(s"VALID $validationCount", s"EARLY STOPPING")
        throw StopEncountered
      }
    }
    reportPeriodPassed(s"VALID $validationCount", "took", timeValidStart)

    lossSinceLastValidation /= updatesInInstancesSinceLastValidation

    if(! isLocalhost){
      plottingSeriesLoss ::= (validationCount, lossSinceLastValidation)
      plottingSeriesValidScore ::= (validationCount, validScore*100)
      dumpCSV(s"$trainingStatsDir/learning_curve.csv", plottingSeriesValidScore, plottingSeriesLoss)

      chartPlotPDF(s"$trainingStatsDir/learning_curve")

      gnuPlot(s"$trainingStatsDir/learning_curve")
    }

    timeSinceLastValidation = System.currentTimeMillis()
    validationCount += 1
    lossSinceLastValidation = 0.0
    updatesInInstancesSinceLastValidation  = 0
  }

  private def chartPlotPDF(f:String) : Unit = {
    val chart = XYLineChart(List(
      "accuracy"->plottingSeriesValidScore,
      "loss"->plottingSeriesLoss))
    chart.title = "learning curve"
    chart.plot.range.axis.label.text = ""
    chart.plot.domain.axis.label.text = "validation"
    val color = new Color(173, 216, 230)
    chart.backgroundPaint = color.brighter()
    chart.plot.setBackgroundPaint(color)
    chart.saveAsPDF(s"$f.pdf")
  }

  private def gnuPlot(f:String) : Unit =
    try{
      var cmd = s"""echo \"set term dumb ; set datafile separator ',' ; plot '$f.csv' using 1:2 with lines title columnhead, '$f.csv' using 1:3 with lines title columnhead " | gnuplot > $f.txt"""
      sys.process.stringSeqToProcess(Seq("/bin/bash", "-c", "cd " + new File(".").getAbsolutePath + ";" + cmd)).!

      val l = 20

      var col = 2
      var typ = "accuracy"
      cmd = s"""echo \"set term dumb ; set datafile separator ',' ; plot '< head -1 $f.csv ; tail -n +2 $f.csv | tail -n $l' using 1:$col with lines title columnhead \" | gnuplot > ${f}_${typ}_last$l.txt"""
      sys.process.stringSeqToProcess(Seq("/bin/bash", "-c", "cd " + new File(".").getAbsolutePath + ";" + cmd)).!

      col = 3
      typ = "loss"
      cmd = s"""echo \"set term dumb ; set datafile separator ',' ; plot '< head -1 $f.csv ; tail -n +2 $f.csv | tail -n $l' using 1:$col with lines title columnhead \" | gnuplot > ${f}_${typ}_last$l.txt"""
      sys.process.stringSeqToProcess(Seq("/bin/bash", "-c", "cd " + new File(".").getAbsolutePath + ";" + cmd)).!

    }catch{
      case _:Exception => println("gnuplotting failed")
    }

  private def dumpCSV(f:String, plottingSeriesValidScore:List[(Int, Double)], plottingSeriesLoss:List[(Int, Double)]) : Unit = {
    val pw = new PrintWriter(f)
    pw.println(s"validation,accuracy,loss")
    for( ((i, loss), (j, valid)) <- (plottingSeriesLoss zip plottingSeriesValidScore).reverse ){
        assert(i == j)
        pw.println(s"$i,$valid,$loss")
    }
    pw.close()
  }

  private def reportValidation(
                      prefix: String,
                      validScore:Double,
                      allScores:Map[String, Double]
                    ) : Unit ={
    val linesToPrint = for((k, v) <- allScores.toList.sortBy(_._1)) yield f"$k -> $v%.3f"
    logLines(prefix, s"main_score: $validScore" :: linesToPrint)
  }

  private def makeChunks(
                           instances:Iterable[IndexedInstance[I]],
                           batchSize:Int
                         ) : Iterator[List[IndexedInstance[I]]] =
    instances.iterator.sliding(batchSize, batchSize).map(_.toList)

}

