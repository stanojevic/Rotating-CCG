package edin.ccg.representation.combinators

import edin.ccg.representation.CombinatorsContainer
import edin.ccg.representation.category._

trait CombinatorBinary extends Combinator {

  val functorIsLeft: Boolean

  def canApply(x:Category, y:Category) : Boolean

  def apply(x:Category, y:Category) : Category

  def isRightAdjCombinator(x:Category, y:Category) : Boolean =
    (y.isBackSlashAdjunctCategory || y.isInstanceOf[ConjCat])         &&
    this.canApply(x, y)                                               &&
    this(x, y) == x                                                   &&
    (this.isInstanceOf[Backwards]  || this.isInstanceOf[ConjunctionTop])

  def isLeftAdjCombinator(x:Category, y:Category) : Boolean =
    x.isFWDSlashAdjunctCategory  && this.canApply(x, y) && this(x, y) == y && this.isInstanceOf[B0fwd]

}

trait Backwards extends CombinatorBinary {
  override final val functorIsLeft: Boolean = false
  val order: Int
}

trait Forwards extends CombinatorBinary {
  override final val functorIsLeft: Boolean = true
  val order: Int
}

object Forwards{
  val b : List[Forwards] = List(
    CombinatorBinary.b0f,
    CombinatorBinary.b1f,
    CombinatorBinary.b2f// ,
    // CombinatorBinary.b3f,
    // CombinatorBinary.b4f
  )
  val maxB:Int = b.size-1
}

object CombinatorBinary{

  val b0f  = B0fwd()
  val b0b  = B0bck()

  val b1f  = B1fwd()
  val b1b  = B1bck( isCrossed = false )
  val b1bx = B1bck( isCrossed = true )

  val b2f  = B2fwd()
  val b2b  = B2bck( isCrossed = false )
  val b2bx = B2bck( isCrossed = true )

  val b3f  = B3fwd()

  val b4f  = B4fwd()

  val conj = Conjunction()
  val conjTop = ConjunctionTop()

  val puncLeft  = RemovePunctuation(punctuationIsLeft = true)
  val puncRight = RemovePunctuation(punctuationIsLeft = false)

  val allPunc               :Set[CombinatorBinary] = Set( puncRight, puncLeft )
  val allForward            :Set[CombinatorBinary] = Forwards.b.toSet
  val allBackwardAndCrossed :Set[CombinatorBinary] = Set( b0b, b1bx, b2bx, b1b, b2b, conjTop )


  private val c_and_c_additional : Set[CombinatorBinary] = Set(
    // PUNCTUATION RULES
    TypeChangeBinary(
      l = Category(""","""),
      r = Category("""NP"""),
      p = Category("""(S\NP)\(S\NP)""")
    ),
    TypeChangeBinary(
      l = Category("""NP"""),
      r = Category(""","""),
      p = Category("""S/S""")
    ),
    TypeChangeBinary(
      l = Category("""S[dcl]/S[dcl]"""),
      r = Category(""","""),
      p = Category("""S/S""")
    ),
    TypeChangeBinary(
      l = Category("""S[dcl]/S[dcl]"""),
      r = Category(""","""),
      p = Category("""(S\NP)\(S\NP)""")
    ),
    TypeChangeBinary(
      l = Category("""S[dcl]/S[dcl]"""),
      r = Category(""","""),
      p = Category("""(S\NP)/(S\NP)""")
    ),
    TypeChangeBinary(
      l = Category("""S[dcl]/S[dcl]"""),
      r = Category(""","""),
      p = Category("""S\S""")
    ),
    TypeChangeBinary(
      l = Category("""S[dcl]\S[dcl]"""),
      r = Category(""","""),
      p = Category("""S/S""")
    ),
    // COMMA COORDINATION RULES
    TypeChangeBinary(
      l = Category(""","""),
      r = Category("""N"""),
      p = Category("""N\N""")
    ),
    TypeChangeBinary(
      l = Category(""","""),
      r = Category("""NP"""),
      p = Category("""NP\NP""")
    ),
    TypeChangeBinary(
      l = Category(""","""),
      r = Category("""S[pss]"""),
      p = Category("""S[pss]\S[pss]""")
    ),
    TypeChangeBinary(
      l = Category(""","""),
      r = Category("""S[ng]"""),
      p = Category("""S[ng]\S[ng]""")
    ),
    TypeChangeBinary(
      l = Category(""","""),
      r = Category("""S[adj]"""),
      p = Category("""S[adj]\S[adj]""")
    ),
    TypeChangeBinary(
      l = Category(""","""),
      r = Category("""S[to]"""),
      p = Category("""S[to]\S[to]""")
    ),
    TypeChangeBinary(
      l = Category(""","""),
      r = Category("""S[dcl]"""),
      p = Category("""S[dcl]\S[dcl]""")
    ),
    // pss, ng, adj, to, dcl
    TypeChangeBinary(
      l = Category(""","""),
      r = Category("""S[pss]\NP"""),
      p = Category("""(S[pss]\NP)\(S[pss]\NP)""")
    ),
    TypeChangeBinary(
      l = Category(""","""),
      r = Category("""S[ng]\NP"""),
      p = Category("""(S[ng]\NP)\(S[ng]\NP)""")
    ),
    TypeChangeBinary(
      l = Category(""","""),
      r = Category("""S[adj]\NP"""),
      p = Category("""(S[adj]\NP)\(S[adj]\NP)""")
    ),
    TypeChangeBinary(
      l = Category(""","""),
      r = Category("""S[to]\NP"""),
      p = Category("""(S[to]\NP)\(S[to]\NP)""")
    ),
    TypeChangeBinary(
      l = Category(""","""),
      r = Category("""S[dcl]\NP"""),
      p = Category("""(S[dcl]\NP)\(S[dcl]\NP)""")
    ),
    TypeChangeBinary(
      l = Category(""","""),
      r = Category("""N/N"""),
      p = Category("""(N/N)\(N/N)""")
    ),
    TypeChangeBinary(
      l = Category(""","""),
      r = Category("""NP\NP"""),
      p = Category("""(NP\NP)\(NP\NP)""")
    ),
    TypeChangeBinary(
      l = Category(""","""),
      r = Category("""(S\NP)\(S\NP)"""),
      p = Category("""((S\NP)\(S\NP))\((S\NP)\(S\NP))""")
    ),
    TypeChangeBinary(
      l = Category(""";"""),
      r = Category("""NP"""),
      p = Category("""NP\NP""")
    ),
    TypeChangeBinary(
      l = Category(""";"""),
      r = Category("""S[pss]"""),
      p = Category("""S[pss]\S[pss]""")
    ),
    TypeChangeBinary(
      l = Category(""";"""),
      r = Category("""S[ng]"""),
      p = Category("""S[ng]\S[ng]""")
    ),
    TypeChangeBinary(
      l = Category(""";"""),
      r = Category("""S[adj]"""),
      p = Category("""S[adj]\S[adj]""")
    ),
    TypeChangeBinary(
      l = Category(""";"""),
      r = Category("""S[to]"""),
      p = Category("""S[to]\S[to]""")
    ),
    TypeChangeBinary(
      l = Category(""";"""),
      r = Category("""S[dcl]"""),
      p = Category("""S[dcl]\S[dcl]""")
    ),
    // pss, ng, adj, to, dcl
    TypeChangeBinary(
      l = Category(""";"""),
      r = Category("""S[pss]\NP"""),
      p = Category("""(S[pss]\NP)\(S[pss]\NP)""")
    ),
    TypeChangeBinary(
      l = Category(""";"""),
      r = Category("""S[ng]\NP"""),
      p = Category("""(S[ng]\NP)\(S[ng]\NP)""")
    ),
    TypeChangeBinary(
      l = Category(""";"""),
      r = Category("""S[adj]\NP"""),
      p = Category("""(S[adj]\NP)\(S[adj]\NP)""")
    ),
    TypeChangeBinary(
      l = Category(""";"""),
      r = Category("""S[to]\NP"""),
      p = Category("""(S[to]\NP)\(S[to]\NP)""")
    ),
    TypeChangeBinary(
      l = Category(""";"""),
      r = Category("""S[dcl]\NP"""),
      p = Category("""(S[dcl]\NP)\(S[dcl]\NP)""")
    ),
    // other rules
    TypeChangeBinary(
      l = Category("""NP"""),
      r = Category("""NP"""),
      p = Category("""NP""")
    ),
    TypeChangeBinary(
      l = Category("""S[dcl]"""),
      r = Category("""S[dcl]"""),
      p = Category("""S[dcl]""")
    ),
    TypeChangeBinary(
      l = Category("""conj"""),
      r = Category("""N"""),
      p = Category("""N""")
    )
  )

  private val my_additional = Set[CombinatorBinary](
//    TypeChangeBinary(Category("""NP/NP"""), Category("""NP/N""" ), Category("""NP/N""")),
//    TypeChangeBinary(Category("""NP/N""" ), Category("""NP\NP"""), Category("""NP/N"""))
  )

  private val chinese_predefined = Set[CombinatorBinary](
    TypeChangeBinary(
      l = Category("""conj"""),
      r = Category("""NP"""),
      p = Category("""NP""")
    ),
    TypeChangeBinary(
      l = Category("""NP"""),
      r = Category("""NP"""),
      p = Category("""NP""")
    ),
    TypeChangeBinary(
      l = Category("""((S\NP)/(S\NP))/(S\NP)"""),
      r = Category("""S[dcl]\NP"""),
      p = Category("""(S\NP)/(S\NP)""")
    ),
    TypeChangeBinary(
      l = Category("""conj"""),
      r = Category("""S[dcl]\NP"""),
      p = Category("""S[dcl]\NP""")
    ),
    TypeChangeBinary(
      l = Category("""S/QP"""),
      r = Category("""QP"""),
      p = Category("""S[frg]""")
    ),
    TypeChangeBinary(
      l = Category("""(S/S)/S"""),
      r = Category("""S[dcl]"""),
      p = Category("""S/S""")
    ),
    TypeChangeBinary(
      l = Category("""conj"""),
      r = Category("""NP/NP"""),
      p = Category("""NP/NP""")
    ),
    TypeChangeBinary(
      l = Category("""conj"""),
      r = Category("""S[dcl]"""),
      p = Category("""S[dcl]""")
    ),
    TypeChangeBinary(
      l = Category("""S/NP"""),
      r = Category("""NP"""),
      p = Category("""S[frg]""")
    ),
    TypeChangeBinary(
      l = Category("""S/S"""),
      r = Category("""S[dcl]\NP"""),
      p = Category("""S[dcl]\NP""")
    ),
    TypeChangeBinary(
      l = Category("""NP"""),
      r = Category("""NP"""),
      p = Category("""NP/NP""")
    )
  )

  private[combinators] def setLanguage(lang:String, combinatorsContainer: CombinatorsContainer) : Unit = lang match {
    case "English" | "English_CandC" =>
      allPredefinedVar = my_additional | allForward | allBackwardAndCrossed | allPunc + conj | c_and_c_additional
    case "English_EasyCCG" =>
      allPredefinedVar = my_additional | allForward | allBackwardAndCrossed | allPunc + conj
    case "Chinese" =>
      allPredefinedVar = my_additional | allForward | allBackwardAndCrossed | allPunc + conj | chinese_predefined
    case "General" =>
      allPredefinedVar = my_additional | allForward | allBackwardAndCrossed | allPunc + conj | combinatorsContainer.allUnholyBinary.toSet
  }

  private var allPredefinedVar  :Set[CombinatorBinary] = my_additional | c_and_c_additional | allForward | allBackwardAndCrossed | allPunc + conj
  def allPredefined : Set[CombinatorBinary] = allPredefinedVar

  def findCombinator(x:Category, y:Category, l:Iterable[CombinatorBinary]) : Iterable[CombinatorBinary] = l.view.filter(_.canApply(x, y))

  def isPredefined(c:CombinatorBinary) : Boolean = allPredefined contains c

}
