package edin.supertagger

import java.io.PrintWriter

import edin.algorithms.Zipper
import edin.nn.DynetSetup
import edin.algorithms.AutomaticResourceClosing.linesFromFile
import edin.general.Global
import edin.nn.embedder.SequenceEmbedderExternal

object MainApply {

  case class CMDargs(
                      model_dirs                     : List[String] = null,
                      input_file_words               : String       = null,
                      input_file_aux_tags            : String       = null,
                      output_file                    : String       = null,
                      output_file_best_k             : String       = null,
                      topK                           : Int          = 1,
                      topBeta                        : Float        = -1f,
                      embeddings_contextual_external : String       = null,
                      dynet_mem                      : String       = null,
                      dynet_autobatch                : Int          = 0
                    )

  def main(args:Array[String]) : Unit = {
    val parser = new scopt.OptionParser[CMDargs](SUPERTAGGER_NAME) {
      head(SUPERTAGGER_NAME, SUPERTAGGER_VERSION.toString)
      opt[ Seq[String] ]( "model_dirs"                ).action((x,c) => c.copy( model_dirs            = x.toList )).required()
      opt[ String      ]( "input_file_words"          ).action((x,c) => c.copy( input_file_words      = x        )).required()
      opt[ String      ]( "input_file_aux_tags"       ).action((x,c) => c.copy( input_file_aux_tags   = x        ))
      opt[ String      ]( "output_file"               ).action((x,c) => c.copy( output_file           = x        )).required()
      opt[ String      ]( "output_file_best_k"        ).action((x,c) => c.copy( output_file_best_k    = x        ))
      opt[ String      ]( "embedding_contextual_file" ).action((x,c) => c.copy( embeddings_contextual_external = x ))
      opt[ Int         ]( "top_K"                     ).action((x,c) => c.copy( topK                  = x        ))
      opt[ Double      ]( "top_beta"                  ).action((x,c) => c.copy( topBeta               = x.toFloat))
      opt[ Int         ]( "dynet-autobatch"           ).action((x,c) => c.copy( dynet_autobatch       = x        ))
      opt[ String      ]( "dynet-mem"                 ).action((x,c) => c.copy( dynet_mem             = x        ))
      help("help").text("prints this usage text")
    }

    parser.parse(args, CMDargs()) match {
      case Some(cmd_args) =>


        require(!(cmd_args.topK>1 || cmd_args.topBeta>0.0) || cmd_args.output_file_best_k != null, "If you want k-best you must also specify --output_file_best_k")

        Global.printProcessId()

        DynetSetup.init_dynet(
          dynet_mem = cmd_args.dynet_mem,
          autobatch = cmd_args.dynet_autobatch)

        if(cmd_args.embeddings_contextual_external != null)
          SequenceEmbedderExternal.loadEmbeddings(cmd_args.embeddings_contextual_external)

        val ensamble = new Ensamble(cmd_args.model_dirs)
        require(!ensamble.isWithAuxTags || cmd_args.input_file_aux_tags!=null, "missing the auxiliary tags file")

        val outFh = new PrintWriter(cmd_args.output_file)
        val outBestKFh = if(cmd_args.output_file_best_k == null) null else new PrintWriter(cmd_args.output_file_best_k)

        val words_iterator = linesFromFile(cmd_args.input_file_words).toIterable
        val auxTags_iterator = if(cmd_args.input_file_aux_tags != null){
          linesFromFile(cmd_args.input_file_words).toIterable
        }else{
          Stream.from(1).map{_ => ""}
        }
        for( (line, aux_line, lineId)  <- Zipper.zip3(words_iterator, auxTags_iterator, Stream.from(1)) ){
          if(lineId % 10 == 0){
            System.err.println(s"processing $lineId")
          }
          val words = line.split(" +").toList
          val auxs = if(aux_line == null || aux_line == "") words.map{_ => null} else aux_line.asInstanceOf[String].split(" +").toList
          val tags  = ensamble.predictBestTagSequence(words, auxs)
          outFh.println(tags.mkString(" "))
          if(outBestKFh != null){
            val tagssWithScores:List[List[(String, Double)]] = if(cmd_args.topBeta > 0){
              ensamble.predictBetaBestTagSequenceWithScores(words, auxs, cmd_args.topBeta)
            }else{
              ensamble.predictKBestTagSequenceWithScores(words, auxs, cmd_args.topK)
            }
            for((word, tagsWithScores) <- words zip tagssWithScores){
                val tagOut = tagsWithScores.map{case (tag, score) => s"$tag $score"}.mkString("\t")
                outBestKFh.println(word+"\tX\t"+tagOut)
            }
            outBestKFh.println()
          }
        }
        outFh.close()
        if(outBestKFh != null)
          outBestKFh.close()
        System.err.println("DONE")
      case None =>
        System.err.println("You didn't specify all the required arguments")
        System.exit(-1)
    }
  }

}
