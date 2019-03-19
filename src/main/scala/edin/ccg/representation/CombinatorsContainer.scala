package edin.ccg.representation

import java.io.{FileInputStream, FileOutputStream, ObjectInputStream, ObjectOutputStream}

import edin.ccg.representation.category.Category
import edin.ccg.representation.combinators._

import scala.collection.mutable.{Map => MutMap, Set => MutSet}

class CombinatorsContainer extends Serializable {

  private val unholyUnaryStorage  = MutSet[CombinatorUnary]()
  private val unholyBinaryStorage = MutSet[TypeChangeBinary]()

  private val unholyBinaryRulesLookup = MutMap[(Category, Category), Set[CombinatorBinary]]().withDefaultValue(Set())
  private val unholyUnaryRulesLookup  = MutMap[Category, Set[CombinatorUnary]]().withDefaultValue(Set())

  private val unariesStandard = CombinatorUnary.allPredefined.toSet

  def allUnholyBinary:Iterator[CombinatorBinary] = unholyBinaryStorage.iterator
  def allUnary:Iterator[CombinatorUnary] = {
    unholyUnaryStorage.iterator ++ unariesStandard.iterator
  }

  def unaryLookup(from:Category) : Set[CombinatorUnary] = {
    unholyUnaryRulesLookup(from) | unariesStandard.filter(_.canApply(from))
  }

  def forwardLookup(l:Category, r:Category) : Option[CombinatorBinary] = {
    CombinatorBinary.allForward.find(_.canApply(l, r))
  }

  def backwardAndCrossedNonRightAdjunctionLookup(l:Category, r:Category) : Set[CombinatorBinary] = {
    backwardAndCrossedLookup(l, r).filterNot(_.isRightAdjCombinator(l, r))
  }

  def backwardAndCrossedRightAdjunctionLookup(l:Category, r:Category) : Set[CombinatorBinary] = {
    backwardAndCrossedLookup(l, r).filter(_.isRightAdjCombinator(l, r))
  }

  def backwardAndCrossedLookup(l:Category, r:Category) : Set[CombinatorBinary] = {
    CombinatorBinary.allBackwardAndCrossed.filter(_.canApply(l, r))
  }

  def puncLookup(l:Category, r:Category) : Set[CombinatorBinary] = {
    CombinatorBinary.allPunc.filter(_.canApply(l, r))
  }

  def conjLookup(l:Category, r:Category) : Set[CombinatorBinary] = {
    Set[CombinatorBinary](CombinatorBinary.conj).filter(_.canApply(l, r))
  }

  def unholyLookup(l:Category, r:Category) : Set[CombinatorBinary] = {
    unholyBinaryRulesLookup((l, r))
  }

  def isPredefined(x:CombinatorUnary) : Boolean = unariesStandard contains x

  def add(x:CombinatorUnary): Unit =
    if(!isPredefined(x)){
      unholyUnaryStorage.add(x)
      unholyUnaryRulesLookup(x.asInstanceOf[TypeChangeUnary].from) += x
    }

  def add(x:CombinatorBinary) : Unit = {
    x match {
      case c@TypeChangeBinary(l, r, p) => {
        unholyBinaryRulesLookup((l, r)) += c //:: specialRulesLookup((l, r))
        unholyBinaryStorage.add(c)
      }
      case _ =>
    }
  }

  def save(fn:String) : Unit = {
    val fh = new ObjectOutputStream(new FileOutputStream(fn))
    fh.writeObject(this)
    fh.close()
  }

}

object CombinatorsContainer{

  def load(fn:String) : CombinatorsContainer = {
    val fh = new ObjectInputStream(new FileInputStream(fn))
    val cc = fh.readObject().asInstanceOf[CombinatorsContainer]
    fh.close()
    cc
  }

}

