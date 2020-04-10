package edin.ccg.transitions

import edin.ccg.representation.category.Category
import edu.cmu.dynet.Expression

object ConfigurationState{

  val TAG_FIRST: Boolean = false

  def initState(outsideRepr:Expression) : ConfigurationState =
    if(TAG_FIRST) Tagging()(outsideRepr)
    else BlockedWaitingForWordWF()(outsideRepr)

}

sealed abstract class ConfigurationState(val outsideRepr:Expression){
  def isParsing : Boolean = !isBlocked && !isTagging
  def isBlocked : Boolean = this match {
    case BlockedWaitingForWord(_)  => true
    case BlockedWaitingForWordWF() => true
    case _                         => false
  }
  def isTagging : Boolean = this match {
    case Tagging()    => true
    case TaggingWF(_) => true
    case _            => false
  }
}

case class RightAdjunction()(outsideRepr:Expression) extends ConfigurationState(outsideRepr){

  def toNormalParsing : ConfigurationState = NormalParsing()(outsideRepr)

  override def equals(obj: scala.Any): Boolean = obj match {
    case RightAdjunction() => true
    case _                 => false
  }
}

case class NormalParsing()(outsideRepr:Expression) extends ConfigurationState(outsideRepr){

  def toRightAdjunction       : ConfigurationState = RightAdjunction()(outsideRepr)
  def toTagging               : ConfigurationState = {
    assert(ConfigurationState.TAG_FIRST)
    Tagging()(outsideRepr)
  }
  def toBlockedWaitingForWord : ConfigurationState = {
    assert(!ConfigurationState.TAG_FIRST)
    BlockedWaitingForWordWF()(outsideRepr)
  }

  override def equals(obj: scala.Any): Boolean = obj match {
    case NormalParsing() => true
    case _               => false
  }
}

case class BlockedWaitingForWord(tag:Category)(outsideRepr:Expression) extends ConfigurationState(outsideRepr){

  assert(ConfigurationState.TAG_FIRST)

  def toNormalParsing(outsideRepr:Expression) : ConfigurationState = NormalParsing()(outsideRepr)

  override def equals(obj: scala.Any): Boolean = obj match {
    case BlockedWaitingForWord(`tag`) => true
    case _                            => false
  }
}

case class Tagging()(outsideRepr:Expression) extends ConfigurationState(outsideRepr){

  assert(ConfigurationState.TAG_FIRST)

  def toBlockedWaitingForWord(tag:Category) : ConfigurationState = BlockedWaitingForWord(tag)(outsideRepr)

  override def equals(obj: scala.Any): Boolean = obj match {
    case Tagging() => true
    case _         => false
  }

}

case class BlockedWaitingForWordWF()(outsideRepr:Expression) extends ConfigurationState(outsideRepr){

  assert(!ConfigurationState.TAG_FIRST)

  def toTagging(word:String, outsideRepr:Expression) : ConfigurationState = TaggingWF(word)(outsideRepr)

  override def equals(obj: scala.Any): Boolean = obj match {
    case BlockedWaitingForWordWF() => true
    case _                         => false
  }
}

case class TaggingWF(word:String)(outsideRepr:Expression) extends ConfigurationState(outsideRepr){

  assert(!ConfigurationState.TAG_FIRST)

  def toNormalParsing : ConfigurationState = NormalParsing()(outsideRepr)

  override def equals(obj: Any): Boolean = obj match {
    case TaggingWF(`word`) => true
    case _                 => false
  }

}

