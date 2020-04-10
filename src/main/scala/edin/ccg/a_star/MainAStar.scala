package edin.ccg.a_star

import java.io.PrintWriter

import edin.algorithms.{CKYItem, CKYMap, MaxHeap, TimingStatsAggregator}
import edin.ccg.representation.DerivationsLoader
import edin.ccg.representation.category.{Category, Slash}
import edin.ccg.representation.combinators._
import edin.ccg.representation.tree.{BinaryNode, TerminalNode, TreeNode, UnaryNode}
import edin.general.Global.{printMessageWithTime, printProcessId}

import edin.algorithms.AutomaticResourceClosing.linesFromFile
import scala.util.{Failure, Success, Try}

object MainAStar {

  val PROGRAM_NAME = "A* CCG PARSER"
  val PROGRAM_VERSION = 0.1

  case class CMDargs(
                      input_file_supertags  : String         = null,
                      output_file           : String         = null,
                      heapType              : String         = "Fibonacci",
                      language              : String         = "English",
                      attachLow             : Boolean        = true,
                      knuth_algorithm       : Boolean        = false
                    )

  val timerParsing = new TimingStatsAggregator("parsing_all")
  val timerAgendaInitialization = new TimingStatsAggregator("heapification")
  val timerAgendaItemCreation = new TimingStatsAggregator("creating_init_items")

  def main(args:Array[String]) : Unit = {
    val parser = new scopt.OptionParser[CMDargs](PROGRAM_NAME) {
      head(PROGRAM_NAME, PROGRAM_VERSION.toString)

      opt[ String  ]( "input_file_supertags" ).action((x,c) => c.copy( input_file_supertags = x        )).required()
      opt[ String  ]( "output_file"          ).action((x,c) => c.copy( output_file          = x        )).required()
      opt[ String  ]( "heap_type"            ).action((x,c) => c.copy( heapType             = x        )).required()
      opt[ String  ]( "language"             ).action((x,c) => c.copy( language             = x        )).required()
      opt[ Boolean ]( "knuth_algorithm"      ).action((x,c) => c.copy( knuth_algorithm      = x        ))
      opt[ Boolean ]( "attach_low_heuristic" ).action((x,c) => c.copy( attachLow            = x        ))

      help("help").text("prints this usage text")
    }

    parser.parse(args, CMDargs()) match {
      case Some(cmd_args) =>
        printProcessId()

        Combinator.setLanguage(cmd_args.language, null)

        printMessageWithTime("parsing started")

        val pw = new PrintWriter(cmd_args.output_file)
        for((tags, i) <- loadSupertags(cmd_args.input_file_supertags).zipWithIndex){

          System.err.println(s"processing $i length="+tags.size)

          val words = tags.map(_._1)

          Try(a_star_parse_dynamic_prog(tags, cmd_args.heapType, cmd_args.knuth_algorithm)) match {
            case Success(tree) if cmd_args.attachLow =>
              pw.println(transformRightAdjLowDestructive(tree).toCCGbankString)
            case Success(tree) if ! cmd_args.attachLow =>
              pw.println(tree.toCCGbankString)
            case Failure(exception) =>
              System.err.println(exception.getMessage)
              pw.println(s"FAILED PARSING: ${words.mkString(" ")}")
          }
          pw.flush()
          // var tree:TreeNode = a_star_parse_dynamic_prog(tags, cmd_args.heapType, cmd_args.knuth_algorithm)
        }
        pw.close()

        printMessageWithTime("parsing finished")
        System.err.println()

        timerParsing.printInfo()
        timerParsing.printCsvInfo()
        System.err.println()

        timerAgendaItemCreation.printInfo()
        timerAgendaItemCreation.printCsvInfo()
        System.err.println()

        timerAgendaInitialization.printInfo()
        timerAgendaInitialization.printCsvInfo()
        System.err.println()

      case None =>
        System.err.println("You didn't specify all the required arguments")
        System.exit(-1)
    }

  }

  private final case class Item(start:Int, end:Int, cat:Category, combState: CombState, outsideScore:Double, var backPointer:BackPointer) extends CKYItem {

    def totalScore : Double = outsideScore + backPointer.score

    override def hashCode(): Int = (start, end, cat, combState).##

    override def equals(o: Any): Boolean = o match {
      case Item(`start`, `end`, `cat`, `combState`, _, _) => true
      case _ => false
    }

  }

  private sealed trait CombType
  private case class  Fwd(order:Int)           extends CombType
  private case class  Bck(order:Int)           extends CombType
  private case class  TR(fwdTR:Boolean)        extends CombType
  private case object TC                       extends CombType
  private case class  Coord(lower:Boolean)     extends CombType
  private case class  Punc(puncIsLeft:Boolean) extends CombType
  private case class  CombState(topCombinator:CombType, hasLeftPuncOnEdge:Boolean, hasRightPuncOnEdge:Boolean)

  private def toCombType(c:Combinator, l:Item, r:Item) : CombState = c match {
    case c:Forwards                    => CombState(Fwd(c.order)          , l.combState.hasLeftPuncOnEdge , r.combState.hasRightPuncOnEdge)
    case c:Backwards                   => CombState(Bck(c.order)          , l.combState.hasLeftPuncOnEdge , r.combState.hasRightPuncOnEdge)
    case TypeRaiser(_, _, slash, true) => CombState(TR(slash == Slash.FWD), l.combState.hasLeftPuncOnEdge , r.combState.hasRightPuncOnEdge)
    case Conjunction()                 => CombState(Coord(lower = true)   , l.combState.hasLeftPuncOnEdge , r.combState.hasRightPuncOnEdge)
    case ConjunctionTop()              => CombState(Coord(lower = false)  , l.combState.hasLeftPuncOnEdge , r.combState.hasRightPuncOnEdge)
    case RemovePunctuation(true)       => CombState(Punc(true)            , true                          , r.combState.hasRightPuncOnEdge)
    case RemovePunctuation(false)      => CombState(Punc(true)            , r.combState.hasRightPuncOnEdge, true                          )
    case _                             => CombState(TC                    , l.combState.hasLeftPuncOnEdge , r.combState.hasRightPuncOnEdge)
  }

  private sealed trait BackPointer{ val score : Double }
  private case class BinaryBackPointer(c:CombinatorBinary, leftItem:Item, rightItem:Item, score:Double) extends BackPointer
  private case class UnaryBackPointer(c:CombinatorUnary, childItem:Item, score:Double) extends BackPointer
  private case class NullBackPointer(word:String, score:Double) extends BackPointer

  private def reconstructTree(chart:CKYMap[Item, _], item:Item) : TreeNode = item.backPointer match {
    case BinaryBackPointer(c, leftItem, rightItem, _) =>
      BinaryNode(
        c,
        reconstructTree(chart, leftItem),
        reconstructTree(chart, rightItem)
      )
    case UnaryBackPointer(c, childItem, _) =>
      UnaryNode(
        c,
        reconstructTree(chart, childItem)
      )
    case NullBackPointer(word, _) =>
      TerminalNode(word, item.cat)
  }

  private def loadSupertags(fn:String) : Iterator[List[(String, List[(Category, Double)])]] = {
    var currSent : List[(String, List[(Category, Double)])] = Nil
    linesFromFile(fn).flatMap{ line =>
      if(line.isEmpty){
        val res = Some(currSent.reverse)
        currSent = Nil
        res
      }else{
        val fields = line.split("\t").toList
        val word = fields.head
        var tagScores = fields.drop(2).flatMap{ x =>
          val y = x.split(" ")
          val cat = Category(y(0))
          val score = y(1).toDouble
//          (cat, if(score <= 0) score else math.log(score))
          if(y(0) == "PADDING")
            None
          else
            Some(cat, score)
        }
//        val normalizer = tagScores.map(_._2).sum
        tagScores = tagScores.map{ case (tag, score) => (tag, score)} // math.log(score/normalizer))}
        currSent ::= (word, tagScores)
        None
      }
    }
  }

  private def a_star_parse_dynamic_prog(sentTags : List[(String, List[(Category, Double)])], heapType:String, onlyKnuthAlg:Boolean) : TreeNode = {
    val n = sentTags.size

    val startTime = System.currentTimeMillis()

    // if(n>60)
    //   throw new Exception(s"exceeded max allowed sent length $n")

    timerParsing.startTiming(n)

    val agenda = new MaxHeap[Item](efficientHandleMap = new CKYMap[Item, AnyRef](n), heapType=heapType)
    val chart = new CKYMap[Item, Null](n)

    // ideal inside scores
    val idealSpanScores : Array[Array[Double]] = new Array(n+1)
    for(i <- 0 until n+1){
      idealSpanScores(i) = new Array(n+1)
      idealSpanScores(i)(i) = 0
    }
    for(((_, wordTags), i) <- sentTags.zipWithIndex){
      idealSpanScores(i)(i+1) = if(onlyKnuthAlg) 0 else wordTags.map(_._2).max
    }
    for(i <- sentTags.indices){
      for(j <- i+2 until n+1){
        idealSpanScores(i)(j) = if(onlyKnuthAlg) 0 else idealSpanScores(i)(j-1)+idealSpanScores(j-1)(j)
      }
    }

    // init agenda
    timerAgendaItemCreation.startTiming(n)
    var itemsToInsert = List[Item]()
    for(((word, wordTags), i) <- sentTags.zipWithIndex) {
      for ((cat, score) <- wordTags) {
        val outsideScore = idealSpanScores(0)(i) + idealSpanScores(i + 1)(n)
        itemsToInsert ::= Item(i, i + 1, cat, CombState(TC, false, false), outsideScore, NullBackPointer(word, score))
      }
    }
    timerAgendaItemCreation.endTiming()

    timerAgendaInitialization.startTiming(n)
    agenda.insertChunk(itemsToInsert, itemsToInsert.map(_.totalScore))
    timerAgendaInitialization.endTiming()

    var prevAgendaSize = 0l

    // A* main
    while(agenda.nonEmpty){
      val (item, agendaScore) = agenda.extractMax()
      assert(item.totalScore == agendaScore)
      assert(! chart.contains(item))
      if(agenda.size-prevAgendaSize>1000){
        System.err.println(agenda.size)
        prevAgendaSize = agenda.size
      }
      if(System.currentTimeMillis()-startTime > 1000*60*2){
        throw new Exception(s"timeout")
      }
      chart(item) = null
      if(item.start == 0 && item.end == n){
        timerParsing.endTiming()
        return reconstructTree(chart, item)
      }else{
        var consequences : List[Item] = Nil

        for((leftItem, _) <- chart.itemsEndingAt(item.start)){
          val rightItem = item
          for(c <- CombinatorBinary.allPredefined){
            if(
              c.canApply(leftItem.cat, rightItem.cat) &&
              normalObservedCatAllows(c, leftItem, rightItem) &&
              normalFormAllows(toCombType(c, leftItem, rightItem), leftItem.combState, rightItem.combState)
            ){
              val newInsideScore = leftItem.backPointer.score + rightItem.backPointer.score
              val bp = BinaryBackPointer(c, leftItem, rightItem, newInsideScore)
              val outsideScore = idealSpanScores(0)(leftItem.start) + idealSpanScores(rightItem.end)(n)
              consequences ::= Item(leftItem.start, rightItem.end, c(leftItem.cat, rightItem.cat), toCombType(c, leftItem, rightItem), outsideScore, bp)
            }
          }
        }
        for((rightItem, _) <- chart.itemsStartingAt(item.end)){
          val leftItem = item
          for(c <- CombinatorBinary.allPredefined){
            if(
              c.canApply(leftItem.cat, rightItem.cat) &&
              normalObservedCatAllows(c, leftItem, rightItem) &&
              normalFormAllows(toCombType(c, leftItem, rightItem), leftItem.combState, rightItem.combState)
            ){
              val newInsideScore = leftItem.backPointer.score + rightItem.backPointer.score
              val bp = BinaryBackPointer(c, leftItem, rightItem, newInsideScore)
              val outsideScore = idealSpanScores(0)(leftItem.start) + idealSpanScores(rightItem.end)(n)
              consequences ::= Item(leftItem.start, rightItem.end, c(leftItem.cat, rightItem.cat), toCombType(c, leftItem, rightItem), outsideScore, bp)
            }
          }
        }
        for(c <- CombinatorUnary.allPredefined){
          if(
            c.canApply(item.cat) &&
            normalObservedCatAllows(c, item) &&
            unaryNormalFormAllows(toCombType(c, item, item), item.combState)
          ){
            val newInsideScore = item.backPointer.score
            val bp = UnaryBackPointer(c, item, newInsideScore)
            val outsideScore = idealSpanScores(0)(item.start) + idealSpanScores(item.end)(n)
            consequences ::= Item(item.start, item.end, c(item.cat), toCombType(c, item, item), outsideScore, bp)
          }
        }

        for(consequence <- consequences){
          if(! chart.contains(consequence)){
            // agenda.insertSmart(consequence, consequence.totalScore)
            agenda.getSameElement(consequence) match {
              case Some(other) =>
                if(consequence.totalScore > other.totalScore){
                  other.backPointer = consequence.backPointer
                  agenda.increaseKey(other, other.totalScore)
                }
                // System.err.println(s"redundant agenda $consequence")
              case None =>
                agenda.insert(consequence, consequence.totalScore)
                // System.err.println(s"new       agenda $consequence")
            }
          }else{
            // System.err.println(s"redundant chart $consequence")
          }
        }
      }
    }
    throw new Exception("parse not found")
  }

  private def unaryNormalFormAllows(parent:CombState, child: CombState) : Boolean = (parent, child) match {
    case (CombState(TR(_), _, _), CombState(Coord(false), _   , _      ) ) => false // constraint 6 from Hockenmaier and Bisk
    case (_                     , CombState(_           , true, _      ) ) => false // my punc normal form -- attach punctuation high
    case (_                     , CombState(_           , _   , true   ) ) => false // my punc normal form -- attach punctuation high
    case _                                                                 => true
  }

  private lazy val observedRules = ExtractObservedRules.loadDefaultObservedRules()

  private def normalObservedCatAllows(c:CombinatorBinary, leftItem:Item, rightItem:Item) : Boolean =
    observedRules._2.contains((c(leftItem.cat, rightItem.cat), leftItem.cat, rightItem.cat))

  private def normalObservedCatAllows(c:CombinatorUnary, childItem:Item) : Boolean =
    observedRules._1.contains((c(childItem.cat), childItem.cat))

  private def normalFormAllows(parent:CombState, left: CombState, right: CombState) : Boolean = (parent, left, right) match {
    case (CombState(c           , _, _), CombState(_, true, _), CombState(_, _, _   )) if ! c.isInstanceOf[Punc] => false // my punc normal form -- attach right punctuation high
    case (CombState(c           , _, _), CombState(_, _   , _), CombState(_, _, true)) if ! c.isInstanceOf[Punc] => false // my punc normal form -- attach right punctuation high
    case (CombState(Fwd(nParent), _, _), CombState(Fwd(nChild), _, _) , _                              ) if nChild>=1 && nParent<=1                   => false // constraint 1 from Hockenmaier and Bisk FWD
    case (CombState(Bck(nParent), _, _), _                            , CombState(Bck(nChild), _, _   )) if nChild>=1 && nParent<=1                   => false // constraint 1 from Hockenmaier and Bisk BCK
    case (CombState(Fwd(nParent), _, _), CombState(Fwd(nChild), _, _) , _                              ) if nChild==1 && nParent>=1                   => false // constraint 2 from Hockenmaier and Bisk FWD
    case (CombState(Bck(nParent), _, _), _                            , CombState(Bck(nChild), _, _   )) if nChild==1 && nParent>=1                   => false // constraint 2 from Hockenmaier and Bisk BCK
    case (CombState(Fwd(nParent), _, _), _                            , CombState(Fwd(nChild), _, _   )) if nChild >1 && nParent >1 && nParent>nChild => false // constraint 3 from Hockenmaier and Bisk FWD
    case (CombState(Bck(nParent), _, _), CombState(Bck(nChild), _, _) , _                              ) if nChild >1 && nParent >1 && nParent>nChild => false // constraint 3 from Hockenmaier and Bisk BCK
    case (CombState(Fwd(nParent), _, _), CombState(TR(true)   , _, _) , CombState(Bck(nChild), _, _   )) if              nParent >0 && nParent<nChild => false // constraint 4 from Hockenmaier and Bisk
    case (CombState(Fwd(0)      , _, _), CombState(TR(true)   , _, _) , _                              )                                              => false // constraint 5 from Hockenmaier and Bisk FWD
    case (CombState(Bck(0)      , _, _), _                            , CombState(TR(false)  , _, _   ))                                              => false // constraint 5 from Hockenmaier and Bisk BCK
    case (CombState(Punc(true)  , _, _), _                            , CombState(_          , _, true))                                              => false // my punc normal form -- right punc must be above left punc (similar in Hockenmaier and Bisk and Tse and Curran)
    case _                                                                                                                                            => true
  }

  def transformRightAdjLowDestructive(node:TreeNode) : TreeNode = {
    val (core, adjs) = detachAdjs(node)
    adjs.foldLeft(core){ case (core, (adjComb, adj)) =>
      attachAdj(core, adjComb, adj).get
    }
  }

  private def detachAdjs(node:TreeNode) : (TreeNode, List[(CombinatorBinary, TreeNode)]) = {
    DerivationsLoader.assignSpans(node)
    recDetachAdjs(node)
  }

  private def recDetachAdjs(node:TreeNode) : (TreeNode, List[(CombinatorBinary, TreeNode)]) = node match {
    case BinaryNode(comb, l , r) =>
      val (lCore, lAdjs) = recDetachAdjs(l)
      val (rCore, rAdjs) = recDetachAdjs(r)
      if(comb(l.category, r.category) == l.category && (comb.isInstanceOf[ConjunctionTop] || comb.isInstanceOf[Backwards] )){
        // adjunction
        (lCore, (lAdjs :+ (comb, rCore)) ++ rAdjs)
      }else{
        (BinaryNode(comb, lCore, rCore), lAdjs ++ rAdjs)
      }
    case UnaryNode(comb, child) =>
      val (core, adjs) = recDetachAdjs(child)
      (UnaryNode(comb, core), adjs)
    case TerminalNode(_, _) =>
      (node, List())
  }

  private def attachAdj(node:TreeNode, adjComb:CombinatorBinary, adj:TreeNode) : Option[TreeNode] = {
    val newMe = node match {
      case BinaryNode(comb, l, r) =>
        if(r.span._1 < adj.span._1)
          attachAdj(r, adjComb, adj) match {
            case Some(newRight) =>
              Some(BinaryNode(comb, l, newRight))
            case None =>
              None
          }
        else
          attachAdj(l, adjComb, adj) match {
            case Some(newLeft) =>
              Some(BinaryNode(comb, newLeft, r))
            case None =>
              None
          }
      case UnaryNode(comb, child) =>
        attachAdj(child, adjComb, adj) match {
          case Some(newChild) =>
            Some(UnaryNode(comb, newChild))
          case None =>
            None
        }
      case TerminalNode(_, _) =>
        None
    }

    newMe match {
      case x@Some(_) =>
        x
      case None =>
        (CombinatorBinary.allBackwardAndCrossed + ConjunctionTop()).find(c => c.canApply(node.category, adj.category) && c(node.category, adj.category) == node.category) match {
          case Some(c) =>
            assert(node.span._2 == adj.span._1)
            Some(BinaryNode(c, node, adj))
          case None =>
            None
        }
    }
//
//    node match {
//      case BinaryNode(comb, l, r) =>
//        if(adj.span._2 < node.span._2){
//          attachAdj(l, adjComb, adj) match {
//            case Some(newLeft) =>
//              Some(BinaryNode(comb, newLeft, r))
//            case None if adjComb.canApply(l.category, adj.category) =>
//              assert(l.span._2==adj.span._1)
//              Some(BinaryNode(comb, BinaryNode(adjComb, l, adj), r))
//            case None =>
//              None
//          }
//        }else{
//          attachAdj(r, adjComb, adj) match {
//            case Some(newRight) =>
//              Some(BinaryNode(comb, l, newRight))
//            case None if adjComb.canApply(r.category, adj.category) =>
//              assert(r.span._2==adj.span._1)
//              Some(BinaryNode(comb, l, BinaryNode(adjComb, r, adj)))
//            case None =>
//              None
//          }
//        }
//      case UnaryNode(comb, child) =>
//        attachAdj(child, adjComb, adj) match {
//          case Some(newChild) =>
//            Some(UnaryNode(comb, newChild))
//          case None if adjComb.canApply(child.category, adj.category) =>
//            assert(child.span._2==adj.span._1)
//            Some(UnaryNode(comb, BinaryNode(adjComb, child, adj)))
//          case None =>
//            None
//        }
//      case TerminalNode(_, _) =>
//        None
//    }
  }

}

