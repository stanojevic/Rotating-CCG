package edin.ccg

import java.io.File

import edin.algorithms.evaluation.LogProbAggregator
import edin.nn.embedder.SequenceEmbedderELMo
import edin.algorithms.Math

import scala.io.Source

object MainLangModel {

  case class CMDargs(
                      gen_samples_dir    : String         = null,
                      disc_samples_dir   : String         = null
                    )

  def main(args:Array[String]) : Unit = {
    val parser = new scopt.OptionParser[CMDargs](PROGRAM_NAME) {
      head(PROGRAM_NAME, PROGRAM_VERSION.toString)

      opt[ String      ]( "gen_samples_dir"    ).action((x,c) => c.copy( gen_samples_dir          = x        )).required()
      opt[ String      ]( "disc_samples_dir"   ).action((x,c) => c.copy( disc_samples_dir         = x        )).required()

      help("help").text("prints this usage text")
    }

    parser.parse(args, CMDargs()) match {
      case Some(cmd_args) =>

        val logProbAgg = new LogProbAggregator()

        for(discFile <- new File(cmd_args.disc_samples_dir).listFiles().sortBy(_.getName.toInt)){
          val sentId = discFile.getName.toInt
          val genFile = new File(s"${cmd_args.gen_samples_dir}/$sentId")
          System.err.print(s"processing $sentId ")
          val discScores = loadSampleScores(discFile)
          val genScores = loadSampleScores(genFile)
          val words = MainRescoreSamples.loadSamples(discFile).next()._2.words
          val scores = (discScores zip genScores).filter(x => ! x._1.isNaN && ! x._2.isNaN)

          if(scores.isEmpty){
            System.err.println(s"skiping sentence $sentId because of NaN samples")
          }else{
            System.err.println()
            var sentLogProb = importanceSamplingLog(scores)
            if(sentLogProb > 0)
              sentLogProb = 0 // just checking that importance sampling didn't create probs > 1
            logProbAgg.addLogProb(sentLogProb, words.size)
          }
        }

        println()
        println(logProbAgg.reportStringWithPerpl)
        System.err.println(logProbAgg.reportStringWithPerpl)

        SequenceEmbedderELMo.endServer()

      case None =>
        System.err.println("You didn't specify all the required arguments")
        System.exit(-1)
    }
  }

  private def loadSampleScores(f:File) : List[Double] =
    Source.fromFile(f).getLines().map{ line =>
      line.split(" ").head.toDouble
    }.toList

  private def importanceSamplingLog(samples:List[(Double, Double)]) : Double = {
    Math.logSumExp(samples.map{ case (logQ, logP) =>
      assert(logQ<=0 && logP<=0)
      logP-logQ
    }) - math.log(samples.size)
  }

}
