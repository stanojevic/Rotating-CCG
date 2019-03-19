package edin.ccg.representation.category

sealed case class NonTerm(val nt: String) extends Serializable {

  lazy val isPunctuation: Boolean = ! nt.matches("[A-Za-z]+") || List("LRB", "RRB", "LQU", "RQU").contains(nt)
  lazy val isConjunct: Boolean = List("conj", ",", ";",      ":", ".") contains nt
  lazy val isLeftBracketOrQuote: Boolean = nt == "LQU" || nt == "LRB"
  lazy val isNoun : Boolean = nt == "N"
  lazy val isNounPhrase: Boolean = nt == "NP"

  override def toString: String = nt

}
