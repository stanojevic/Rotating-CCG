package edin.supertagger

import edin.algorithms.AutomaticResourceClosing.linesFromFile

object MainEvaluateNBest {

  case class CMDargs(
                      gold_file : String = null,
                      pred_file : String = null,
                      maxK      : Int    = 5
                    )

  def main(args:Array[String]) : Unit = {

    val parser = new scopt.OptionParser[CMDargs](SUPERTAGGER_NAME) {
      head(SUPERTAGGER_NAME, SUPERTAGGER_VERSION.toString)
      opt[String]("gold_file"           ).action((x, c) => c.copy(gold_file = x)).required()
      opt[String]("predicted_file_nbest").action((x, c) => c.copy(pred_file = x)).required()
      opt[Int   ]("maxK"                ).action((x, c) => c.copy(maxK      = x)).required()
    }

    parser.parse(args, CMDargs()) match {
      case Some(cmd_args) =>

        val allGoldTags:List[List[String]] = loadTokens(cmd_args.gold_file)
        val allPredTags:List[List[List[String]]] = loadNBest(cmd_args.pred_file)
        assert(allGoldTags.size == allPredTags.size)

        val maxK = cmd_args.maxK
        val correct = Array.fill(maxK+1)(0.0)
        var total   = 0.0
        for((goldTags, predTags) <- (allGoldTags zip allPredTags)){
          assert(goldTags.size == predTags.size)
          for(k <- 1 to maxK){
            correct(k) += (goldTags zip predTags).count{case (g, p) =>
              p.take(k) contains g
            }
          }
          total += goldTags.size
        }

        val precisions = correct.map{_/total}
        for(k <- 1 to maxK){
          println(s"precision $k : ${precisions(k)}")
          // System.err.println(s"precision $k : ${precisions(k)}")
        }

      case None =>
        System.err.println("You didn't specify all the required arguments")
        System.exit(-1)
    }

  }

  def loadNBest(fn:String) : List[List[List[String]]] = {
    var res = List[List[List[String]]]()
    var curr = List[List[String]]()
    for(line <- linesFromFile(fn)){
      if(line.isEmpty){
        res ::= curr.reverse
        curr = List()
      }else{
        curr ::= line.split("\t").toList.tail.tail.map{entry =>
          val fields = entry.split(" ")
          fields(0)
        }
      }
    }
    if(curr.nonEmpty){
      res ::= curr.reverse
      curr = List()
    }
    res.reverse
  }

  def loadTokens(fn:String) : List[List[String]] =
    linesFromFile(fn).map{ line =>
      line.split(" +").toList
    }.toList

}

