package edin.ccg.representation.combinators

import edin.ccg.representation.CombinatorsContainer

trait Combinator extends Serializable

object Combinator{

  var language:String = "English"

  def setLanguage(lang:String, combinatorsContainer: CombinatorsContainer) : Unit = {
    CombinatorUnary.setLanguage(lang, combinatorsContainer)
    CombinatorBinary.setLanguage(lang, combinatorsContainer)
    this.language = lang
  }

  val isPredefined : Combinator => Boolean = {
    case c:CombinatorBinary => CombinatorBinary.isPredefined(c)
    case c:CombinatorUnary  => CombinatorUnary.isPredefined(c)
  }

}
