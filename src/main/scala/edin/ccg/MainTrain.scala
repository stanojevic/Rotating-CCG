package edin.ccg

import edin.ccg.parsing.RevealingModel
import edin.general.TrainingController
import edin.nn.DynetSetup
import edin.general.Global

object MainTrain {

  case class CMDargs(
                      model_dir             : String    = null,
                      embedding_file        : String    = null,
                      embeddings_lowercased : Boolean   = false,
                      embeddings_dim        : Int       = -1,
                      train_file            : String    = null,
                      dev_file              : String    = null,
                      epochs                : Int       = 10,
                      hyper_params_file     : String    = null,
                      all_in_memory         : Boolean   = true,
                      language              : String    = "English",
                      dynet_mem             : String    = null,
                      dynet_autobatch       : Int       = 0
                    )

  def main(args:Array[String]) : Unit = {
    val parser = new scopt.OptionParser[CMDargs](PROGRAM_NAME) {
      head(PROGRAM_NAME, PROGRAM_VERSION.toString)
      opt[ String   ]( "model_dir"             ).action((x,c) => c.copy( model_dir             = x         )).required()
      opt[ String   ]( "hyper_params_file"     ).action((x,c) => c.copy( hyper_params_file     = x         )).required()
      opt[ String   ]( "embedding_file"        ).action((x,c) => c.copy( embedding_file        = x         ))
      opt[ Boolean  ]( "embeddings_lowercased" ).action((x,c) => c.copy( embeddings_lowercased = x         ))
      opt[ Int      ]( "embeddings_dim"        ).action((x,c) => c.copy( embeddings_dim        = x         ))
      opt[ String   ]( "train_file"            ).action((x,c) => c.copy( train_file            = x         )).required()
      opt[ String   ]( "dev_file"              ).action((x,c) => c.copy( dev_file              = x         )).required()
      opt[ String   ]( "language"              ).action((x,c) => c.copy( language              = x         )).required()
      opt[ Int      ]( "epochs"                ).action((x,c) => c.copy( epochs                = x         )).required()
      opt[ Boolean  ]( "all_in_memory"         ).action((x,c) => c.copy( all_in_memory         = x         ))
      opt[ Int      ]( "dynet-autobatch"       ).action((x,c) => c.copy( dynet_autobatch       = x         ))
      opt[ String   ]( "dynet-mem"             ).action((x,c) => c.copy( dynet_mem             = x         ))
      help("help").text("prints this usage text")
    }


    parser.parse(args, CMDargs()) match {
      case Some(cmd_args) =>

        assert((cmd_args.embedding_file==null) == (cmd_args.embeddings_dim>0),
          "you can provide ether embedding_file or embeddings_dim but not both")

        Global.printProcessId()

        DynetSetup.init_dynet(
          dynet_mem = cmd_args.dynet_mem,
          autobatch = cmd_args.dynet_autobatch)

        val model = new RevealingModel(
          embeddingsFile = cmd_args.embedding_file,
          embeddingsDim  = cmd_args.embeddings_dim,
          lowercased     = cmd_args.embeddings_lowercased,
          language       = cmd_args.language
        )

        new TrainingController(
          continueTraining = false,  // Boolean,
          epochs           = cmd_args.epochs,  // Int,
          trainFile        = cmd_args.train_file,  // String,
          devFile          = cmd_args.dev_file,  // String,
          modelDir         = cmd_args.model_dir,  // String,
          hyperFile        = cmd_args.hyper_params_file,  // String,
          modelContainer   = model,  // ModelContainer[I],
          allInMemory      = cmd_args.all_in_memory   // Boolean
        ).train()

      case None =>
        System.err.println("You didn't specify all the required arguments")
        System.exit(-1)
    }
  }


}

