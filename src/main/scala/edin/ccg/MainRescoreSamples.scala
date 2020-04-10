package edin.ccg

import java.io.{File, PrintWriter}

import edin.ccg.parsing.{RevealingModel, RevealingModelBSO}
import edin.ccg.representation.tree.TreeNode
import edin.nn.DynetSetup
import edu.cmu.dynet.Expression
import edin.ccg.representation.DerivationsLoader
import edin.nn.DyFunctions._
import edin.general.Global.{printMessageWithTime, printProcessId}
import edin.algorithms.AutomaticResourceClosing.linesFromFile
import scala.collection.mutable.{Map => MutMap}

import scala.util.{Failure, Success, Try}

object MainRescoreSamples {

  case class CMDargs(
                      input_samples_dir  : String         = null,
                      output_samples_dir : String         = null,
                      gen_model_dirs     : List[String]   = null,

                      dynet_mem          : String         = null,
                      dynet_autobatch    : Int            =    0
                    )

  def main(args:Array[String]) : Unit = {
    val parser = new scopt.OptionParser[CMDargs](PROGRAM_NAME) {
      head(PROGRAM_NAME, PROGRAM_VERSION.toString)

      opt[ String      ]( "samples_dir_input"  ).action((x,c) => c.copy( input_samples_dir    = x        )).required()
      opt[ String      ]( "samples_dir_output" ).action((x,c) => c.copy( output_samples_dir   = x        )).required()

      opt[ Seq[String] ]( "gen_model_dirs"     ).action((x,c) => c.copy( gen_model_dirs       = x.toList )).required()

      opt[ Int         ]( "dynet-autobatch"    ).action((x,c) => c.copy( dynet_autobatch      = x        ))
      opt[ String      ]( "dynet-mem"          ).action((x,c) => c.copy( dynet_mem            = x        ))

      help("help").text("prints this usage text")
    }

    parser.parse(args, CMDargs()) match {
      case Some(cmd_args) =>

        printProcessId()
        DynetSetup.init_dynet(
          dynet_mem = cmd_args.dynet_mem,
          autobatch = cmd_args.dynet_autobatch)

        val gen_models = cmd_args.gen_model_dirs.map { modelDir =>
          val model = new RevealingModel()
          model.loadFromModelDir(modelDir)
          model.parserProperties = model.parserProperties.copy(withObservedCatsOnly = false)
          model
        }

        if(!new File(cmd_args.output_samples_dir).exists())
          new File(cmd_args.output_samples_dir).mkdirs()

        printMessageWithTime("rescoring started")

        var failures = 0

        for(file <- sortedSampleFilesFromDir(cmd_args.input_samples_dir)){
          val sentId = file.getName.toInt
          System.err.print(s"rescoring $sentId ")
          val pw = new PrintWriter(s"${cmd_args.output_samples_dir}/$sentId")
          val cache = MutMap[TreeNode, Double]()
          loadSamples(file).map(_._2).foreach{ tree =>
            DynetSetup.cg_renew()
            System.err.print(".")
            val score = if(cache contains tree){
              cache(tree)
            }else{
              Try(genReScore(gen_models, tree)) match {
                case Success(logProb) =>
                  val lp = logProb.toDouble
                  cache(tree) = lp
                  lp
                case Failure(exception) =>
                  failures += 1
                  System.err.println(s"failure $failures: "+exception.getMessage)
                  Double.NaN
              }
            }
            pw.println(score.toString+" "+tree.toCCGbankString)
          }
          pw.close()

          System.err.println()
        }
        printMessageWithTime("rescoring finished")

        System.err.println("DONE")

      case None =>
        System.err.println("You didn't specify all the required arguments")
        System.exit(-1)
    }
  }

  def sortedSampleFilesFromDir(dir:String) : List[File] =
    new File(dir).
      listFiles().
      filter(x => Try(x.getName.toInt).isSuccess).
      sortBy(_.getName.toInt).
      toList

  def loadSamples(f:File) : Iterator[(Double, TreeNode)] =
    linesFromFile(f).map{ line =>
      val (a, b) = line.splitAt(line indexOf " ")
      (a.toDouble, DerivationsLoader.fromString(b))
    }

  private def genReScore(genModels:List[RevealingModel], tree:TreeNode) : Expression =
    averageLogSoftmaxes( genModels map RevealingModelBSO.computeTreeScore(tree) )

}
