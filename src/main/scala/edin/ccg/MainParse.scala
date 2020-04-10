package edin.ccg

import java.io.PrintWriter

import edin.ccg.parsing.{Parser, RevealingModel}
import edin.general.Global
import edin.nn.DynetSetup
import Global.{printMessageWithTime, printProcessId}
import edin.algorithms.AutomaticResourceClosing.linesFromFile

object MainParse {

  case class CMDargs(
                      input_file_words  : String         =         null,
                      output_file       : String         =         null,
                      model_dirs        : Seq[String]    =         null,

                      max_stack_size    : Int            = Int.MaxValue,

                      beamType          : String         =   "wordSync",
                      kMidParsing       : Int            =            1,
                      kOutWord          : Int            =            1,
                      beamRescaled      : Boolean        =        false,
                      kOutParsing       : Int            =            1,
                      kOutTagging       : Int            =            1,

                      dynet_mem         : String         =         null,
                      dynet_autobatch   : Int            =            0
                    )

  def main(args:Array[String]) : Unit = {
    val parser = new scopt.OptionParser[CMDargs](PROGRAM_NAME) {
      head(PROGRAM_NAME, PROGRAM_VERSION.toString)

      opt[ String      ]( "input_file"            ).action((x,c) => c.copy( input_file_words     = x )).required
      opt[ String      ]( "output_file"           ).action((x,c) => c.copy( output_file          = x )).required
      opt[ Seq[String] ]( "model_dirs"            ).action((x,c) => c.copy( model_dirs           = x )).required

      opt[ Int         ]( "max_stack_size"        ).action((x,c) => c.copy( max_stack_size       = x ))

      opt[ Boolean     ]("beam-rescaled"          ).action((x,c) => c.copy( beamRescaled         = x ))
      opt[ String      ]("beam-type"              ).action((x,c) => c.copy( beamType             = x )).required
      opt[ Int         ]("beam-mid-parsing"       ).action((x,c) => c.copy( kMidParsing          = x )).required
      opt[ Int         ]("beam-out-parsing"       ).action((x,c) => c.copy( kOutParsing          = x )).required
      opt[ Int         ]("beam-out-word"          ).action((x,c) => c.copy( kOutWord             = x ))
      opt[ Int         ]("beam-out-tagging"       ).action((x,c) => c.copy( kOutTagging          = x ))

      opt[ Int         ]( "dynet-autobatch"       ).action((x,c) => c.copy( dynet_autobatch      = x ))
      opt[ String      ]( "dynet-mem"             ).action((x,c) => c.copy( dynet_mem            = x ))

      help("help").text("prints this usage text")
    }

    parser.parse(args, CMDargs()) match {
      case Some(cmd_args) =>
        printProcessId()

        DynetSetup.init_dynet(
          dynet_mem = cmd_args.dynet_mem,
          autobatch = cmd_args.dynet_autobatch)

        printMessageWithTime("model loading started")
        val models = cmd_args.model_dirs.map { modelDir =>
          val model = new RevealingModel()
          model.loadFromModelDir(modelDir)
          model
        }
        printMessageWithTime("parsing started")
        val pw = new PrintWriter(cmd_args.output_file)

        for((line, i) <- linesFromFile(cmd_args.input_file_words).zipWithIndex){
          DynetSetup.cg_renew()
          if(i%1 == 0)
            System.err.println(s"processing $i")
          val words = line.split(" +").toList

          val bestTree = Parser.parse(
            sent         = words,
            beamType     = cmd_args.beamType,
            beamRescaled = cmd_args.beamRescaled,
            kMidParsing  = cmd_args.kMidParsing,
            kOutParsing  = cmd_args.kOutParsing,
            kOutWord     = cmd_args.kOutWord,
            kOutTagging  = cmd_args.kOutTagging,
            maxStackSize = cmd_args.max_stack_size
          )(models.toList)

          pw.println(bestTree.toCCGbankString)
          pw.flush()
        }
        pw.close()
        printMessageWithTime("parsing finished")
      case None =>
        System.err.println("You didn't specify all the required arguments")
        System.exit(-1)
    }

  }

}
