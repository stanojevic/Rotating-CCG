package edin.ccg.transitions

import edin.ccg.representation._
import edin.ccg.representation.category.Category
import edin.ccg.representation.combinators._
import edin.ccg.representation.transforms.AdjunctionGeneral.RightAdjoinCombinator
import edin.ccg.representation.transforms._
import edin.ccg.representation.tree.{BinaryNode, TerminalNode, TreeNode, UnaryNode}
import edin.general.YamlConfig

case class ParserProperties (
                              // incrementality
                              useIncrementalTrans     : Boolean,
                              useRevealing            : Boolean,
                              revealingDepth          : Int = 6,

                              // normal form stuff
                              withObservedCatsOnly    : Boolean,
                              withForwardAntiEisnerNF : Boolean, //      FWD and BCK   |  you can't Bn> X and Y if Y is result of Bm> for n>=1 and m>=1 or n=m=0 // will crash on B4
                              withForwardEisnerNF     : Boolean, //      FWD and BCK   |  you can't Bn> X and Y if X is result of Bm> for n>=0 and m>=1
                              withBackwardEisnerNF    : Boolean, //      FWD and BCK   |  you can't Bn> X and Y if X is result of Bm> for n>=0 and m>=1
                              withPuncNF              : Boolean, //      BINARY        |  you can't reduce constituents X and Y if Y's rightmost terminal is punctuation
                              withHighTRconjNF        : Boolean, //      CONJ          |  you can't conj X and Y if Y is (TR ?)
                              withLowTRconjNF         : Boolean, //      UNARY         |  you can't TR const X if X is (B<0 ? (CONJ ? ?))
                              withLowLeftAdjNF        : Boolean, //      FWD           |  you can't left adjoin X to Y if Y is a result of right adjoin
                              withLowRightAdjNF       : Boolean, //      BCK && ADJOIN |  you can't right adjoin Y to X if X is a result of left adjoin
                              withHockenCatNormal     : Boolean,  //      any binary    |  you can't extract join two constituents that can't be represented with HockenCat

                              onlyPredefinedCombs     : Boolean
){

  assert(!useRevealing || useIncrementalTrans)
  assert(!withForwardAntiEisnerNF || useIncrementalTrans)
  assert(!(withForwardEisnerNF  && useIncrementalTrans))
  assert(!(withBackwardEisnerNF && useIncrementalTrans))
  assert(!(withHighTRconjNF && withLowTRconjNF))
  assert(!(withLowLeftAdjNF && withLowRightAdjNF))

  def reduceOptions(combinatorsContainer: CombinatorsContainer) : List[TransitionOption] =
    if(onlyPredefinedCombs)
      reduceOptionsOnlyPredefined
    else
      reduceOptionsEverything(combinatorsContainer)

  private def reduceOptionsOnlyPredefined : List[TransitionOption] = {
    val binary = CombinatorBinary.allPredefined.map(BinaryReduceOption)
    val unary  = CombinatorUnary.allPredefined.map(UnaryReduceOption)
    val rest = if(useRevealing) List(RevealingOption(), ShiftOption()) else List(ShiftOption())
    rest++unary++binary
  }

  private def reduceOptionsEverything(combinatorsContainer: CombinatorsContainer) : List[TransitionOption] = {
    List(ShiftOption()) ++
    ( if(useRevealing) List(RevealingOption()) else List()) ++
    CombinatorBinary.allPredefined.toList.map{BinaryReduceOption} ++
    combinatorsContainer.allUnholyBinary.map{BinaryReduceOption} ++
    combinatorsContainer.allUnary.map{UnaryReduceOption}
  }

  def prepareDerivationTreeForTraining(node:TreeNode) : TreeNode =
    extractNonGlued(node)
      .map(repairNPcomposition)
      .map(prepareDerivationTreeForTrainingSimple)
      .reduceLeft(BinaryNode(Glue(), _, _))

  private val illegalNPcomposition = TypeChangeBinary(Category("NP/NP"), Category("NP/N"), Category("NP/N"))
  private val repairNPcomposition : TreeNode => TreeNode = {
    case BinaryNode(B0fwd(), BinaryNode(`illegalNPcomposition`, x, y), z) =>
      repairNPcomposition(BinaryNode(B0fwd(), x, BinaryNode(B0fwd(), y, z)))
    case BinaryNode(c, x, y) =>
      BinaryNode(c, repairNPcomposition(x), repairNPcomposition(y))
    case UnaryNode(c, child) =>
      UnaryNode(c, repairNPcomposition(child))
    case x@TerminalNode(_, _) =>
      x
  }

  private val extractNonGlued: TreeNode => List[TreeNode] = {
    case BinaryNode(Glue(), l, r) => extractNonGlued(l) ++ extractNonGlued(r)
    case x => List(x)
  }

  private def prepareDerivationTreeForTrainingSimple(node:TreeNode) : TreeNode = {
    var tree = node

    if(useIncrementalTrans)
      tree = TypeRaisingTransforms.addSubjectTypeRaising(tree)

    tree = PuncAttachment.reattachPunctuationTopLeft(tree)

    if(withHighTRconjNF)
      tree = TypeRaisingTransforms.raiseTRinConj(tree)
    else if(withLowTRconjNF)
      tree = TypeRaisingTransforms.lowerTRinConj(tree)

    // adjunct transform is excluded because coordination is treated as adjunction and that puts some unwanted constraints
    if(withLowLeftAdjNF)
      tree = AdjunctionGeneral.lowerAdjunction(tree, lowerLeftAdjuncts = true)
    else if(withLowRightAdjNF)
      tree = AdjunctionGeneral.lowerAdjunction(tree, lowerLeftAdjuncts = false)

    if(useIncrementalTrans){
      if(useRevealing){
        tree = RevealingTransforms.addRevealing(tree)
      }
      tree = RevealingTransforms.toLeftBranchingWithRevealing(tree, withLeftBranching=true)
    }else{
      tree = Rebranching.toRightBranching(tree)
      tree = PuncAttachment.reattachPunctuationTopLeft(tree)
    }

    assert(tree.leafs == node.leafs)

    tree
  }

  // this is a hack
  private def fakeNode(cat: Category, sp: (Int, Int)) : TreeNode = {
    val l = TerminalNode(null, cat)
    l.position = sp._1
    val r = TerminalNode(null, Category(","))
    r.position = sp._2-1
    val n = BinaryNode(RemovePunctuation(punctuationIsLeft = false), l, r)
    assert(n.span == sp)
    n
  }

  def findDerivation(node: TreeNode) : List[TransitionOption] =
    prepareDerivationTreeForTraining(node).allNodesPostorder.flatMap{
      case BinaryNode(Glue(), _, _)  =>
        Nil
      case BinaryNode(RightAdjoinCombinator(c, span), _, _)  =>
        RevealingOption() :: RightAdjoinOption(fakeNode(c, span)) :: Nil
      case BinaryNode(c, _, _)  =>
        BinaryReduceOption(c) :: Nil
      case TerminalNode(_, cat) =>
        ShiftOption() :: TaggingOption(cat) :: Nil
      case UnaryNode(c, _)      =>
        UnaryReduceOption(c) :: Nil
    }

}

object ParserProperties{

  def fromYaml(conf:YamlConfig) : ParserProperties = {
    ParserProperties(
      useIncrementalTrans     = conf("useIncrementalTrans"     ).bool,
      useRevealing            = conf("useRevealing"            ).bool,
      withObservedCatsOnly    = conf("withObservedCatsOnly"    ).bool,
      withForwardAntiEisnerNF = conf("withForwardAntiEisnerNF" ).bool,
      withForwardEisnerNF     = conf("withForwardEisnerNF"     ).bool,
      withBackwardEisnerNF    = conf("withBackwardEisnerNF"    ).bool,
      withPuncNF              = conf("withPuncNF"              ).bool,
      withHighTRconjNF        = conf("withHighTRconjNF"        ).bool,
      withLowTRconjNF         = conf("withLowTRconjNF"         ).bool,
      withLowLeftAdjNF        = conf("withLowLeftAdjNF"        ).bool,
      withLowRightAdjNF       = conf("withLowRightAdjNF"       ).bool,
      withHockenCatNormal     = conf("withHockenCatNormal"     ).bool,
      onlyPredefinedCombs     = conf("onlyPredefinedCombs"     ).bool,
    )
  }


  val prototypeProps = ParserProperties(
    useIncrementalTrans     = false,
    useRevealing            = false,

    withObservedCatsOnly    = false,
    withForwardAntiEisnerNF = false,
    withForwardEisnerNF     = false,
    withBackwardEisnerNF    = false,
    withPuncNF              = true,

    withHighTRconjNF        = false,
    withLowTRconjNF         = true,
    withLowLeftAdjNF        = false,
    withLowRightAdjNF       = false,
    withHockenCatNormal     = false,

    onlyPredefinedCombs     = false
  )


}


