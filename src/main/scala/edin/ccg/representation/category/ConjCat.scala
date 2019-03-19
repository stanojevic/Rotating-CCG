package edin.ccg.representation.category

@SerialVersionUID(-15085688163678538L)
final case class ConjCat(cat:Category) extends Category {

  override val isModifier: Boolean = true
  override val isFeatureLess: Boolean = cat.isFeatureLess
  override val isTypeRaised: Boolean = cat.isTypeRaised

  override def matches(other: Category): Boolean = other match {
    case ConjCat(cat2) if cat matches cat2 => true
    case _ => false
  }

  override def matchesFeatureless(other: Category): Boolean = other match {
    case ConjCat(cat2) if cat matchesFeatureless cat2 => true
    case _ => false
  }

  override def doSubstitution(substitute: Substitution): Category =
    ConjCat(cat.doSubstitution(substitute))

  override def getSubstitution(other: Category): Substitution = other match {
    case ConjCat(cat2) => cat getSubstitution cat2
    case _ => Substitution(Set())
  }

  override def toString: String = s"$cat[conj]"

  override def forPrinting(concise: Boolean): String = cat.forPrinting(concise)+"[conj]"

}
