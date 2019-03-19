package edin.nn.tree.composers

import edin.nn.State
import edu.cmu.dynet.{Expression, ParameterCollection}

trait CompositionFunctionConfig{
  // val outDim:Int
  def construct()(implicit model: ParameterCollection) : CompositionFunction
}

trait CompositionFunction {

  def compose(childA:State, childB:State, parentRep: Expression) : State =
    compose(List(childA, childB), parentRep)

  def compose(childrenStates:List[State], parentRep: Expression) : State

  def initState(h:Expression) : State

}

