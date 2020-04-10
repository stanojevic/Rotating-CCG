package edin.ccg

import java.io.PrintWriter

import edin.ccg.representation.DerivationsLoader
import edin.supertagger.{SUPERTAGGER_NAME, SUPERTAGGER_VERSION}

object MainExtractTagsCCGFile {

  case class CMDargs(
                      ccg_file : String   = null,
                      out_file : String   = null
                    )

  def main(args:Array[String]) : Unit = {

    val parser = new scopt.OptionParser[CMDargs](SUPERTAGGER_NAME) {
      head(SUPERTAGGER_NAME, SUPERTAGGER_VERSION.toString)
      opt[String]("ccg_file").action((x, c) => c.copy(ccg_file = x)).required()
      opt[String]("out_file").action((x, c) => c.copy(out_file = x)).required()
    }

    parser.parse(args, CMDargs()) match {
      case Some(cmd_args) =>

        val pwTags = new PrintWriter(cmd_args.out_file+".tags")
        val pwWords = new PrintWriter(cmd_args.out_file+".words")
        for(tree <- DerivationsLoader.fromFile(cmd_args.ccg_file)){
          pwTags.println(tree.leafs.map(_.cat.toString).mkString(" "))
          pwWords.println(tree.leafs.map(_.word).mkString(" "))
        }
        pwTags.close()
        pwWords.close()

      case None =>
        System.err.println("You didn't specify all the required arguments")
        System.exit(-1)
    }

  }

}
