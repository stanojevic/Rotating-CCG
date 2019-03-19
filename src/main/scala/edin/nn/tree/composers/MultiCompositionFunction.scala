package edin.nn.tree.composers

import edin.general.YamlConfig
import edin.nn.{StateClosed, State}
import edu.cmu.dynet.{Expression, ParameterCollection}

sealed case class MultiCompositionFunctionConfig(
                                                  compositionType   : String,
                                                  inDim             : Int,
                                                  outDim            : Int,
                                                  layers            : Int,
                                                  maxArity          : Int,
                                                  ignoreHigherInput : Boolean,
                                                  withLayerNorm     : Boolean,
                                                  withWeightNorm    : Boolean,
                                                  dropout           : Float
                                                ) extends CompositionFunctionConfig {
  def construct()(implicit model: ParameterCollection) = new MultiCompositionFunction(this)
}

object MultiCompositionFunctionConfig{

  def fromYaml(conf:YamlConfig) : CompositionFunctionConfig = {
    MultiCompositionFunctionConfig(
      compositionType   =       conf("compositionType"                   ).str,
      inDim             =       conf("in-dim"                            ).int,
      outDim            =       conf("out-dim"                           ).int,
      layers            =       conf("layers"                            ).int,
      maxArity          =       conf("max-arity"                         ).int,
      ignoreHigherInput =       conf.getOrElse("ignore-higher-input", false),
      withLayerNorm     =       conf.getOrElse("with-layer-norm"    , false),
      withWeightNorm    =       conf.getOrElse("with-weight-norm"   , false),
      dropout           =       conf.getOrElse("dropout"            , 0f   )
    )
  }

}

sealed case class StateMultiCompositionFunction(states:List[State]) extends StateClosed {
  override val h: Expression = states.last.h
}

class MultiCompositionFunction(c:MultiCompositionFunctionConfig)(implicit model: ParameterCollection) extends CompositionFunction {

  private var composers: List[CompositionFunction] = List()

  define()

  protected def define()(implicit model: ParameterCollection): Unit = {

    composers ::= singleComposerConfigFactory(  c.compositionType, c.inDim , c.outDim, c.maxArity, c.ignoreHigherInput, c.dropout, c.withLayerNorm, c.withWeightNorm).construct()
    for(_ <- 2 to c.layers){
      composers ::= singleComposerConfigFactory(c.compositionType, c.outDim, c.outDim, c.maxArity, c.ignoreHigherInput, c.dropout, c.withLayerNorm, c.withWeightNorm).construct()
    }
    composers = composers.reverse
  }

  override def compose(aChildrenStates: List[State], parentRep: Expression): State = {
    val childrenStates = aChildrenStates.asInstanceOf[List[StateMultiCompositionFunction]]
    var states = List[State]()

    val currChildrenStates = childrenStates.map{_.states.head}
    states ::= composers.head.compose(currChildrenStates, parentRep)
    for(i <- 1 until composers.size){
      val composer = composers(i)
      val currChildrenStates = childrenStates.map{_.states(i)}
      states ::= composer.compose(currChildrenStates, states.head.h)
    }
    states = states.reverse

    StateMultiCompositionFunction(states)
  }

  override def initState(h: Expression): State = {
    var states = List[State]()
    states ::= composers.head.initState(h)
    for(composer <- composers.tail){
      states ::= composer.initState(states.head.h)
    }
    states = states.reverse
    StateMultiCompositionFunction(states)
  }


  def singleComposerConfigFactory(
                                   composerName:String,
                                   inDim:Int,
                                   outDim:Int,
                                   maxArity:Int,
                                   ignoreHigherInput: Boolean,
                                   dropProb:Float,
                                   withLayerNorm:Boolean,
                                   withWeightNorm:Boolean
                                 )(implicit model: ParameterCollection) : CompositionFunctionConfig = {
    composerName match {
      case "HierNN" => SingleHierNNConfig(
        inDim          = inDim,
        outDim         = outDim,
        seqType        = "LSTM",
        withLayerNorm  = withLayerNorm,
        withWeightNorm = withWeightNorm,
        seqDropout     = dropProb
      )
      case "TreeLSTM" => SingleTreeLSTMConfig(
        inDim          = inDim, // dimension of the node?
        outDim         = outDim, // dimension of the computed representation?
        maxArity       = maxArity,
        withLayerNorm  = withLayerNorm,
        withWeightNorm = withWeightNorm,
        dropout        = dropProb
      )
      case "SpanRep" => SpanRepresentationConfig(
        inDim             = inDim,
        outDim            = outDim,
        withLayerNorm     = withLayerNorm,
        withWeightNorm    = withWeightNorm,
        dropout           = dropProb,
        ignoreHigherInput = ignoreHigherInput
      )
    }
  }

}
