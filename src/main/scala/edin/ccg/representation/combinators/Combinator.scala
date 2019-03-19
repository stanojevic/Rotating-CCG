package edin.ccg.representation.combinators

import edin.ccg.representation.category.Category

trait Combinator extends Serializable

object Combinator{

  val isPredefined : Combinator => Boolean = {
    case c:CombinatorBinary => CombinatorBinary.isPredefined(c)
    case c:CombinatorUnary  => CombinatorUnary.isPredefined(c)
  }

  def main(args:Array[String]) : Unit = {
    val l = Category("""(NP\NP)/(S[dcl]/NP)""")
    val r = Category("""((((S[dcl]\NP)/NP)/(S[to]\NP))/(S[adj]\NP))/NP[expl]""")
    println(B4fwd().canApply(l, r))
    println(B3fwd().canApply(l, r))
    println("Hello")
  }

}
