package edin.supertagger

import edin.nn.DyFunctions._
import edin.nn.DynetSetup
import edu.cmu.dynet.Expression

class Ensamble(modelDirs:Seq[String]) {

  def isWithAuxTags: Boolean = modelContainers.exists(_.isWithAuxTags)

  val modelContainers:Seq[SuperTaggingModel] = modelDirs.map{ modelDir =>
    val modelContainer = new SuperTaggingModel()
    modelContainer.loadFromModelDir(modelDir)
    modelContainer
  }

  def findLogSoftmaxes(words:List[String], aux_tags:List[String]) : List[Expression] = {
    val allSoftmaxes:Seq[Seq[Expression]] = modelContainers.map{_.findLogSoftmaxes(words, aux_tags)}
    val ensambleSoftmaxes = allSoftmaxes.transpose.map(averageLogSoftmaxes).toList
    ensambleSoftmaxes
  }

  def predictBestTagSequence(words:List[String], aux_tags:List[String]) : List[String] = {
    DynetSetup.cg_renew()
    val logSoftmaxes = findLogSoftmaxes(words, aux_tags)
    SuperTaggingModel.predictBestTagSequenceGeneral(logSoftmaxes, modelContainers.head.allS2I.t2i)
  }

  def predictKBestTagSequenceWithScores(words:List[String], aux_tags:List[String], k:Int) : List[List[(String, Double)]] = {
    DynetSetup.cg_renew()
    val logSoftmaxes = findLogSoftmaxes(words, aux_tags)
    SuperTaggingModel.predictKBestTagSequenceGeneral(logSoftmaxes, modelContainers.head.allS2I.t2i, k)
  }

  def predictBetaBestTagSequenceWithScores(words:List[String], aux_tags:List[String], beta:Float) : List[List[(String, Double)]] = {
    DynetSetup.cg_renew()
    val logSoftmaxes = findLogSoftmaxes(words, aux_tags)
    SuperTaggingModel.predictBetaBestTagSequenceGeneral(logSoftmaxes, modelContainers.head.allS2I.t2i, beta)
  }

}

