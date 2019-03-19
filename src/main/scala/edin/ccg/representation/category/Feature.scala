package edin.ccg.representation.category

sealed abstract class Feature(str:String) extends Serializable{

  def matches(other:Feature) : Boolean

  override def toString: String = str match{
    case "" => ""
    case _ => s"[$str]"
  }

}

/**
  * AgreeableFeatureEmpty
  * uninstantiated feature variable that can be unified in the future
  */
case class FeatureEmpty()(strRep:String="") extends Feature(str = strRep) {

  override def matches(other: Feature): Boolean = true

  override def equals(other: scala.Any): Boolean = other.isInstanceOf[FeatureEmpty]

  override def hashCode(): Int = 0

}

/**
  * AgreeableFeature
  * normal instantiated feature that can unify with uninstantiated non-term
  */
case class FeatureNonEmpty(f:String) extends Feature(f) {

  override def matches(other: Feature): Boolean =
    other match {
      case FeatureNonEmpty(`f`) => true
      case FeatureEmpty() => true
      case _ => false
    }

  override def equals(other: scala.Any): Boolean =
    other match {
      case FeatureNonEmpty(`f`) => true
      case _ => false
    }

  override def hashCode(): Int = f.##

}

