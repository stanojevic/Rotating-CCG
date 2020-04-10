package edin.supertagger

import edin.general.{Global, TrainingController}
import edin.nn.DynetSetup
import edin.nn.embedder.SequenceEmbedderExternal

object MainTrain {

  /*
    java -cp ./target/scala-2.11/CCG-translator-assembly-0.1.jar edin.ccg.supertagger.MainTrain
    --model_dir           /afs/inf.ed.ac.uk/user/m/mstanoje/Data_CCG_tagger/tagging_models/model_1
    --hyper_params_file   /afs/inf.ed.ac.uk/user/m/mstanoje/experiments/CCG-translator/configs/supertagger_model_desc.yaml
    --embedding_file      /afs/inf.ed.ac.uk/user/m/mstanoje/Data_CCG_tagger/glove.42B.300d.txt
    --train_file          /afs/inf.ed.ac.uk/user/m/mstanoje/Data_CCG_tagger/tagging_data/train
    --dev_file            /afs/inf.ed.ac.uk/user/m/mstanoje/Data_CCG_tagger/tagging_data/dev
    --epochs              100
    --all_in_memory       true
    --dynet-mem           10000
   */

  case class CMDargs(
                      model_dir                      : String    = null,
                      embedding_file                 : String    = null,
                      embeddings_dim                 : Int       = -1,
                      embeddings_lowercased          : Boolean   = false,
                      embeddings_contextual_external : String    = null,
                      train_file                     : String    = null,
                      dev_file                       : String    = null,
                      epochs                         : Int       = 10,
                      hyper_params_file              : String    = null,
                      all_in_memory                  : Boolean   = true,
                      tagMinCount                    : Int       = 0,
                      tagMaxVoc                      : Int       = Int.MaxValue,
                      dynet_mem                      : String    = null,
                      dynet_autobatch                : Int       = 0
                    )

  def main(args:Array[String]) : Unit = {
    val parser = new scopt.OptionParser[CMDargs](SUPERTAGGER_NAME) {
      head(SUPERTAGGER_NAME, SUPERTAGGER_VERSION.toString)
      opt[ String   ]( "model_dir"                 ).action((x,c) => c.copy( model_dir                      = x )).required()
      opt[ String   ]( "hyper_params_file"         ).action((x,c) => c.copy( hyper_params_file              = x )).required()
      opt[ String   ]( "embedding_contextual_file" ).action((x,c) => c.copy( embeddings_contextual_external = x ))
      opt[ String   ]( "embedding_file"            ).action((x,c) => c.copy( embedding_file                 = x ))
      opt[ Boolean  ]( "embeddings_lowercased"     ).action((x,c) => c.copy( embeddings_lowercased          = x ))
      opt[ Int      ]( "embeddings_dim"            ).action((x,c) => c.copy( embeddings_dim                 = x ))
      opt[ String   ]( "train_file"                ).action((x,c) => c.copy( train_file                     = x )).required()
      opt[ String   ]( "dev_file"                  ).action((x,c) => c.copy( dev_file                       = x )).required()
      opt[ Int      ]( "epochs"                    ).action((x,c) => c.copy( epochs                         = x )).required()
      opt[ Int      ]( "tag_min_count"             ).action((x,c) => c.copy( tagMinCount                    = x ))
      opt[ Int      ]( "tag_max_vocabulary"        ).action((x,c) => c.copy( tagMaxVoc                      = x ))
      opt[ Boolean  ]( "all_in_memory"             ).action((x,c) => c.copy( all_in_memory                  = x ))
      opt[ Int      ]( "dynet-autobatch"           ).action((x,c) => c.copy( dynet_autobatch                = x ))
      opt[ String   ]( "dynet-mem"                 ).action((x,c) => c.copy( dynet_mem                      = x ))
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

        if(cmd_args.embeddings_contextual_external != null)
          SequenceEmbedderExternal.loadEmbeddings(cmd_args.embeddings_contextual_external)

        val modelContainer = new SuperTaggingModel(
          embeddingsFile         = cmd_args.embedding_file,
          embeddingsDim          = cmd_args.embeddings_dim,
          lowercased             = cmd_args.embeddings_lowercased,
          tagMaxVoc              = cmd_args.tagMaxVoc,
          tagMinCount            = cmd_args.tagMinCount
        )

        new TrainingController(
          continueTraining = false,
          epochs           = cmd_args.epochs,
          trainFile        = cmd_args.train_file,
          devFile          = cmd_args.dev_file,
          modelDir         = cmd_args.model_dir,
          hyperFile        = cmd_args.hyper_params_file,
          modelContainer   = modelContainer,
          allInMemory      = cmd_args.all_in_memory
        ).train()
      case None =>
        System.err.println("You didn't specify all the required arguments")
        System.exit(-1)
    }
  }

}
