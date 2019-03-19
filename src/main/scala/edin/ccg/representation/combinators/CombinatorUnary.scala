package edin.ccg.representation.combinators

import edin.ccg.representation.category.{Category, Slash}

object CombinatorUnary{

  val typeRaiserSubject = TypeRaiser(
    fromMatcher= Category("NP"),
    functionResult= Category("S"),
    slash = Slash.FWD // Functor(Slash.FWD, Atomic.fromString("S"), Functor(Slash.BCK, Atomic.fromString("S"), Atomic.fromString("NP")))
  )

  def isPredefined(c:CombinatorUnary) : Boolean = allPredefined.contains(c)

  private val a_star_predefined : List[CombinatorUnary] = List[CombinatorUnary](

  // N       NP
    TypeChangeUnary(
      from= Category("N"),
      to= Category("NP") // unary hard coded
    ),

    // # Relativization, as in "the boy playing tennis"
    // S[pss]\NP      NP\NP
    TypeChangeUnary(
      from= Category("""S[pss]\NP"""),
      to= Category("""NP\NP""") // unary hard coded
    ),

    // S[ng]\NP       NP\NP
    TypeChangeUnary(
      from= Category("""S[ng]\NP"""),
      to= Category("""NP\NP""") // unary hard coded
    ),

    // S[adj]\NP      NP\NP
    TypeChangeUnary(
      from= Category("""S[adj]\NP"""),
      to= Category("""NP\NP""") // unary hard coded
    ),
    // S[to]\NP       NP\NP
    TypeChangeUnary(
      from= Category("""S[to]\NP"""),
      to= Category("""NP\NP""") // unary hard coded
    ),
    // S[to]\NP       N\N
    TypeChangeUnary(
      from= Category("""S[to]\NP"""),
      to= Category("""N\N""") // unary hard coded
    ),
    // S[dcl]/NP       NP\NP
    TypeChangeUnary(
      from= Category("""S[dcl]/NP"""),
      to= Category("""NP\NP""") // unary hard coded
    ),

    // # Rules that let verb-phrases modify sentences, as in "Born in Hawaii, Obama is the 44th president."
    // S[pss]\NP      S/S
    TypeChangeUnary(
      from= Category("""S[pss]\NP"""),
      to= Category("""S/S""") // unary hard coded
    ),
    // S[ng]\NP       S/S
    TypeChangeUnary(
      from= Category("""S[ng]\NP"""),
      to= Category("""S/S""") // unary hard coded
    ),
    // S[to]\NP       S/S
    TypeChangeUnary(
      from= Category("""S[to]\NP"""),
      to= Category("""S/S""") // unary hard coded
    ),

    // # Type raising
    // Type raise 1
    // NP      S[X]/(S[X]\NP)
    TypeRaiser(
      fromMatcher = Category("NP"),
      functionResult = Category("S"),
      slash=Slash.FWD
    ),
    // Type raise 2
    // NP      (S[X]\NP)\((S[X]\NP)/NP)
    TypeRaiser(
      fromMatcher= Category("NP"),
      functionResult = Category("""S\NP"""),
      slash= Slash.BCK
    ),
    // Type raise 3
    // PP      (S[X]\NP)\((S[X]\NP)/PP)
    TypeRaiser(
      fromMatcher = Category("PP"),
      functionResult = Category("""S\NP"""),
      slash = Slash.BCK
    )
  )

  // from appendix A of C&C paper
  private val c_and_c_additional : List[CombinatorUnary] = List(
    TypeRaiser(
      fromMatcher= Category("NP"),
      functionResult = Category("""(S\NP)/NP"""),
      slash= Slash.BCK
    ),
    TypeRaiser(
      fromMatcher= Category("NP"),
      functionResult = Category("""(S\NP)/PP"""),
      slash= Slash.BCK
    ),
    TypeRaiser(
      fromMatcher= Category("NP"),
      functionResult = Category("""(S\NP)/(S[to]\NP)"""),
      slash= Slash.BCK
    ),
    TypeRaiser(
      fromMatcher= Category("NP"),
      functionResult = Category("""(S\NP)/(S[adj]\NP)"""),
      slash= Slash.BCK
    ),
    TypeRaiser(
      fromMatcher= Category("""S[adj]\NP"""),
      functionResult = Category("""S\NP"""),
      slash= Slash.BCK
    ),
    TypeChangeUnary(
      from= Category("""NP"""),
      to= Category("""S/(S/NP)""")
    ),
    TypeChangeUnary(
      from= Category("""NP"""),
      to= Category("""NP/(NP\NP)""")
    ),
    TypeChangeUnary(
      from= Category("""(S[to]\NP)/NP"""),
      to= Category("""NP\NP""")
    ),
    TypeChangeUnary(
      from= Category("""S[dcl]/NP"""),
      to= Category("""NP\NP""")
    ),
    TypeChangeUnary(
      from= Category("""S[dcl]"""),
      to= Category("""NP\NP""")
    ),
    TypeChangeUnary(
      from= Category("""S[pss]\NP"""),
      to= Category("""(S\NP)\(S\NP)""")
    ),
    TypeChangeUnary(
      from= Category("""S[ng]\NP"""),
      to= Category("""(S\NP)\(S\NP)""")
    ),
    TypeChangeUnary(
      from= Category("""S[adj]\NP"""),
      to= Category("""(S\NP)\(S\NP)""")
    ),
    TypeChangeUnary(
      from= Category("""S[to]\NP"""),
      to= Category("""(S\NP)\(S\NP)""")
    ),
    TypeChangeUnary(
      from= Category("""S[ng]\NP"""),
      to= Category("""(S\NP)/(S\NP)""")
    ),
    TypeChangeUnary(
      from= Category("""S[adj]\NP"""),
      to= Category("""S/S""")
    ),
    TypeChangeUnary(
      from= Category("""S[ng]\NP"""),
      to= Category("""S\S""")
    ),
    TypeChangeUnary(
      from= Category("""S[dcl]\NP"""),
      to= Category("""S\S""")
    ),
    TypeChangeUnary(
      from= Category("""S[ng]\NP"""),
      to= Category("""N\N""")
    ),
    TypeChangeUnary(
      from= Category("""S[dcl]"""),
      to= Category("""S\S""")
    ),
    TypeChangeUnary(
      from= Category("""S[dcl]\NP"""),
      to= Category("""S\S""")
    ),
    TypeChangeUnary(
      from= Category("""S[ng]\NP"""),
      to= Category("""NP""")
    )
  )

  val allPredefined : List[CombinatorUnary] = (a_star_predefined ++ c_and_c_additional).distinct

}

trait CombinatorUnary extends Combinator {

  def isUnaryCoordination: Boolean

  def canApply(x: Category): Boolean

  def apply(x: Category): Category

}

