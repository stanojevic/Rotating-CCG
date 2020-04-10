package edin.supertagger

import edin.algorithms.AutomaticResourceClosing.linesFromFile

object MainEvaluate {

  case class CMDargs(
                      gold_file : String    = null,
                      pred_file : String    = null
                    )

  def main(args:Array[String]) : Unit = {

    val parser = new scopt.OptionParser[CMDargs](SUPERTAGGER_NAME) {
      head(SUPERTAGGER_NAME, SUPERTAGGER_VERSION.toString)
      opt[String]("gold_file"     ).action((x, c) => c.copy(gold_file = x)).required()
      opt[String]("predicted_file" ).action((x, c) => c.copy(pred_file = x)).required()
    }

    parser.parse(args, CMDargs()) match {
      case Some(cmd_args) =>

        val allGoldTags:List[List[String]] = loadTokens(cmd_args.gold_file)
        val allPredTags:List[List[String]] = loadTokens(cmd_args.pred_file)
        assert(allGoldTags.size == allPredTags.size)

        var correct = 0.0
        var total   = 0.0
        for((goldTags, predTags) <- (allGoldTags zip allPredTags)){
          assert(goldTags.size == predTags.size)
          correct += (goldTags zip predTags).count{case (g, p) => g == p}
          total += goldTags.size
        }

        val precision = correct/total
        println(s"precision $precision")

      case None =>
        System.err.println("You didn't specify all the required arguments")
        System.exit(-1)
    }

  }

  def loadTokens(fn:String) : List[List[String]] =
    linesFromFile(fn).map{ line =>
      line.split(" +").toList
    }.toList

}

