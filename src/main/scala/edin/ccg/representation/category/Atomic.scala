package edin.ccg.representation.category

@SerialVersionUID(5543129395156549164L)
final case class Atomic(label:NonTerm, feature:Feature) extends Category{

  override val isTypeRaised: Boolean = false

  override val isModifier: Boolean = false

  override val isFeatureLess: Boolean = feature match {
    case FeatureEmpty() => true
    case _ => false
  }

  override def toString: String = forPrinting(concise = false)

  override def doSubstitution(substitute: Substitution): Category = substitute.get(label) match {
    case None =>
      this
    case Some(f) =>
      feature match {
        case FeatureEmpty() => Atomic(label, f)
        case _ => this
      }
  }

  override def getSubstitution(other: Category): Substitution = other match {
    case Atomic(`label`, oFeature) =>
      (feature, oFeature) match {
        case (FeatureEmpty(), f @ FeatureNonEmpty(_)) =>
          Substitution(Set((false, label, f)))
        case (f @ FeatureNonEmpty(_), FeatureEmpty()) =>
          Substitution(Set((true, label, f)))
        case _ =>
          Substitution(Set())
      }
    case _ =>
      Substitution(Set())
  }

  override def matchesFeatureless(other: Category): Boolean = other match{
    case Atomic(`label`, _) => true
    case _ => false
  }

  override def matches(other: Category): Boolean = other match{
    case Atomic(`label`, ofeature) if feature.matches(ofeature) => true
    case _ => false
  }

  def forPrinting(concise:Boolean) : String = label.toString + feature.toString

}

object Atomic{

  def fromString(s:String) : Atomic ={
    s.replaceAll("]", "").split("\\[").toList match{
      case List(n) =>
        val feat = FeatureEmpty()()
        val nt = NonTerm(n)
        Atomic(nt, feat)
      case List(n, f) =>
        val feat = if(n == "NP" && f == "nb"){
          FeatureEmpty()(f)
        }else{
          FeatureNonEmpty(f)
        }
        val nt = NonTerm(n)
        Atomic(nt, feat)
    }
  }
}

