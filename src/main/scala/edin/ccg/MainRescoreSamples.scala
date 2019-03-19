package edin.ccg

import java.io.{File, PrintWriter}
import java.lang.management.ManagementFactory
import java.text.SimpleDateFormat
import java.util.Date

import edin.ccg.parsing.{Parser, ParserState, RevealingModel}
import edin.ccg.representation.tree.TreeNode
import edin.ccg.transitions.{BinaryReduceOption, ShiftOption, TransitionOption, UnaryReduceOption}
import edin.nn.DynetSetup
import edin.nn.embedder.SequenceEmbedderELMo
import edin.search.{EnsamblePredictionState, PredictionState}
import edu.cmu.dynet.Expression
import edin.ccg.representation.DerivationsLoader
import edin.ccg.representation.combinators.TypeChangeBinary
import edin.nn.DyFunctions._

import scala.io.Source
import scala.util.{Failure, Random, Success, Try}

object MainRescoreSamples {

  case class CMDargs(
                      input_samples_dir  : String         = null,
                      output_samples_dir : String         = null,
                      gen_model_dirs     : List[String]   = null,

                      dynet_mem          : String         = null,
                      dynet_weight_decay : Float          = 0.0f,
                      dynet_autobatch    : Int            =    0,
                      dynet_gpus         : List[Int]      =  Nil
                    )

  def main(args:Array[String]) : Unit = {
    val parser = new scopt.OptionParser[CMDargs](PROGRAM_NAME) {
      head(PROGRAM_NAME, PROGRAM_VERSION.toString)

      opt[ String      ]( "samples_dir_input"  ).action((x,c) => c.copy( input_samples_dir    = x        )).required()
      opt[ String      ]( "samples_dir_output" ).action((x,c) => c.copy( output_samples_dir   = x        )).required()

      opt[ Seq[String] ]( "gen_model_dirs"     ).action((x,c) => c.copy( gen_model_dirs       = x.toList )).required()

      opt[ Int         ]( "dynet-autobatch"    ).action((x,c) => c.copy( dynet_autobatch      = x        ))
      opt[ String      ]( "dynet-mem"          ).action((x,c) => c.copy( dynet_mem            = x        ))
      opt[ Seq[Int]    ]( "dynet-gpus"         ).action((x,c) => c.copy( dynet_gpus           = x.toList ))

      help("help").text("prints this usage text")
    }

    parser.parse(args, CMDargs()) match {
      case Some(cmd_args) =>

        System.err.println("\nprocess identity: "+ManagementFactory.getRuntimeMXBean.getName+"\n")

        DynetSetup.init_dynet(
          cmd_args.dynet_mem,
          cmd_args.dynet_weight_decay,
          cmd_args.dynet_autobatch,
          cmd_args.dynet_gpus)

        val gen_models = cmd_args.gen_model_dirs.map { modelDir =>
          val model = new RevealingModel()
          model.loadFromModelDir(modelDir)
          model.parserProperties = model.parserProperties.copy(withObservedCatsOnly = false)
          model
        }

        if(!new File(cmd_args.output_samples_dir).exists())
          new File(cmd_args.output_samples_dir).mkdirs()

        if(!new File(cmd_args.output_samples_dir+"_beforeShift").exists())
          new File(cmd_args.output_samples_dir+"_beforeShift").mkdirs()

        val ft = new SimpleDateFormat ("HH:mm dd.MM.yyyy")
        System.err.println(s"rescoring started at ${ft.format(new Date())}")

        var failures = 0

        for(file <- new File(cmd_args.input_samples_dir).listFiles().sortBy(_.getName.toInt)){
          val sentId = file.getName.toInt
          System.err.print(s"processing $sentId ")
          val pw = new PrintWriter(s"${cmd_args.output_samples_dir}/$sentId")
          val pwBefore = new PrintWriter(s"${cmd_args.output_samples_dir}_beforeShift/$sentId")
          loadSamples(file).map(_._2).foreach{ tree =>
            DynetSetup.cg_renew()
            System.err.print(".")
            Try(genReScore(gen_models, tree)) match {
              case Success((beforeShiftExp, afterShiftExp)) =>
                pw.println(afterShiftExp.toDouble+" "+tree.toCCGbankString)
                pwBefore.println(beforeShiftExp.toDouble+" "+tree.toCCGbankString)
              case Failure(exception) =>
                failures += 1
                System.err.println(s"failure $failures: "+exception.getMessage)
                pw.println(Double.NaN+" "+tree.toCCGbankString)
                pwBefore.println(Double.NaN+" "+tree.toCCGbankString)
            }
          }
          pw.close()
          pwBefore.close()

          System.err.println()
        }
        System.err.println(s"rescoring finished at ${ft.format(new Date())}")

        SequenceEmbedderELMo.endServer()

        System.err.println("DONE")

      case None =>
        System.err.println("You didn't specify all the required arguments")
        System.exit(-1)
    }
  }

  def loadSamples(f:File) : Iterator[(Double, TreeNode)] =
    Source.fromFile(f).getLines().map{ line =>
      val (a, b) = line.splitAt(line.indexOf(" "))
      (a.toDouble, DerivationsLoader.fromString(b))
    }

  private def genReScore(genModels:List[RevealingModel], tree:TreeNode) : (Expression, Expression) = {
    val words :List[String] = tree.words
    var wordsLeft:List[Int] = words.map(genModels.head.allS2I.w2i_tgt_gen(_))
    var transLeft:List[TransitionOption] = genModels.head.parserProperties.findDerivation(tree)

    var currParserState:PredictionState = new EnsamblePredictionState(
      states = genModels.map{model => Parser.initParserState(words)(model)}
    )

    while(transLeft.nonEmpty){
      val t = transLeft.head
      val conf = currParserState.unwrapState[ParserState].conf
      val t_i = conf.transitionLogDistribution._1.zipWithIndex.find(_._1 == t) match {
        case Some((_, i)) => i
        case None =>
          val r = Random.nextInt()
          conf.saveVisualStackState(s"gen_model_failed_tree_$r")
          tree.saveVisual(s"gen_model_failed_tree_$r.gold.tree")
          genModels.head.parserProperties.prepareDerivationTreeForTraining(tree).saveVisual(s"gen_model_failed_tree_$r.transformed.tree")
          throw new Exception(s"failed to find the sampled transition $t in the generative model")
      }
      currParserState = currParserState.applyAction(t_i)
      if(t == ShiftOption()){
        val w_i = wordsLeft.head
        wordsLeft = wordsLeft.tail
        currParserState = currParserState.applyAction(w_i)
      }
      transLeft = transLeft.tail
    }

    val t_i = currParserState.unwrapState[ParserState].conf.transitionLogDistribution._1.zipWithIndex.find(_._1 == ShiftOption()).get._2
    currParserState = currParserState.applyAction(t_i)

    (currParserState.scoreExp, currParserState.applyAction(genModels.head.allS2I.w2i_tgt_gen.EOS_i).scoreExp)
  }

}
