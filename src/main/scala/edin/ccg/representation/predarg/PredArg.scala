package edin.ccg.representation.predarg

import edin.general.Global.projectDir
import edin.ccg.representation.category._
import edin.ccg.representation.combinators._
import edin.ccg.representation.transforms.AdjunctionGeneral.RightAdjoinCombinator
import edin.ccg.representation.tree.{BinaryNode, TerminalNode, TreeNode, UnaryNode}

import scala.io.Source

sealed trait PredArgState
case object Normal extends PredArgState
case class Coordination(right:TreeNode) extends PredArgState
case class WeirdCoordination(right:TreeNode) extends PredArgState
case object WeirdTC2 extends PredArgState

final case class PredArg(
                          formula         : Formula,
                          headWord        : Option[Word],
                          mainWord        : Word,
                          state           : PredArgState,
                          unfinishedDeps  : Set[UnfinishedDepLink],
                          newFinishedDeps : Set[DepLink]
                        ){

  // lazy val extractDependencies : List[DepLink] = finishedDeps().toList.flatMap(_.toFinalDeps)

  // lazy val activeWords: Set[Word] = unfinishedDeps.flatMap(_.headWords.words) + mainWord
  lazy val activeWords: Set[Word] = activeWordsAsFunction + mainWord

  /** most important */
  private lazy val activeWordsAsFunction:Set[Word] =
    formula match {
      case PAFunctor(_, _, _, right) =>
        val vars = right.vars
        unfinishedDeps.toList.withFilter(vars contains _.depWords.asInstanceOf[Var]).flatMap(_.headWords.words).toSet
      case PAAtom(_, _) =>
        Set()
    }

  /** less important */
  //noinspection ScalaUnusedSymbol
  private def activeWordsAsArgs(maxOrder:Int):Set[Word] = {
    val (head, args) = PredArg.cutArgs(formula, 1000)
    val vars = (head :: args.take(maxOrder)).flatMap(_.vars).toSet
    unfinishedDeps.toList.withFilter(vars contains _.depWords.asInstanceOf[Var]).flatMap(_.headWords.words).toSet
  }

  /** sort of backup plan if activeWordsAsFunction doesn't give any result */
  private def activeSimpleHeads : Set[Word] = PredArg.findHeadTerm(formula) match{
    case Words(ws) => ws
    case _ => headWord.toSet
  }

  private[predarg] val vars:List[Var] = formula.vars

  private[predarg] def renewMinVar(mmin:Int) : PredArg = {
    val replacements = formula.vars.zipWithIndex.map{case (v, i) => (v, v.copy(id=i+mmin))}.toMap
    val newFormula = formula.replaceVars(replacements)
    val newUnfinishedDeps = unfinishedDeps.map(_.replaceVar(replacements))
    this.copy(formula = newFormula, unfinishedDeps = newUnfinishedDeps)
  }

}

final case class UnfinishedDepLink(
                                    headCat   : Category,
                                    slot      : Int,
                                    headWords : Words,
                                    depWords  : Term,
                                    boundness : Boundness
                                  ){

  def replaceVar(subs:PredArg.SubstitutionLookup) : UnfinishedDepLink = depWords match {
    case x@Var(_) if subs.contains(x) => this.copy(depWords = subs(x))
    case _ => this
  }

  def toFinalDeps : List[DepLink] = (headWords, depWords) match {
    case (Words(hws), Words(dws)) =>
      for{h <- hws.toList ; d <- dws.toList}
        yield DepLink(
          headCat   = headCat,
          headPos   = h.wordPos,
          depPos    = d.wordPos,
          depSlot   = slot,
          headWord  = h.word,
          depWord   = d.word,
          boundness = boundness
        )
    case _ =>
      List()
  }

}

sealed trait Term extends {
  override def toString: String = this match {
    case Unavailable() => ""
    case Var(id) => s"$id"
    case Words(List(Word(wordPos, _))) => s"W$wordPos"
    case Words(words) => s"W${words.mkString(",")}"
  }
}
case class Unavailable() extends Term
case class Var(id:Int) extends Term
case class Words(words:Set[Word]) extends Term
sealed case class Word(wordPos:Int, word:String)

object PredArg {

  private val fn = s"$projectDir/src/main/scala/edin/ccg/representation/predarg/PredArgMapping.txt"
  lazy val mappingCatFormula : Map[Category, Formula] = Source.fromFile(fn).getLines().map{ line =>
    val fields = line.split("\t")
    (Category(fields(0)), Formula(fields(1)))
  }.toMap

  def forNode(node:TreeNode) : PredArg = {
    val parg = node match {
      case n@TerminalNode(_, _) =>
        processTerminalNode(n)
      case n@UnaryNode(_, _)     =>
        processUnaryNode(n)
      case n@BinaryNode(_, _, _)     =>
        processBinaryNode(n)
    }
    cleanDeps(parg.copy(formula = fixLeftformulaEdges(parg.formula)).renewMinVar(0))
  }

  private val fixLeftformulaEdges : Formula => Formula = {
    case PAFunctor(_, _, left, right) =>
      val nl = fixLeftformulaEdges(left)
      PAFunctor(nl.head, Local, nl, right)
      // PAFunctor(Unavailable(), Local, nl, right)
    case x =>
      x
  }

  private def cutArgs(f:Formula, toCut:Int): (Formula, List[Formula]) =
    if(toCut == 0)
      (f, Nil)
    else
      (f: @unchecked) match {
        case PAFunctor(_, _, left, right) =>
          val (h, as) = cutArgs(left, toCut-1)
          (h, as:+right)
        case _ =>
          (f, Nil)
      }

  type Substitution = Set[(Var, Term)]
  type SubstitutionLookup = Map[Var, Term]

  private def unify(lF:Formula, rF:Formula) : Substitution = ((lF, rF): @unchecked) match {
    case (PAFunctor(lh, _, ll, lr), PAFunctor(rh, _, rl, rr)) =>
      pair(lh, rh) match {
        case Some(x) =>
          unify(ll, rl) union unify(lr, rr) + x
        case None =>
          unify(ll, rl) union unify(lr, rr)
      }
    case (PAAtom(lh, _), PAAtom(rh, _)) =>
      pair(lh, rh) match {
        case Some(x) =>
          Set(x)
        case None =>
          Set()
      }
  }

  private def allNonLocalTerms : Formula => (Set[Term], Set[Term]) = {
    case PAFunctor(head, boundness, left, right) =>
      val (lB, lU) = allNonLocalTerms(left)
      val (rB, rU) = allNonLocalTerms(right)
      (head, boundness) match {
        case (x       , Bound)   => (lB union rB + x, lU union rU)
        case (x       , UnBound) => (lB union rB, lU union rU+x)
        case (_       , _      ) => (lB union rB, lU union rU)
      }
    case PAAtom(head, Bound) =>
      (Set(head), Set())
    case PAAtom(head, UnBound) =>
      (Set(), Set(head))
    case PAAtom(_, _) =>
      (Set(), Set())
  }

  private def pair(l:Term, r:Term) : Option[(Var, Term)] = (l, r) match {
    case (Unavailable(), _  ) => None
    case (_,   Unavailable()) => None
    case (l@Var(_), _       ) => Some((l, r))
    case (_       , r@Var(_)) => Some((r, l))
    case (_       , _       ) => None
  }

  private def replaceVars(parg:PredArg, subs:SubstitutionLookup) : PredArg = {
    val newF = parg.formula.replaceVars(subs)
    val (newUnfinished, newFinished) = parg.unfinishedDeps.toList.map(_.replaceVar(subs)).partition(_.depWords.isInstanceOf[Var])
    parg.copy(
      formula = newF,
      unfinishedDeps = newUnfinished.toSet,
      newFinishedDeps = newFinished.flatMap(_.toFinalDeps).toSet
    )
  }

  private def cleanDeps(parg:PredArg) : PredArg =
    parg.copy(
      unfinishedDeps = parg.unfinishedDeps.filter(d => parg.vars.contains(d.depWords))
    )

  private def normalCombine(mainParg:PredArg, nonMainParg2:PredArg, order:Int) : PredArg = {
    var nonMainParg = alphaTransformRight(mainParg, nonMainParg2)
    val (rMatch, rargs) = cutArgs(nonMainParg.formula, order)
    val (hLeft, List(lMatch)) = cutArgs(mainParg.formula, 1)
    val subs = unify(lMatch, rMatch).toList
    val subsLookup = subs.toMap
    val (lBound, lUnbound) = allNonLocalTerms(lMatch)
    val subsRedundant = (subs ++ subs.map(_.swap)).toMap
    val rBound = lBound.flatMap(subsRedundant.get)
    val rUnbound = lUnbound.flatMap(subsRedundant.get)
    nonMainParg = markBoundnessDep(nonMainParg, rBound, Bound)
    nonMainParg = markBoundnessDep(nonMainParg, rUnbound, UnBound)
    val res_unfinished  = rargs.foldLeft(hLeft)(PAFunctor(Unavailable(), Local, _, _))
    val parg_unfinished = PredArg(
      formula = res_unfinished,
      headWord = mainParg.headWord,
      mainWord = mainParg.mainWord,
      state = Normal,
      unfinishedDeps = mainParg.unfinishedDeps++nonMainParg.unfinishedDeps,
      newFinishedDeps = Set()
    )
    replaceVars(parg_unfinished, subsLookup)
  }

  private def markBoundnessDep(parg: PredArg, terms:Set[Term], boundness: Boundness) : PredArg =
    parg.copy(
      unfinishedDeps = parg.unfinishedDeps.map{ d =>
        // if(terms.contains(d.depWords) && d.boundness == Local)
        if(terms.contains(d.depWords) && (d.boundness==Local || boundness==UnBound))
          d.copy(boundness = boundness)
        else
          d
      }
    )

  private def markUnboundedVarsCoordination(parg:PredArg, slashes:List[Slash], isLeft:Boolean) : PredArg = {
    val (_, args) = cutArgs(parg.formula, 1000)
    val s = if(isLeft) Slash.FWD else Slash.BCK
    val terms = (args zip slashes)
      .withFilter(_._2==s)
      // .withFilter(_._1.head.isInstanceOf[Var])
      .map(_._1.head)
      .toSet
    markBoundnessDep(parg, terms, UnBound)
  }

  private def alphaTransformRight(lParg:PredArg, rParg:PredArg) : PredArg =
    rParg.renewMinVar(lParg.vars.size)

  private val findHeadTerm : Formula => Term = {
    case PAFunctor(_, _, left, _) => findHeadTerm(left)
    case PAAtom(h, _) => h
  }

  private def unifyCoordinationHeads(lf: Formula, rf: Formula) : Term = (findHeadTerm(lf), findHeadTerm(rf)) match {
    case (Words(words1), Words(words2)) =>
      Words(words1++words2)
    case (h, _) =>
      h
  }

  private def replaceHeadTerm(f:Formula, h:Term) : Formula = f match {
    case PAFunctor(head, boundness, left, right) =>
      PAFunctor(head, boundness, replaceHeadTerm(left, h), right)
    case PAAtom(_, b) =>
      PAAtom(h, b)
  }

  private def coordCombine(lParg3 :PredArg, rParg3 :PredArg, slashes:List[Slash]) : PredArg = {
    val lParg2 = lParg3
    val rParg2 = alphaTransformRight(lParg2, rParg3)
    val lParg = markUnboundedVarsCoordination(lParg2, slashes, isLeft = true)
    val rParg = markUnboundedVarsCoordination(rParg2, slashes, isLeft = false)
    val subs = unify(lParg.formula, rParg.formula)
    val res2 = trivialCombine(lParg, rParg, Normal)
    val res3 = res2.copy(
      formula = replaceHeadTerm(res2.formula, unifyCoordinationHeads(lParg.formula, rParg.formula))
    )
    replaceVars(res3, subs.toMap)
  }

  private def trivialCombine(mainParg:PredArg, nonMainParg2:PredArg, state:PredArgState) : PredArg = {
    val nonMainParg = alphaTransformRight(mainParg, nonMainParg2)
    PredArg(
      formula = mainParg.formula,
      headWord = mainParg.headWord,
      mainWord = mainParg.mainWord,
      state = state,
      unfinishedDeps = mainParg.unfinishedDeps union nonMainParg.unfinishedDeps,
      newFinishedDeps = Set()
    )
  }

  private val findSlashes : Category => List[Slash] = {
    case Functor(slash, l, _) => findSlashes(l) :+ slash
    case _ => Nil
  }

  private def adjunctCombine(adjParg:PredArg, mainParg:PredArg) : PredArg = {
    val pa = trivialCombine(mainParg, adjParg, Normal)
    val slot = cutArgs(adjParg.formula, 10000)._2.size
    val headWord = Words(mainParg.activeSimpleHeads)
    val adjHead = Words(Set(adjParg.headWord.get))
    adjParg.unfinishedDeps.find(d => d.headWords==adjHead && d.slot==slot) match{
      case Some(d) =>
        val newDep = d.copy(depWords = headWord)
        pa.copy(
          newFinishedDeps = newDep.toFinalDeps.toSet
        )
      case None =>
        pa
    }
  }

  private val isAdjunctionCombination : TreeNode => Boolean = {
    case BinaryNode(c, l, r) if c.isRightAdjCombinator(l.category, r.category) =>
      val mParg = l.predArg
      val aParg = r.predArg
      val v = findHeadTerm(aParg.formula.asInstanceOf[PAFunctor].right)
      val deps = aParg.unfinishedDeps.filter(d => d.depWords==v)
      deps.size == 1 && mParg.activeSimpleHeads.nonEmpty && aParg.headWord.nonEmpty
    case BinaryNode(c, l, r) if c.isLeftAdjCombinator(l.category, r.category) =>
      val mParg = r.predArg
      val aParg = l.predArg
      val v = findHeadTerm(aParg.formula.asInstanceOf[PAFunctor].right)
      val deps = aParg.unfinishedDeps.filter(d => d.depWords==v)
      deps.size == 1 && mParg.activeSimpleHeads.nonEmpty && aParg.headWord.nonEmpty
    case _ =>
      false
  }

  private def weirdCoordCombine(lParg:PredArg, rParg:PredArg, parentCat:Category) : PredArg = {
    val lHeadTerm = findHeadTerm(lParg.formula)
    val rHeadTerm = findHeadTerm(rParg.formula)
    (lHeadTerm, rHeadTerm) match {
      case (lws:Words, rws:Words) =>
        val w = Words(lws.words++rws.words)
        val lParg2 = trivialCombine(lParg, rParg, Normal)
        trivialCombine(lParg2, rParg, Normal).copy(formula = replaceHeadTerm(lParg.formula, w))
      case _ =>
        trivialCombine(lParg, rParg, Normal).copy(formula = cat2formula(parentCat))
    }
  }

  private def processBinaryNode(node:BinaryNode) : PredArg = {
    val lParg = node.leftChild.predArg
    val rParg = node.rightChild.predArg
    node.c match {
      case RightAdjoinCombinator(_, _) | Glue() =>
        lParg.copy(newFinishedDeps = Set())
      case ConjunctionTop() =>
        rParg.state match {
          case Coordination(r) =>
            val lPargA = trivialCombine(node.leftChild.predArg, node.rightChild.predArg, Normal)
            val rPargA = r.predArg
            val slashes = findSlashes(node.leftChild.category)
            coordCombine(lPargA, rPargA, slashes)
          case WeirdCoordination(r) =>
            weirdCoordCombine(lParg, rParg, node.category)
//            val lHeadTerm = findHeadTerm(node.leftChild.predArg.formula)
//            val rHeadTerm = findHeadTerm(r.predArg.formula)
//            (lHeadTerm, rHeadTerm) match {
//              case (lws:Words, rws:Words) =>
//                val w = Words(lws.words++rws.words)
//                val lParg2 = trivialCombine(lParg, node.rightChild.predArg, Normal)
//                trivialCombine(lParg2, r.predArg, Normal).copy(formula = replaceHeadTerm(lParg.formula, w))
//              case _ =>
//                trivialCombine(lParg, rParg, Normal).copy(formula = cat2formula(node.category))
//            }
          case _ =>
            trivialCombine(lParg, rParg, Normal)
        }
      case c:Forwards =>
        if(isAdjunctionCombination(node)){
          adjunctCombine(lParg, rParg)
        }else{
          normalCombine(lParg, rParg, c.order)
        }
      case c:Backwards =>
        rParg.state match {
          case WeirdCoordination(r) if c.order == 0 =>
            weirdCoordCombine(lParg, rParg, node.category)
          case WeirdCoordination(r) =>
            trivialCombine(lParg, rParg, Normal).copy(formula = cat2formula(node.category))
//            val lHeadTerm = findHeadTerm(node.leftChild.predArg.formula)
//            val rHeadTerm = findHeadTerm(r.predArg.formula)
//            (lHeadTerm, rHeadTerm) match {
//              case (lws:Words, rws:Words) if c.order==0 =>
//                val w = Words(lws.words++rws.words)
//                val lParg2 = trivialCombine(lParg, node.rightChild.predArg, Normal)
//                trivialCombine(lParg2, r.predArg, Normal).copy(formula = replaceHeadTerm(lParg.formula, w))
//              case _ =>
//                trivialCombine(lParg, rParg, Normal).copy(formula = cat2formula(node.category))
//            }
          case WeirdTC2 =>
            trivialCombine(lParg, rParg, Normal).copy(formula = cat2formula(node.category))
          case _ if isAdjunctionCombination(node) =>
            adjunctCombine(rParg, lParg)
          case _ =>
            normalCombine(rParg, lParg, c.order)
        }
      case Conjunction() =>
        trivialCombine(rParg, lParg, Coordination(node.rightChild)) // .copy(formula = cat2formula(node.category))
      case RemovePunctuation(true) | Glue() =>
        trivialCombine(rParg, lParg, rParg.state)
      case RemovePunctuation(false) =>
        trivialCombine(lParg, rParg, lParg.state)
      case TypeChangeBinary(_, r, p) if r matches p =>
        trivialCombine(rParg, lParg, rParg.state)
      case TypeChangeBinary(l, _, p) if l matches p =>
        trivialCombine(lParg, rParg, lParg.state)
      case TypeChangeBinary(Functor(_, _, x), Atomic(nt, _), to@Functor(_, _, y)) if (x matches y) && nt.isPunctuation =>
        val newFormula1 = cat2formula(node.category)
        val childParg = node.leftChild.predArg
        val childF = childParg.formula
        val PAFunctor(_, _, _, r) = childF
        val newFormula2 = newFormula1.asInstanceOf[PAFunctor].copy(right = r)
        val unfinishedDeps = if (to matches Category.S_fwd_S) {
          childParg.unfinishedDeps.map(_.copy(boundness = Bound))
        } else {
          childParg.unfinishedDeps
        }
        val f = to match {
          case Functor(_, lCat, rCat) if lCat == rCat => // if it's an adjunct category
            replaceHeadTerm(newFormula2, r.head)
          case _ =>
            replaceHeadTerm(newFormula2, findHeadTerm(childF))
        }
        childParg.copy(
          formula = f,
          unfinishedDeps = unfinishedDeps
        )
      case c@TypeChangeBinary(Atomic(_, _), _, Functor(_, Atomic(_, _), Atomic(_, _))) if c.isCoordinationOfNotAlikes =>
        trivialCombine(rParg, lParg, WeirdCoordination(node.rightChild)).copy(formula = cat2formula(node.category))
      case c@TypeChangeBinary(_, Atomic(_, _), Functor(_, Atomic(_, _), Atomic(_, _))) if c.isCoordinationOfNotAlikes =>
        trivialCombine(lParg, rParg, WeirdCoordination(node.rightChild)).copy(formula = cat2formula(node.category))
      case TypeChangeBinary(_, _, _) =>
        trivialCombine(lParg, rParg, WeirdTC2).copy(formula = cat2formula(node.category))
    }
  }

  private val cat2formula : Category => Formula = {
    case Functor(_, l, r) =>
      PAFunctor(Unavailable(), Local, cat2formula(l), cat2formula(r))
    case Atomic(_, _) =>
      PAAtom(Unavailable(), Local)
    case ConjCat(subcat) =>
      PAFunctor(Unavailable(), Local, cat2formula(subcat), cat2formula(subcat))
  }

  private def processUnaryNode(node:UnaryNode) : PredArg = {
    val childParg = node.child.predArg
    val childF = childParg.formula
    node.c match {
      case TypeRaiser(_, funcResult, _) =>
        val newVar = Var(childParg.vars.size)
        val funcF = replaceHeadTerm(cat2formula(funcResult), newVar)
        val formula = PAFunctor(newVar, Local, funcF, PAFunctor(newVar, Local, funcF, childF))
        childParg.copy(formula = formula, headWord = None)
      case c if c.isUnaryCoordination =>
      // case TypeChangeUnary(x, Functor(Slash.BCK, y, z)) if y==z && (x matches y) =>
        childParg.copy(state = Coordination(node.child), formula = cat2formula(node.category))
      case TypeChangeUnary(from@Functor(_, _, x), to@Functor(_, _, y)) if (x matches y) || (x == Category.NP && y == Category.N) =>
        val newFormula1 = cat2formula(node.category)
        val PAFunctor(_, _, _, r) = childF
        val newFormula2 = newFormula1.asInstanceOf[PAFunctor].copy(right = r)
        val unfinishedDeps = if(from matches Category.S_back_NP){
          childParg.unfinishedDeps.map(_.copy(boundness = Bound))
        }else if(from matches Category.S_fwd_NP){
          childParg.unfinishedDeps.map(_.copy(boundness = UnBound))
        }else{
          childParg.unfinishedDeps
        }
        val f = to match {
          case Functor(_, lCat, rCat) if lCat == rCat => // if it's an adjunct category
            replaceHeadTerm(newFormula2, r.head)
          case _ =>
            replaceHeadTerm(newFormula2, findHeadTerm(childF))
        }
        childParg.copy(
          formula = f,
          unfinishedDeps = unfinishedDeps
        )
      case _ =>
        val newFormula1 = cat2formula(node.category)
        val f = replaceHeadTerm(newFormula1, findHeadTerm(childF))
        childParg.copy(
          formula = f,
          unfinishedDeps = childParg.unfinishedDeps
        )
    }
  }

  private def isBoundedFormula(f:Formula) : Boolean = {
    val (b, u) = allNonLocalTerms(f)
    b.nonEmpty || u.nonEmpty
  }

  private val specialIgnoreCat = Category("""(S[to]\NP)/(S[b]\NP)""")

  private def processTerminalNode(node:TerminalNode) : PredArg = {
    val formula = mappingCatFormula(node.cat).assignHeadWord(node.span._1, node.word)
    val ds = cutArgs(formula, 10000)._2.map(x => (x.head, x.boundness))
    val word = Word(node.position, node.word)
    var unfinishedDeps = ds.zipWithIndex.reverse.take(countDeps(node.category, formula))map{ case ((d:Term, b:Boundness), i:Int) =>
      UnfinishedDepLink(
        headCat   = node.category,
        slot      = i+1,
        headWords = Words(Set(word)),
        depWords  = d,
        boundness = b
      )
    }
    if(node.category == specialIgnoreCat)
      unfinishedDeps = unfinishedDeps.filterNot(_.slot==1)
    PredArg(
      formula         = formula,
      headWord        = if(isBoundedFormula(formula)) None else Some(word),
      mainWord        = word,
      state           = Normal,
      unfinishedDeps  = unfinishedDeps.toSet,
      newFinishedDeps = Set()
    )
  }

  private val countDeps: (Category, Formula) => Int = {
    case (Functor(_, l, r), PAFunctor(_, _, left, right)) if l==r && left==right => 1
    case (Functor(_, l, _), PAFunctor(_, _, left, _)) => 1+countDeps(l, left)
    case _ => 0
  }

//  def main(args:Array[String]) : Unit = {
//    val pargs = loadPredArgFromFile("/home/milos/Projects/CCG-translator/tmp/ccg_extracted2/dev.parg")
//    val trees = DerivationsLoader.fromFile("/home/milos/Projects/CCG-translator/tmp/ccg_extracted2/dev.trees").toList
//    var i_fail = 0
//    var i = 0
//
//    var cm = 0
//    var cs = 0
//    var cr = 0
//
//    for((tree, origDeps) <- trees zip pargs){
//      i+=1
//       val predDeps = tree.predArg.extractDependencies.toSet
//       // val origDeps = tree.predArg.toFinalDeps
////      val predDeps = tree.toLeftBranching.toRightBranching.extractDependencies.toSet
////      val origDeps = tree.extractDependencies.toSet
//      //if(predDeps != origDeps.toSet){
//        i_fail += 1
//        // System.err.println(s"fail $i_fail")
//
//        // println("correct")
//        for(dep <- (predDeps intersect origDeps.toSet).toList.sortBy(_.headPos)) {
//          // println(List(dep.headPos, dep, mappingCatFormula(dep.headCat)).mkString("\t"))
//          cm+=1
//          cs+=1
//          cr+=1
//        }
//        // println("wrongly predicted")
//        for(dep <- (predDeps diff origDeps.toSet).toList.sortBy(_.headPos)){
//          println(List(dep.headPos, dep, "\t", mappingCatFormula(dep.headCat)).mkString("\t"))
//          cs+=1
//        }
//        // println("wrongly missing")
//        for(dep <- (origDeps.toSet diff predDeps).toList.sortBy(_.headPos)){
//          println(List(dep.headPos, dep, "\t", mappingCatFormula(dep.headCat)).mkString("\t"))
//          cr+=1
//        }
//
//        // println(i)
//        // println(s"\n\n\n\n")
////      }else{
////        System.err.println(s"success $i_success")
////      }
//    }
//    println(pargs.size)
//    println(trees.size)
//
//    // println(s"success $i_success")
//    println(s"fail $i_fail")
//    println(s"matched $cm")
//    println(s"system $cs")
//    println(s"reference $cr")
//    val p = cm.toDouble/cs
//    val r = cm.toDouble/cr
//    val f = 2*p*r/(p+r)
//    println(s"p: $p")
//    println(s"r: $r")
//    println(s"f: $f")
//  }

  def loadPredArgFromFile(fn:String) : List[List[DepLink]] = {
    var allDeps = List[List[DepLink]]()
    var currDeps = List[DepLink]()
    Source.fromFile(fn).getLines().foreach{ line =>
      if(line == "<\\s>"){
        allDeps ::= currDeps.reverse
        currDeps = List()
      }else if(! line.startsWith("<s id")){
        val fields = line.split("\\s+")
        currDeps ::= DepLink(
          headCat = Category(fields(2)),
          headPos = fields(1).toInt,
          depPos = fields(0).toInt,
          depSlot = fields(3).toInt,
          headWord = fields(5),
          depWord = fields(4),
          boundness = if(fields.size>6 && fields(6)=="<XB>") Bound else if(fields.size>6 && fields(6)=="<XU>") UnBound else Local
        )
      }
    }
    allDeps.reverse
  }

//  // rename this to main if you want to extract PredArgMapping.txt
//  def main2(args:Array[String]) : Unit = {
//    assert(args.length > 0)
//
//    System.err.println("extracting mapping")
//    val mapping = MutMap[String, String]()
//    for(f <- args){
//      for(line <- Source.fromFile(f).getLines().filter(_.startsWith("("))){
//        line.split("<L ").toList.tail.map(_.split(">").head).foreach{ lexEntry =>
//          val fields = lexEntry.split(" ")
//          val catStr = fields(0)
//          val predargStr = fields(4)
//          if(catStr != """((S[b]\NP)/NP)/"""){
//            mapping(catStr) = predargStr
//          }
//        }
//      }
//    }
//    for((catStr, predargStr) <- mapping){
//      println(s"$catStr\t$predargStr")
//    }
//  }

}
