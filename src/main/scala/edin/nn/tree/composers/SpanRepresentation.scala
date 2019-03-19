package edin.nn.tree.composers
import edin.nn.layers.{Layer, SingleLayerConfig}
import edin.nn.DyFunctions._
import edin.nn.{State, StateClosed}
import edu.cmu.dynet.{Expression, ParameterCollection}

case class SpanRepresentationConfig(
                                   inDim             : Int,
                                   outDim            : Int,
                                   dropout           : Float,
                                   withLayerNorm     : Boolean,
                                   withWeightNorm    : Boolean,
                                   ignoreHigherInput : Boolean
                              ) extends CompositionFunctionConfig {

  override def construct()(implicit model: ParameterCollection): CompositionFunction = {
    new SpanRepresentation(this)
  }
}

class SpanRepresentation(conf:SpanRepresentationConfig)(implicit model: ParameterCollection) extends CompositionFunction {

  var compressor : Layer = _

  private val compressorInDim = if(conf.ignoreHigherInput) conf.inDim else 2*conf.inDim

  compressor = SingleLayerConfig(
                 inDim = compressorInDim,
                 outDim = conf.outDim,
                 activationName = "tanh",
                 withBias = true,
                 withLayerNorm = conf.withLayerNorm,
                 withWeightNorm = conf.withWeightNorm,
                 dropout = conf.dropout
               ).construct()

  case class SpanState(subexps:List[Expression], input:Expression=null) extends StateClosed {

    // similar to Cross and Huang
    private lazy val subRep = subexps.last - subexps.head

    override lazy val h: Expression = Option(input) match {
      case Some(x) => combine(subRep, x)
      case None => combine(subRep)
    }

    private def combine(x:Expression):Expression = compressor(x)
    private def combine(x:Expression, y:Expression):Expression = combine(concat(x, y))
  }

  override def compose(childrenStates: List[State], parentRep: Expression): State = {
    val leftEnd = childrenStates.head.asInstanceOf[SpanState].subexps.head
    val rightEnd = childrenStates.last.asInstanceOf[SpanState].subexps.last

    if(conf.ignoreHigherInput) {
      SpanState(subexps = List(leftEnd, rightEnd))
    }else{
      SpanState(subexps = List(leftEnd, rightEnd), parentRep)
    }
  }

  override def initState(h: Expression): State = {
    if(conf.ignoreHigherInput) {
      SpanState(subexps = List(h, h))
    }else{
      SpanState(subexps = List(h, h), input = h)
    }
  }

}

