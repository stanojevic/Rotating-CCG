package edin.ccg.representation.category


@SerialVersionUID(4288342574572750131L)
final case class Functor(slash:Slash, left: Category, right:Category) extends Category{

  override lazy val isModifier: Boolean = left == right

  override val isFeatureLess: Boolean = left.isFeatureLess && right.isFeatureLess

  override val isTypeRaised: Boolean = right match {
    case Functor(_, rightLeft, _) => rightLeft == left
    case _ => false
  }

  override def toString: String = forPrinting(concise = false)

  override def doSubstitution(substitute: Substitution): Category =
    Functor(slash, left.doSubstitution(substitute), right.doSubstitution(substitute))

  override def getSubstitution(other: Category) : Substitution =
    other match {
      case Functor(`slash`, oLeft, oRight) =>
        left.getSubstitution(oLeft) + right.getSubstitution(oRight)
      case _ =>
        Substitution(Set())
    }

  override def matchesFeatureless(other: Category): Boolean =
    other match {
      case Functor(oSlash, oLeft, oRight) =>
        slash == oSlash && left.matchesFeatureless(oLeft) && right.matchesFeatureless(oRight)
      case _ =>
        false
    }

  override def matches(other: Category): Boolean =
    other match {
      case Functor(oSlash, oLeft, oRight) =>
        slash == oSlash && left.matches(oLeft) && right.matches(oRight)
      case _ =>
        false
    }

  override def forPrinting(concise:Boolean) : String = {
    val leftRep = left match {
      case Functor(_, _, _) if ! concise => "("+left.forPrinting(concise)+")"
      case _ => left.forPrinting(concise)
    }
    val rightRep = right match {
      case Functor(_, _, _) => "("+right.forPrinting(concise)+")"
      case _ => right.forPrinting(concise)
    }
    slash match{
      case Slash.FWD => s"$leftRep/$rightRep"
      case Slash.BCK => s"$leftRep\\$rightRep"
    }
  }

}

object Functor5 {

  def apply(c1: Category, s2:Slash, c2: Category, s3:Slash, c3: Category, s4:Slash, c4: Category, s5:Slash, c5:Category) : Category =
    Functor(s5, Functor(s4, Functor(s3, Functor(s2, c1, c2), c3), c4), c5)

  def unapply(cat: Category): Option[(Category, Slash, Category, Slash, Category, Slash, Category, Slash, Category)] = cat match {
    case Functor(s5, Functor(s4, Functor(s3, Functor(s2, c1, c2), c3), c4), c5) => Some((c1, s2, c2, s3, c3, s4, c4, s5, c5))
    case _ => None
  }

}

object Functor4 {

  def apply(c1: Category, s2:Slash, c2: Category, s3:Slash, c3: Category, s4:Slash, c4: Category) : Category =
    Functor(s4, Functor(s3, Functor(s2, c1, c2), c3), c4)

  def unapply(cat: Category): Option[(Category, Slash, Category, Slash, Category, Slash, Category)] = cat match {
    case Functor(s4, Functor(s3, Functor(s2, c1, c2), c3), c4) => Some((c1, s2, c2, s3, c3, s4, c4))
    case _ => None
  }

}

object Functor3 {

  def apply(c1: Category, s1:Slash, c2: Category, s2:Slash, c3: Category) : Category =
    Functor(s2, Functor(s1, c1, c2), c3)

  def unapply(cat: Category): Option[(Category, Slash, Category, Slash, Category)] = cat match {
    case Functor(s3, Functor(s2, c1, c2), c3) => Some((c1, s2, c2, s3, c3))
    case _ => None
  }

}

object Functor2 {

  def apply(c1: Category, s2:Slash, c2: Category) : Category =
    Functor(s2, c1, c2)

  def unapply(cat: Category): Option[(Category, Slash, Category)] = cat match {
    case Functor(s2, c1, c2) => Some((c1, s2, c2))
    case _ => None
  }

}

