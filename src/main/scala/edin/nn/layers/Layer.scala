package edin.nn.layers

import edu.cmu.dynet.Expression

trait Layer {

  var inDim : Int = _
  var outDim : Int = _

  def apply(x:Expression, targets:List[Long]=List()) : Expression

}
