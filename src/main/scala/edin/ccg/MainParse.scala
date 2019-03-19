package edin.ccg

import java.io.PrintWriter
import java.lang.management.ManagementFactory
import java.text.SimpleDateFormat
import java.util.Date

import edin.ccg.parsing.{Parser, ParserState, RevealingModel}
import edin.nn.DynetSetup
import edin.nn.embedder.SequenceEmbedderELMo
import edin.search.EnsamblePredictionState

import scala.io.Source

object MainParse {

  case class CMDargs(
                      input_file_words  : String         = null,
                      output_file_words : String         = null,
                      model_dirs        : List[String]   = null,

                      parsingBeamSize   : Int            =    1,
                      wordBeamSize      : Int            =    0,
                      fastTrackBeamSize : Int            =    0,

                      dynet_mem             : String     = null,
                      dynet_weight_decay    : Float      = 0.0f,
                      dynet_autobatch       : Int        =    0,
                      dynet_gpus            : List[Int]  = List()
                    )

  def main(args:Array[String]) : Unit = {
    val parser = new scopt.OptionParser[CMDargs](PROGRAM_NAME) {
      head(PROGRAM_NAME, PROGRAM_VERSION.toString)

      opt[ String      ]( "input_file"         ).action((x,c) => c.copy( input_file_words     = x        )).required()
      opt[ String      ]( "output_file"        ).action((x,c) => c.copy( output_file_words    = x        )).required()
      opt[ Seq[String] ]( "model_dirs"         ).action((x,c) => c.copy( model_dirs           = x.toList )).required()

      opt[ Int         ]( "beam-parsing"       ).action((x,c) => c.copy( parsingBeamSize      = x        )).text("k")
      opt[ Int         ]( "beam-word"          ).action((x,c) => c.copy( wordBeamSize         = x        )).text("k_wd")
      opt[ Int         ]( "beam-fasttrack"     ).action((x,c) => c.copy( fastTrackBeamSize    = x        )).text("k_ft")

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

        val ft = new SimpleDateFormat ("HH:mm dd.MM.yyyy")

        System.err.println(s"model loading started at ${ft.format(new Date())}")
        val models = cmd_args.model_dirs.map { modelDir =>
          val model = new RevealingModel()
          model.loadFromModelDir(modelDir)
          model
        }
        System.err.println(s"parsing started at ${ft.format(new Date())}")

        val pw = new PrintWriter(cmd_args.output_file_words)

        for((line, i) <- Source.fromFile(cmd_args.input_file_words).getLines().zipWithIndex){
          DynetSetup.cg_renew()
          if(i%1 == 0)
            System.err.println(s"processing $i")
          val words = line.split(" +").toList
          val searcher = Parser.searcherForModel(
            model = models.head,
            sent = words,
            parsingBeamSize = cmd_args.parsingBeamSize,
            wordBeamSize = cmd_args.wordBeamSize,
            fastTrackBeamSize = cmd_args.fastTrackBeamSize
          )
          val parserState = new EnsamblePredictionState(
            states = models.map{model => Parser.initParserState(words)(model)}
          )
          val bestStates = searcher(parserState::Nil).map(_.unwrapState[ParserState])
          val bestTree = bestStates.maxBy(_.score).conf.extractTree
          pw.println(bestTree.toCCGbankString)
          pw.flush()
        }
        pw.close()
        SequenceEmbedderELMo.endServer()
        System.err.println(s"parsing finished at ${ft.format(new Date())}")
      case None =>
        System.err.println("You didn't specify all the required arguments")
        System.exit(-1)
    }

  }

}
