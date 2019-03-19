package edin.ccg.representation.predarg

import edin.ccg.representation.predarg.PredArg.SubstitutionLookup

import scala.util.parsing.combinator._

sealed trait Formula {

  val head      : Term
  val boundness : Boundness

//  def renewMinVar(mmin:Int) : Formula = {
//    val replacements = this.vars.zipWithIndex.map{case (v, i) => (v, v.copy(id=i+mmin))}.toMap
//    replacements.foldLeft(this){case (f, (x, y)) => f.replaceVar(x, y)}
//  }

  final val vars: List[Var] = this match {
    case PAAtom(x@Var(_), _) => List(x)
    case PAAtom(_       , _) => List()
    case PAFunctor(x@Var(_), _, left, right)  => (x :: left.vars ++ right.vars).distinct
    case PAFunctor(_       , _, left, right)  =>      (left.vars ++ right.vars).distinct
  }

  def replaceVars(subs:SubstitutionLookup) : Formula = this match {
    case PAFunctor(h@Var(_), b, left, right) if subs contains h => PAFunctor(subs(h), b, left.replaceVars(subs), right.replaceVars(subs))
    case PAFunctor(h       , b, left, right) => PAFunctor(h, b, left.replaceVars(subs), right.replaceVars(subs))
    case PAAtom(h@Var(_), b) if subs contains h => PAAtom(subs(h), b)
    case PAAtom(h       , b) => PAAtom(h, b)
  }

//  final def replaceVar(x: Var, y: Term): Formula = this match {
//    case PAFunctor(`x`, b, left, right) => PAFunctor(y, b, left.replaceVar(x, y), right.replaceVar(x, y))
//    case PAFunctor( h , b, left, right) => PAFunctor(h, b, left.replaceVar(x, y), right.replaceVar(x, y))
//    case PAAtom(`x`, b) => PAAtom(y, b)
//    case PAAtom( h , b) => PAAtom(h, b)
//  }

  def assignHeadWord(wordPos:Int, wordStr:String): Formula = this match {
    case PAFunctor(h, b, left, right) =>
      PAFunctor(h, b, left.assignHeadWord(wordPos, wordStr), right)
    case PAAtom(Unavailable(), b) =>
      PAAtom(Words(Set(Word(wordPos, wordStr))), b)
    case _ =>
      this
  }

  override final def toString: String = this match {
    case PAFunctor(h, b, left, right) => s"( $left | $right )$h$b"
    case PAAtom(h, b) => s"$h$b"
  }

}

object Formula{

  def apply(s:String) : Formula = {
    val parser = new FormulaParser
    parser.parse(parser.formula, s).get
  }

  private class FormulaParser extends RegexParsers {
    def label: Parser[String]    = """([A-Za-z]+(\[[a-z]+])?)|,|.""".r
    def num  : Parser[Int]       = """[0-9]+""".r ^^ (_.toInt)
    def b    : Parser[Boundness] = (
      ":B" ^^ (_ => Bound     )
        | ":U" ^^ (_ => UnBound   )
        | ""   ^^ (_ => Local)
      )
    def varr: Parser[Term] = opt("_"~num) ^^ {
      case Some(_~num) => Var(id=num)
      case None        => Unavailable()
    }
    def atom          : Parser[PAAtom]  = label~varr~b          ^^ {case _~varr~b => PAAtom(head=varr, boundness = b)}
    def bracketFormula: Parser[Formula] = "("~formula~")"~varr~b  ^^ {case _~(x@PAFunctor(_, _, _, _))~_~varr~b => x.copy(head=varr, boundness=b)}
    def formula       : Parser[Formula] = repsep(bracketFormula | atom, "/"|"\\") ^^ (_.reduceLeft(PAFunctor(Unavailable(), Local, _, _)))
  }

  def renewMinVar(formula:Formula, mmin:Int) : Formula = {
    val replacements = formula.vars.zipWithIndex.map{case (v, i) => (v, v.copy(id=i+mmin))}.toMap
    // val newFormula = replacements.foldLeft(formula){case (f, (x, y)) => f.replaceVar(x, y)}
    val newFormula = formula.replaceVars(replacements)

    newFormula
  }

}
final case class PAFunctor(head:Term, boundness:Boundness, left:Formula, right:Formula) extends Formula
final case class PAAtom(head:Term, boundness:Boundness) extends Formula
