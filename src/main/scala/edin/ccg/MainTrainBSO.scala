package edin.ccg

import edin.ccg.parsing.RevealingModelBSO
import edin.nn.DynetSetup
import edin.general.{Global, TrainingController}

object MainTrainBSO {

  case class CMDargs(
                      original_model_dir          : String    = null,
                      bso_model_dir               : String    = null,
                      bso_hyper_params_file    : String    = null,
                      train_file                  : String    = null,
                      dev_file                    : String    = null,
                      epochs                      : Int       = 10,
                      all_in_memory               : Boolean   = true,
                      dynet_mem                   : String    = null,
                      dynet_autobatch             : Int       = 0
                    )

  def main(args:Array[String]) : Unit = {
    val parser = new scopt.OptionParser[CMDargs](PROGRAM_NAME) {
      head(PROGRAM_NAME, PROGRAM_VERSION.toString)
      opt[ String  ]( "original_model_dir"          ).action((x,c) => c.copy( original_model_dir          = x )).required()
      opt[ String  ]( "bso_model_dir"               ).action((x,c) => c.copy( bso_model_dir               = x )).required()
      opt[ String  ]( "bso_hyper_params_file"       ).action((x,c) => c.copy( bso_hyper_params_file       = x )).required()
      opt[ String  ]( "train_file"                  ).action((x,c) => c.copy( train_file                  = x )).required()
      opt[ String  ]( "dev_file"                    ).action((x,c) => c.copy( dev_file                    = x )).required()
      opt[ Int     ]( "epochs"                      ).action((x,c) => c.copy( epochs                      = x )).required()
      opt[ Boolean ]( "all_in_memory"               ).action((x,c) => c.copy( all_in_memory               = x ))
      opt[ Int     ]( "dynet-autobatch"             ).action((x,c) => c.copy( dynet_autobatch             = x ))
      opt[ String  ]( "dynet-mem"                   ).action((x,c) => c.copy( dynet_mem                   = x ))
      help("help").text("prints this usage text")
    }


    parser.parse(args, CMDargs()) match {
      case Some(cmd_args) =>

        Global.printProcessId()

        DynetSetup.init_dynet(
          dynet_mem = cmd_args.dynet_mem,
          autobatch = cmd_args.dynet_autobatch,
          seed      = 2802152667L)

        val model = new RevealingModelBSO( originalModelDir  = cmd_args.original_model_dir )

        new TrainingController(
          continueTraining = false,  // Boolean,
          epochs           = cmd_args.epochs,  // Int,
          trainFile        = cmd_args.train_file,  // String,
          devFile          = cmd_args.dev_file,  // String,
          modelDir         = cmd_args.bso_model_dir,  // String,
          hyperFile        = cmd_args.bso_hyper_params_file,  // String,
          modelContainer   = model,  // ModelContainer[I],
          allInMemory      = cmd_args.all_in_memory   // Boolean
        ).train()

      case None =>
        System.err.println("You didn't specify all the required arguments")
        System.exit(-1)
    }
  }

}
