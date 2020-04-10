package edin.ccg.representation.predarg

import edin.ccg.representation.category.Category

sealed trait Boundness{
  def isBound: Boolean = this == Bound
  def isUnbound: Boolean = this == UnBound
  def isLocal: Boolean = this == Local
  override def toString: String = this match {
    case Bound => ":B"
    case UnBound => ":U"
    case Local => ""
  }
}
case object Bound   extends Boundness
case object UnBound extends Boundness
case object Local   extends Boundness

sealed case class DepLink(
                           headCat    : Category,
                           headPos    : Int,
                           depPos     : Int,
                           depSlot    : Int,
                           headWord   : String,
                           depWord    : String,
                           boundness  : Boundness
                         ){

  def arcLabel:String = s"$depSlot$boundness"

  def toUnlabelled : (Int, Int) = (headPos, depPos)

  def isBound : Boolean = boundness == Bound

  def isUnbound : Boolean = boundness == UnBound

  def isLongDistance : Boolean = isBound || isUnbound

  def toUnlabelledAndUndirected : (Int, Int) =
    (math.min(headPos, depPos), math.max(headPos, depPos))

}
