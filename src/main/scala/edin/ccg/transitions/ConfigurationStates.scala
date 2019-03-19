package edin.ccg.transitions

import edu.cmu.dynet.Expression


object ConfigurationState{

  // def initState(outsideRepr:Expression) : ConfigurationState = BlockedWaitingForWord()(outsideRepr)
  def initState(outsideRepr:Expression) : ConfigurationState = NormalParsing(ShiftIncluded)(outsideRepr)

}

sealed abstract class ConfigurationState(val outsideRepr:Expression){
  def isFinal : Boolean = this match {
    case NormalParsing(ShiftExcluded) => true
    case _ => false
  }
  def isBlocked : Boolean = this match {
    case BlockedWaitingForWord() => true
    case _ => false
  }
}

sealed trait Shifting
case object ShiftIncluded extends Shifting
case object ShiftExcluded extends Shifting

case class  NormalParsing(shifting:Shifting)(outsideRepr:Expression) extends ConfigurationState(outsideRepr){
  def toRightAdjunction : ConfigurationState = RightAdjunction()(shifting, outsideRepr)
  def toBlocked : ConfigurationState = shifting match {
    case ShiftIncluded => BlockedWaitingForWord()(outsideRepr)
    case ShiftExcluded => throw new Exception("can't do shifting now")
  }
  override def equals(obj: scala.Any): Boolean = obj.isInstanceOf[NormalParsing] && obj.asInstanceOf[NormalParsing].shifting == shifting
}

case class BlockedWaitingForWord()(outsideRepr:Expression) extends ConfigurationState(outsideRepr){
  def toTagging(outsideRepr:Expression, word:String, isFinal:Boolean) : ConfigurationState = Tagging(word)(outsideRepr, isFinal)
  override def equals(obj: scala.Any): Boolean = obj.isInstanceOf[BlockedWaitingForWord]
}

case class Tagging(word:String)(outsideRepr:Expression, isFinal:Boolean) extends ConfigurationState(outsideRepr){

  def toNormalParsing : ConfigurationState =
    if(isFinal)
      NormalParsing(ShiftExcluded)(outsideRepr)
    else
      NormalParsing(ShiftIncluded)(outsideRepr)

  override def equals(obj: scala.Any): Boolean = obj.isInstanceOf[Tagging] && obj.asInstanceOf[Tagging].word == word
}

case class RightAdjunction()(val shifting:Shifting, outsideRepr:Expression) extends ConfigurationState(outsideRepr){
  def toNormalParsing : ConfigurationState = NormalParsing(shifting)(outsideRepr)
  override def equals(obj: scala.Any): Boolean = obj.isInstanceOf[RightAdjunction] && obj.asInstanceOf[RightAdjunction].shifting == shifting
}

