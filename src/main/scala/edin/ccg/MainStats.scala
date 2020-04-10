package edin.ccg

import edin.ccg.representation._
import edin.ccg.representation.category.Category
import edin.ccg.representation.combinators.{Combinator, CombinatorBinary, CombinatorUnary, TypeChangeBinary}
import edin.ccg.representation.transforms.AdjunctionGeneral.RightAdjoinCombinator
import edin.ccg.representation.transforms.{Rebranching, RightSpine}
import edin.ccg.representation.tree._
import edin.ccg.transitions.ParserProperties
import edin.general.YamlConfig

import scala.collection.mutable.{Map => MutMap}

object MainStats {

  case class CMDargs(
                      ccg_file           : String = null,
                      language           : String = "English",
                      hyper_params_file  : String = null
                    )

  def main(args:Array[String]) : Unit = {
    val parser = new scopt.OptionParser[CMDargs](PROGRAM_NAME) {
      head(PROGRAM_NAME, PROGRAM_VERSION.toString)
      opt[ String   ]( "language"           ).action((x,c) => c.copy( language          = x )).required()
      opt[ String   ]( "ccg_file"           ).action((x,c) => c.copy( ccg_file          = x )).required()
      opt[ String   ]( "hyper_params_file"  ).action((x,c) => c.copy( hyper_params_file = x )).required()
      help("help").text("prints this usage text")
    }


    parser.parse(args, CMDargs()) match {
      case Some(cmd_args) =>

        Combinator.setLanguage(cmd_args.language, null)

        val parserProperties = if(cmd_args.hyper_params_file == "original")
          null
        else if(cmd_args.hyper_params_file == "right")
          ParserProperties.prototypeProps.copy(
            useIncrementalTrans = false,
            useRevealing        = false
          )
        else if(cmd_args.hyper_params_file == "left")
          ParserProperties.prototypeProps.copy(
            useIncrementalTrans = true,
            useRevealing        = false
          )
        else if(cmd_args.hyper_params_file == "revealing" || cmd_args.hyper_params_file == "reveal")
          ParserProperties.prototypeProps.copy(
            useIncrementalTrans = true,
            useRevealing        = true
          )
        else
          ParserProperties.fromYaml(YamlConfig.fromFile(cmd_args.hyper_params_file)("parser-properties"))

        var allConnectedness = List[List[Int]]()
        var allWaitingTimes = List[List[Int]]()
        var allAdjunctionInfo = List[List[(Int, Int)]]()
        var allBinaryConstCount = List[Int]()

        val unaryCounts  = MutMap[CombinatorUnary , Int]().withDefaultValue(0)
        val binaryCounts = MutMap[CombinatorBinary, Int]().withDefaultValue(0)
        val tagsCounts = MutMap[Category, Int]().withDefaultValue(0)

        var weirdUnarySentIds  = List[Int]()
        var weirdBinarySentIds = List[Int]()
        var allSentIds = List[Int]()

        var treesCountWithNoPredefinedCombs = 0
        var treesCount = 0

        println(s"loading from ${cmd_args.ccg_file}")
        for((origTree, id) <- DerivationsLoader.fromFile(cmd_args.ccg_file).zipWithIndex){

          if(origTree.allNodes.flatMap(_.getCombinator).exists(!Combinator.isPredefined(_)))
            treesCountWithNoPredefinedCombs += 1
          treesCount += 1

          val deps = origTree.deps(hockenDeps = true).map(_.toUnlabelledAndUndirected)
          val transformed = if(parserProperties!=null) parserProperties.prepareDerivationTreeForTraining(origTree) else origTree

          allBinaryConstCount ::= transformed.allNodes.count(_.isInstanceOf[BinaryNode])

          var weirdUnary = false
          var weirdBinary = false

          transformed.leafs.foreach{node => tagsCounts(node.category) += 1}

          if(transformed.allNodes.flatMap(_.getCombinator).contains(TypeChangeBinary(Category("""NP/NP"""), Category("""NP[nb]/N"""), Category("""NP[nb]/N"""))))
            println("weird NP/NP NP/N composition")

          transformed.allNodes.foreach{
            case BinaryNode(RightAdjoinCombinator(_, _), _, _) =>
              binaryCounts(RightAdjoinCombinator(null, null)) += 1
            case BinaryNode(c, _, _)=>
              binaryCounts(c) += 1
              if(c.toString.startsWith("TC"))
                weirdBinary = true
            case UnaryNode(c, _) =>
              unaryCounts(c) += 1
              if(!CombinatorUnary.isPredefined(c))
                weirdUnary = true
            case TerminalNode(_, _) =>
          }

          if(weirdUnary)
            weirdUnarySentIds ::= id
          if(weirdBinary)
            weirdBinarySentIds ::= id
          allSentIds ::= id

          allConnectedness ::= connectedness(transformed)

          allWaitingTimes ::= waitingTime(transformed, deps)
          assert(allWaitingTimes.head.size == deps.size)

          allAdjunctionInfo ::= rightAdjunctionStats(transformed)._2
        }
        allConnectedness = allConnectedness.reverse
        allWaitingTimes = allWaitingTimes.reverse
        allAdjunctionInfo = allAdjunctionInfo.reverse
        allBinaryConstCount = allBinaryConstCount.reverse


        println(s"trees without non predefined combinators $treesCountWithNoPredefinedCombs/$treesCount")
        println()
        println(s"connectedness avg ${avg(allConnectedness.flatten)}")
        println(s"connectedness max ${allConnectedness.flatten.max}")
        println(s"connectedness maxSent ${allConnectedness.map(x => x.max).zipWithIndex.maxBy(_._1)._2}")
        println()
        println(s"waiting times avg ${avg(allWaitingTimes.flatten)}")
        println(s"waiting times max ${allWaitingTimes.flatten.max}")
        println(s"waiting times maxSent ${allWaitingTimes.map(x => (0::x).max).zipWithIndex.maxBy(_._1)._2}")
        println()
        println(s"safe adjunction number ${findSafeAdjunctionNumber(allAdjunctionInfo)}")
        if(allAdjunctionInfo.flatten.nonEmpty)
          println(s"adjunction info starts from ${allAdjunctionInfo.flatten.map(_._2).min}")
        println()
        println(s"adjunction choices avg ${avg(allAdjunctionInfo.flatten.map(_._1))}")
        println(s"adjunction choices max ${(0::allAdjunctionInfo.flatten.map(_._1)).max}")
        println(s"adjunction choices maxSent ${allAdjunctionInfo.map(x => (0::x.map(_._1)).max).zipWithIndex.maxBy(_._1)._2}")
        val (meanAdjHeight, stdAdjHeight) = meanStd(allAdjunctionInfo.flatten.filter(_._1>1).map{case (tot, pos) => 1-pos.toDouble/(tot-1)})
        println(s"adjunction height in multichoice avg $meanAdjHeight +- $stdAdjHeight (0 is bottom, 1 is top)")
        println(s"adjunction cases of multichoice "+ allAdjunctionInfo.flatten.count(_._1>1)+" total")
        println()
        val totalNumberOfAdjunctions = allAdjunctionInfo.flatten.size.toDouble
        println(s"adjunction total $totalNumberOfAdjunctions")
        println(s"adjunctions per sentence avg ${totalNumberOfAdjunctions/allAdjunctionInfo.size}")
        println(s"adjunction operation percent out of all binary consts ${100*totalNumberOfAdjunctions/allBinaryConstCount.sum} %")
        println()
        println(s"weird unary sents ${weirdUnarySentIds.size}/${allSentIds.size}")
        println(s"weird binary sents ${weirdBinarySentIds.size}/${allSentIds.size}")
        println(s"weird unary or binary sents ${(weirdUnarySentIds.toSet union weirdBinarySentIds.toSet).size}/${allSentIds.size}")

        println()
        println()
        println(s"all UNARY rules ${unaryCounts.size}")
        println(s"all UNARY non-core ${unaryCounts.keys.count{! CombinatorUnary.isPredefined(_)}}")
        val unaryTotalCounts = unaryCounts.values.sum
        var aggregPercentUnary = 0d
        for((c, i) <- unaryCounts.keys.toList.sortBy(unaryCounts).reverse.zipWithIndex){
          val holiness = if(CombinatorUnary.isPredefined(c)) "H" else "U"
          val count = unaryCounts(c)
          val percent = count.toDouble*100/unaryTotalCounts
          aggregPercentUnary += percent
          // println(f"$percent%.3f %%\t$holiness $c\t$count")
          println(f"${i+1}. $aggregPercentUnary%.3f %%\t $percent%.3f %%\t$holiness $c\t$count")
        }

        println()
        println()
        var aggregPercentBinary = 0d
        var aggregPercentBinaryHoly = 0d
        println(s"all BINARY rules ${binaryCounts.size}")
        println(s"all BINARY non-core ${binaryCounts.keys.count{_.toString.startsWith("TC")}}")
        val binaryTotalCounts = binaryCounts.values.sum
        for((c, i) <- binaryCounts.keys.toList.sortBy(binaryCounts).reverse.zipWithIndex){
          val holiness = if(CombinatorBinary.isPredefined(c) || c.isInstanceOf[RightAdjoinCombinator]) "H" else "U"
          val count = binaryCounts(c)
          val percent = count.toDouble*100/binaryTotalCounts
          aggregPercentBinary += percent
          aggregPercentBinaryHoly += (if(holiness == "H") percent else 0.0)
          // println(f"$percent%.3f %%\t$c\t$count")
          println(f"${i+1}. $aggregPercentBinary%.3f %%\t $aggregPercentBinaryHoly%.3f %%\t $percent%.3f %%\t$holiness $c\t$count")
        }

        println()
        println()
        val combsCounts = (unaryCounts.toList ++ binaryCounts.toList).toMap
        println(s"all ALL rules ${combsCounts.size}")
        println(s"all ALL non-core ${combsCounts.keys.count{! Combinator.isPredefined(_)}}")
        val allCombsTotalCounts = combsCounts.values.sum
        var aggregPercentAll = 0d
        var aggregPercentAllHoly = 0d
        for((c, i) <- combsCounts.keys.toList.sortBy(combsCounts).reverse.zipWithIndex){
          val holiness = if(Combinator.isPredefined(c) || c.isInstanceOf[RightAdjoinCombinator]) "H" else "U"
          val count = combsCounts(c)
          val percent = count.toDouble*100/allCombsTotalCounts
          aggregPercentAll += percent
          aggregPercentAllHoly += (if(holiness == "H") percent else 0.0)
          // println(f"$percent%.3f %%\t$holiness $c\t$count")
          println(f"${i+1}. $aggregPercentAll%.3f %%\t $aggregPercentAllHoly%.3f %%\t $percent%.3f %%\t$holiness $c\t$count")
        }

        println()
        println()
        var aggregPercentTag = 0d
        println(s"all TAGS ${tagsCounts.size}")
        val tagsTotalCounts = tagsCounts.values.sum
        for((t, i) <- tagsCounts.keys.toList.sortBy(tagsCounts).reverse.zipWithIndex){
          val count = tagsCounts(t)
          val percent = count.toDouble*100/tagsTotalCounts
          aggregPercentTag += percent
          println(f"${i+1}. $aggregPercentTag%.3f %%\t $percent%.3f %%\t$t\t$count")
        }


      case None =>
        System.err.println("You didn't specify all the required arguments")
        System.exit(-1)
    }
  }

  def findSafeAdjunctionNumber(allAdjunctionInfo:List[List[(Int, Int)]]) : Int = {
    val info = allAdjunctionInfo.flatten

    for(i <- 0 to 10000000){
      if(info.forall{case (total, level) => level < i || (total-level)<=i}){
        return i
      }
    }
    throw new Exception
  }

  def meanStd(xs:List[Double]) : (Double, Double) = {
    val mean = xs.sum/xs.size
    val std = math.sqrt(xs.map(x => math.pow(x-mean, 2)).sum/(xs.size-1))
    (mean, std)
  }

  def avgd(xs:List[Double]) : Double = if(xs.nonEmpty) xs.sum/xs.size else 0
  def avg(xs:List[Int]) : Double = if(xs.nonEmpty) xs.sum.toDouble/xs.size else 0

  /** We measure the average number of nodes in the stack
    * before shifting a new token from input buffer to
    * the stack, which we call as connectedness
    * min = 1 except for the very beginning
    */
  private def connectedness(node:TreeNode, currStackSize:Int=0) : List[Int] = node match {
    case UnaryNode(_, child)        => connectedness(child, currStackSize)
    case BinaryNode(_, left, right) => connectedness(left, currStackSize)++connectedness(right, currStackSize+1)
    case TerminalNode(_, _)         => List(currStackSize)
  }

  /** We define waiting time as the number of nodes
    * that need to be shifted to the stack before a de-
    * pendency between any two nodes in the stack is
    * resolved.
    * min = 0
    */
  private def waitingTime(node:TreeNode, deps:List[(Int, Int)]) : List[Int] = node match {
    case UnaryNode(_, child) =>
      waitingTime(child, deps)
    case TerminalNode(_, _) =>
      assert(deps == Nil)
      List()
    case BinaryNode(_, left, right) =>
      val splitPoint = left.span._2
      val (solvedDeps, unsolvedDeps) = deps.partition(dep => dep._1 < splitPoint && splitPoint <= dep._2)
      val (leftDeps, rightDeps) = unsolvedDeps.partition(dep => dep._2 < splitPoint)
      val currWaitingTime = solvedDeps.sorted.map(x => right.span._2-x._2-1)
      waitingTime(left, leftDeps) ++ currWaitingTime ++ waitingTime(right, rightDeps)
  }

  /**
    * returns a list of (number of adjunction options, the option take counting from top down)
    */
  private def rightAdjunctionStats(node:TreeNode) : (TreeNode, List[(Int, Int)]) = node match {
    case BinaryNode(comb, left, right) =>
      val (newLeft, leftStats) = rightAdjunctionStats(left)
      val (newRight, rightStats) = rightAdjunctionStats(right)
      comb match {
        case RightAdjoinCombinator(cat, span) =>
          val options = RightSpine.extractRightAdjunctionCandidatesOfficial(newLeft, right.category)
          val positionTopDown = options.indexWhere(_.span == span)
          if(positionTopDown<0)
            print("hello")
          assert(positionTopDown>=0)

          val newNode = RightSpine.rightModify(newLeft, newRight, cat, span)
          (newNode, leftStats ++ rightStats :+ (options.size, positionTopDown))
        case _ =>
          val newNode = Rebranching.sinkForwardRightward(
             Rebranching.sinkForwardRightward(
               Rebranching.sinkForwardRightward(
                BinaryNode(comb, newLeft, newRight)
               )
             )
          )
          (newNode, leftStats ++ rightStats)
      }
    case UnaryNode(comb, child) =>
      val (newChild, stats) =rightAdjunctionStats(child)
      (Rebranching.sinkForwardRightward(UnaryNode(comb, newChild)), stats)
    case TerminalNode(_, _) =>
      (node, Nil)
  }

}

