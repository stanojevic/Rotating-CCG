package edin.nn.tree

import edin.nn.State

trait EncodableNode extends Serializable {

  // @transient var h:Expression = _
  // @transient var state:AnyRef = _
  @transient var nn:State = _

  val children:List[EncodableNode]

  val isTerm:Boolean    = children.isEmpty
  val isNonTerm:Boolean = children.nonEmpty

}
