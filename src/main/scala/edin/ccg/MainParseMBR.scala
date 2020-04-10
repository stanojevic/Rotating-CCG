package edin.ccg

import java.io.{File, PrintWriter}

import edin.ccg.representation.tree.TreeNode
import edin.general.Global

import scala.collection.mutable.{Map => MutMap}
import edin.algorithms.Math.logSumExp
import edin.ccg.representation.DerivationsLoader

object MainParseMBR {

  case class CMDargs(
                      samples_dir         : String   = null,
                      out_dir             : String   = null,
                      gold_trees          : String   = null,
                      alpha               : Double   =   -1
                    )

  def main(args:Array[String]) : Unit = {
    val parser = new scopt.OptionParser[CMDargs](PROGRAM_NAME) {
      head(PROGRAM_NAME, PROGRAM_VERSION.toString)

      opt[ String      ]( "samples_dir" ).action((x,c) => c.copy( samples_dir = x )).required()
      opt[ String      ]( "out_dir"     ).action((x,c) => c.copy( out_dir     = x )).required()
      opt[ String      ]( "gold_trees"  ).action((x,c) => c.copy( gold_trees  = x ))
      opt[ Double      ]( "alpha"       ).action((x,c) => c.copy( alpha       = x ))

      help("help").text("prints this usage text")
    }

    parser.parse(args, CMDargs()) match {
      case Some(cmd_args) =>

        Global.printProcessId()

        if(! new File(cmd_args.out_dir).exists())
          new File(cmd_args.out_dir).mkdirs()

        val pwConsensusLab    = new PrintWriter(cmd_args.out_dir+"/CONSENSUS.labelled.trees")
        val pwConsensusUnLab  = new PrintWriter(cmd_args.out_dir+"/CONSENSUS.unlabelled.trees")
        val pwArgmax          = new PrintWriter(cmd_args.out_dir+"/argmax.trees")

        val pwBestTrees       = new PrintWriter(cmd_args.out_dir+"/oracle_best.trees")
        val pwWorstTrees      = new PrintWriter(cmd_args.out_dir+"/oracle_worst.trees")
        val goldTrees: Option[Array[TrainInstance]] = Option(cmd_args.gold_trees).map(DerivationsLoader.fromFile(_).toArray)

        val labelledMetric   = {tree:TreeNode => tree.deps(hockenDeps = false)                                 }
        val unLabelledMetric = {tree:TreeNode => tree.deps(hockenDeps = false).map(_.toUnlabelledAndUndirected)}

        // for(file <- new File(cmd_args.samples_dir).listFiles().sortBy(_.getName.toInt)){
        for(file <- MainRescoreSamples.sortedSampleFilesFromDir(cmd_args.samples_dir)){
          val sentId = file.getName.toInt
          if(sentId % 10 == 0)
            System.err.println(s"consensus processing $sentId ")
          val samples: List[(Double, TreeNode)] = renormalizeSamples(MainRescoreSamples.loadSamples(file).toList.filterNot(_._1.isNaN), cmd_args.alpha)

          pwConsensusLab   println consensusFindBest(samples, labelledMetric).toCCGbankString
          pwConsensusUnLab println consensusFindBest(samples, unLabelledMetric).toCCGbankString
          pwArgmax         println samples.maxBy(_._1)._2.toCCGbankString

          for(goldTree <- goldTrees.map(_(sentId))){
            val measuredSamples: Seq[(TrainInstance, Double)] = for((_, sample) <- samples) yield (sample, fScore(labelledMetric)(sample, goldTree))
            pwBestTrees  println measuredSamples.maxBy(_._2)._1.toCCGbankString
            pwWorstTrees println measuredSamples.minBy(_._2)._1.toCCGbankString
          }
        }

        pwConsensusLab.close()
        pwConsensusUnLab.close()
        pwArgmax.close()
        pwBestTrees.close()
        pwWorstTrees.close()

        System.err.println("Consensus done")

        val pwMBRLab    = new PrintWriter(cmd_args.out_dir+"/MBR.labelled.trees")
        val pwMBRUnLab  = new PrintWriter(cmd_args.out_dir+"/MBR.unlabelled.trees")

        for(file <- MainRescoreSamples.sortedSampleFilesFromDir(cmd_args.samples_dir)) {
          val sentId = file.getName.toInt
          if(sentId % 10 == 0)
            System.err.println(s"MBR processing $sentId ")
          val samples: List[(Double, TreeNode)] = renormalizeSamples(MainRescoreSamples.loadSamples(file).toList.filterNot(_._1.isNaN), cmd_args.alpha)
          val bestLabelled   : TreeNode = mbrTrueFindBest(samples, fScore(labelledMetric))
          val bestUnLabelled : TreeNode = mbrTrueFindBest(samples, fScore(unLabelledMetric))
          pwMBRLab.println(bestLabelled.toCCGbankString)
          pwMBRUnLab.println(bestUnLabelled.toCCGbankString)
        }

        pwMBRLab.close()
        pwMBRUnLab.close()

        System.err.println("MBR done")

      case None =>
        System.err.println("You didn't specify all the required arguments")
        System.exit(-1)
    }
  }

  private def renormalizeSamples[T](samples: List[(Double, T)], alpha:Double) : List[(Double, T)] =
    if(alpha<0){
      samples
    }else{
      val logNormalizer = logSumExp(samples.map(_._1*alpha))
      samples.map{case (log, tree) => (log*alpha-logNormalizer, tree)}
    }

  private def fScore[T, K](metricComponents: T => List[K])(sys:T, ref:T): Double = {
    val sysParts:Map[K, Int] = metricComponents(sys).groupBy(identity).mapValues(_.size).withDefaultValue(0)
    val refParts:Map[K, Int] = metricComponents(ref).groupBy(identity).mapValues(_.size).withDefaultValue(0)
    val overlap:Double = sysParts.keys.toList.map( k => math.min(sysParts(k), refParts(k))).sum
    val p = if(sysParts.nonEmpty) overlap/sysParts.size else 0.0
    val r = if(refParts.nonEmpty) overlap/refParts.size else 0.0
    if(p+r>0)
      2*p*r/(p+r)
    else
      0.0
  }

  private def mbrTrueFindBest[T](samples: List[(Double, T)], metric: (T, T) => Double): T =
    samples.maxBy{ case (_, sys) =>
      logSumExp(samples.map{ case (logPref, ref) => math.log(metric(sys, ref)) + logPref })
    }._2
//    samples.maxBy{ case (_, sys) =>
//      samples.map{ case (logPref, ref) =>
//        metric(sys, ref)*math.exp(logPref)
//      }.sum
//    }._2

  private def consensusFindBest[T, K](samples: List[(Double, T)], metricComponents: T => List[K]): T = {
    val expectations = MutMap[K, Double]().withDefaultValue(Double.NegativeInfinity)

    for((logScore, sample) <- samples){
      for(p <- metricComponents(sample)){
        expectations(p) = logSumExp(expectations(p), logScore)
      }
    }

    samples.map{ case (_, sample) =>
      val parts = metricComponents(sample)
      if(parts.isEmpty)
        (Double.NegativeInfinity, sample)
      else
        (logSumExp(parts map expectations), sample)
    }.maxBy(_._1)._2
  }


}


