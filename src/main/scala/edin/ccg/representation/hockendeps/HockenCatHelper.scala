package edin.ccg.representation.hockendeps

import edin.ccg.representation.hockendeps.{CCGcat => HockenCat}
import edin.ccg.representation.category.{Category, Functor}
import edin.ccg.representation.combinators._
import edin.ccg.representation.predarg.{Bound, DepLink, Local, UnBound}
import edin.ccg.representation.tree._

import scala.util.Try

object HockenCatHelper {

  def lexicalEntry(word:String, cat:Category, position:Int) : Option[HockenCat] =
    Some(HockenCat.lexCat(word, cat.toString, position))

  def unaryCombine(comb:CombinatorUnary, child:TreeNode) : Option[HockenCat] = {
    val parentCatHockenStr = comb(child.category).toString
    child.hockenState.hockenCat match {
      case Some(cHockCat) =>
        if(comb.isInstanceOf[TypeRaiser])
          Option(Try(HockenCat.typeRaiseTo(cHockCat, parentCatHockenStr)).getOrElse(null))
        else
          Option(Try(HockenCat.typeChangingRule(cHockCat, parentCatHockenStr)).getOrElse(null))
      case _ =>
        throw new Exception("problem?")
    }
  }

  def binaryCombine(comb:CombinatorBinary, leftNode:TreeNode, rightNode:TreeNode) : Option[HockenCat] = {
    val hockenStr:String = comb match {
      case RemovePunctuation(true) =>
        rightNode.hockenState.hockenCat.get.catString
      case RemovePunctuation(false) =>
        leftNode.hockenState.hockenCat.get.catString
      case _ =>
        comb(leftNode.category, rightNode.category).toString
    }
    val hockenCat = Try(HockenCat.combine(leftNode.hockenState.hockenCat.get, rightNode.hockenState.hockenCat.get, hockenStr)).getOrElse(null)
    Option(hockenCat)
  }

  def binaryCombineWithAdjunctionBackoff(comb:CombinatorBinary, leftNode:TreeNode, rightNode:TreeNode) : Option[HockenCat] =
    binaryCombine(comb, leftNode, rightNode) match {
      case Some(x) =>
        Some(x)
      case None =>
        if(rightNode.category.isModifier)
          leftNode.hockenState.hockenCat
        else
          rightNode.hockenState.hockenCat
    }

  // def dependencies(hockenCatOpt: Option[HockenCat]) : List[HockenDepLink] = hockenCatOpt match {
  val dependencies : Option[HockenCat] => List[DepLink] = {
    case Some(x) => dependencies(x)
    case None    => Nil
  }

  def dependencies(hockenCat:HockenCat) : List[DepLink] = {
    if(hockenCat == null)
      throw new Exception("something's wrong")

    var currDep = hockenCat.filledDependencies

    var deps = List[DepLink]()
    while(currDep != null){
      deps ::=  DepLink(
        headCat   = Category(currDep.headCat),
        headPos   = currDep.headIndex,
        depPos    = currDep.argIndex ,
        depSlot   = currDep.argPos   ,
        headWord  = currDep.headWord ,
        depWord   = currDep.argWord  ,
        boundness = if(currDep.extracted && currDep.bounded) Bound else if (! currDep.bounded) UnBound else Local)
      currDep = currDep.next
    }
    deps
  }

}
