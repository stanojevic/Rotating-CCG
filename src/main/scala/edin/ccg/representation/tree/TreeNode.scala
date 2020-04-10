package edin.ccg.representation.tree

import java.io.File

import edin.ccg.representation.category.Category
import edin.ccg.representation.combinators._
import edin.ccg.representation.hockendeps.HockenState
import edin.ccg.representation.predarg.{DepLink, PredArg}
import edin.ccg.representation.transforms.AdjunctionGeneral.RightAdjoinCombinator
import edin.ccg.transitions.ParserProperties
import edin.general.DepsVisualizer
import edin.general.DepsVisualizer.DepsDesc
import edin.general.TreeVisualizer.{SimpleTreeNode, Color, Shape}
import edin.nn.State
import edu.cmu.dynet.Expression

import scala.util.{Failure, Random, Success, Try}

abstract class TreeNode extends State with Serializable {

  override protected def myEquals(o: Any): Boolean = o match {
    case that:TreeNode =>
      that.category == this.category &&
        that.span == this.span &&
        that.getCombinator == this.getCombinator &&
        that.children == this.children
    case _ =>
      false
  }

  override protected def myHash(): Int = (category, span, getCombinator).##

  val category     : Category

  ////////////////   DEPENDENCIES   /////////////////
  private[representation] lazy val hockenState     : HockenState   = HockenState.forNode(this)
  private                 lazy val hockenDepsSet   : Set[DepLink]  = this.children.map(_.hockenDepsSet).foldLeft(hockenState.nodeOnlyDeps.toSet){_ union _}
  private                 lazy val hockenDepsList  : List[DepLink] = hockenDepsSet.toList

  private[representation] lazy val predArg         : PredArg       = debugCage(PredArg.forNode(this))
  private                 lazy val predArgDeps     : Set[DepLink]  = this.children.map(_.predArgDeps).foldLeft(predArg.newFinishedDeps){_ union _}
  private                 lazy val predArgDepsList : List[DepLink] = predArgDeps.toList
                          lazy val heads           : Set[Int]      = predArg.activeWords.map(_.wordPos)

  private def debugCage[A](pa: => A) : A =
    Try(pa) match {
      case Success(value) =>
        value
      case Failure(exception) =>
        val i : Int = Random.nextInt()
        this.saveVisual(s"problematic_tree_$i", s"problematic_tree_$i")
        for(child <- this.children){
          System.err.println(child.predArg)
        }
        throw exception
    }

  def deps(hockenDeps:Boolean=false) : List[DepLink] = debugCage(if(hockenDeps && Combinator.language != "Chinese") hockenDepsList else predArgDepsList)
  private def depsDesc(hockenDeps:Boolean, graphLabel:String) : DepsDesc =
    DepsDesc(
      label=graphLabel,
      words = this.words,
      tags = this.leafs.map(_.category.toString),
      deps = deps(hockenDeps).map(d => (d.headPos, d.depPos, d.arcLabel, if(d.isLongDistance) "blue" else "black")),
      depsBelow = Nil
    )
  def depsVisualize(hockenDeps:Boolean=false, graphLabel:String="") : Unit = DepsVisualizer.visualize(depsDesc(hockenDeps, graphLabel))
  def depsSaveVisual(hockenDeps:Boolean=false, graphLabel:String="", fn:String) : Unit = DepsVisualizer.visualize(depsDesc(hockenDeps, graphLabel), fn)

  ////////////////   Neural Stuff   /////////////////
  private var hierStateMemo: State = _ // null means it's unitialized
  private var hierStateComputation: () => State = {() => null}
  def hierState : State = {
    if(hierStateMemo == null){
      hierStateMemo = hierStateComputation()
    }
    hierStateMemo
  }
  def hierState_=(computation : => State) : Unit = {
    hierStateComputation = () => computation
  }
  override lazy val h:Expression = hierState.h

  def span : (Int, Int)

  ////////////////   Node accessors   /////////////////

  def allNodes:List[TreeNode] = allNodesPreorder

  def allNodesPreorder:List[TreeNode] = this match {
    case TerminalNode(_, _) => List(this)
    case UnaryNode(_, child) => this :: child.allNodes
    case BinaryNode(_, left, right) => this :: (left.allNodes ++ right.allNodes)
  }

  def allNodesPostorder:List[TreeNode] = this match {
    case TerminalNode(_, _) => List(this)
    case UnaryNode(_, child) => child.allNodesPostorder :+ this
    case BinaryNode(_, left, right) => (left.allNodesPostorder ++ right.allNodesPostorder) :+ this
  }

  def leafs : List[TerminalNode] = {
    this match {
      case x@TerminalNode(_, _) => List(x)
      case BinaryNode(_, l, r) => l.leafs ++ r.leafs
      case UnaryNode(_, child) => child.leafs
    }
  }

  def children : List[TreeNode] = this match {
    case BinaryNode(_, l, r) => List(l, r)
    case UnaryNode(_, child) => List(child)
    case TerminalNode(_, _) => List()
  }

  def getCombinator : Option[Combinator] = this match {
    case BinaryNode(c, _, _) => Some(c)
    case UnaryNode(c, _) => Some(c)
    case TerminalNode(_, _) => None
  }

  def words : List[String] = leafs.map{_.word}

  ////////////////   Visualization   /////////////////
  private def toSimpleTreeNode : SimpleTreeNode = this match {
    case n@TerminalNode(word, cat) =>
      SimpleTreeNode(
        label = s"$cat\n$word\n${n.position}",
        children = List(),
        shape=Shape.RECTANGLE, color = Color.LIGHT_BLUE)
    case UnaryNode(c, child) =>
      val shape = c match {
        case tc:TypeChangeUnary if tc.isUnaryCoordination => Shape.HEXAGON
        case _                                            => Shape.BOX
      }
      SimpleTreeNode(
        label = this.toString.replaceAll("-", "\n"),
        children = List(child.toSimpleTreeNode),
        shape=shape, color = Color.GREEN)
    case BinaryNode(c, left, right) =>
      val color = c match {
        case _:Backwards                      => Color.PURPLE
        case _:ConjunctionTop | _:Conjunction => Color.BLUE
        case _:Forwards                       => Color.RED
        case _:RemovePunctuation              => Color.LIGHT_BLUE
        case _                                => Color.BLACK
      }
      val shape = c match {
        case _:ConjunctionTop | _:Conjunction => Shape.HEXAGON
        case _                                => Shape.RECTANGLE
      }
      SimpleTreeNode(
        label = this.toString.replaceAll("-", "\n"),
        children = List(left.toSimpleTreeNode, right.toSimpleTreeNode),
        shape=shape, color = color)
  }

  def visualize(graphLabel:String="", fileType:String="pdf") : Unit =
    this.toSimpleTreeNode.visualize(graphLabel=graphLabel, fileType=fileType)

  def saveVisual(fn:String, graphLabel:String="", fileType:String="pdf") : Unit =
    this.toSimpleTreeNode.saveVisual(new File(s"$fn.$fileType"), graphLabel=graphLabel, fileType=fileType)

  ////////////////   Rebranching   /////////////////
  def toLeftBranching : TreeNode = ParserProperties.prototypeProps.copy(
    useIncrementalTrans     = true,
    useRevealing            = false
  ).prepareDerivationTreeForTraining(this)

  def toRevealingBranching : TreeNode = ParserProperties.prototypeProps.copy(
    useIncrementalTrans     = true,
    useRevealing            = true
  ).prepareDerivationTreeForTraining(this)

  def toRightBranching : TreeNode = ParserProperties.prototypeProps.copy(
    useIncrementalTrans     = false,
    useRevealing            = false
  ).prepareDerivationTreeForTraining(this)

  ////////////////   toString   /////////////////
  def toCCGbankString : String = this match {
    case TerminalNode(word, cat) => s"(<L $cat X X $word X>)"
    case n@UnaryNode(_, child) => s"(<T ${n.category} 0 1> ${child.toCCGbankString} )"
    case BinaryNode(Glue(), l, r) => s"(<T GLUE 1 2> ${l.toCCGbankString} ${r.toCCGbankString} )"
    // case BinaryNode(Glue(), l, r) => s"(<T ${l.toCCGbankString} 1 2> ${l.toCCGbankString} ${r.toCCGbankString} )"
    case BinaryNode(RightAdjoinCombinator(_, _), _, _) => throw new Exception("can't print RightAdjoinCombinator ; rebracket")
    case n@BinaryNode(c, l, r) => s"(<T ${n.category} ${if(c.functorIsLeft) 0 else 1} 2> ${l.toCCGbankString} ${r.toCCGbankString} )"
  }

}

