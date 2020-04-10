package edin.nn.tree.composers

import edin.nn.{StateClosed, State}
import edin.nn.DyFunctions._
import edin.nn.layers.{Layer, SingleLayer}
import edu.cmu.dynet.{Expression, Parameter, ParameterCollection}

sealed case class SingleTreeLSTMConfig(
                                      inDim          : Int, // dimension of the node?
                                      outDim         : Int, // dimension of the computed representation?
                                      maxArity       : Int,
                                      withLayerNorm  : Boolean,
                                      withWeightNorm : Boolean,
                                      dropout        : Float
                                    ) extends CompositionFunctionConfig {
  def construct()(implicit model: ParameterCollection) = new SingleTreeLSTM(this)
}

class SingleTreeLSTM(config:SingleTreeLSTMConfig)(implicit model: ParameterCollection) extends CompositionFunction {

  if(config.withLayerNorm)
    System.err.println("WARNING 'LAYER NORM' NOT SUPPORTED IN TREE-LSTM")
  if(config.withWeightNorm)
    System.err.println("WARNING 'WEIGHT NORM' NOT SUPPORTED IN TREE-LSTM")

  // this is implementation of "N-ary Tree-LSTM" version of Tree-LSTM from Tai. et al. paper
  type Activation = Expression => Expression

  private var Wi : Parameter = _
  private var Ui : List[Parameter] = _
  private var bi : Parameter = _

  private var Wo : Parameter = _
  private var Uo : List[Parameter] = _
  private var bo : Parameter = _

  private var Wu : Parameter = _
  private var Uu : List[Parameter] = _
  private var bu : Parameter = _

  private var Wf : Parameter = _
  private var Ufs : List[List[Parameter]] = _
  private var bf : Parameter = _

  private var initCompressor : Layer = _

  define()
  private def define(): Unit ={
    Wi = model.addParameters((config.outDim,config.inDim))
    Ui = generateParamsList(config.outDim, config.outDim, config.maxArity)
    bi = model.addParameters(config.outDim)
    Wo = model.addParameters((config.outDim,config.inDim))
    Uo = generateParamsList(config.outDim, config.outDim, config.maxArity)
    bo = model.addParameters(config.outDim)
    Wu = model.addParameters((config.outDim,config.inDim))
    Uu = generateParamsList(config.outDim, config.outDim, config.maxArity)
    bu = model.addParameters(config.outDim)
    Wf = model.addParameters((config.outDim,config.inDim))
    Ufs = (1 to config.maxArity).toList.map{_ => generateParamsList(config.outDim, config.outDim, config.maxArity)}
    bf = model.addParameters(config.outDim)
    initCompressor = SingleLayer.compressor(config.inDim, config.outDim)
  }

  private def generateParamsList(inDim:Int, outDim:Int, count:Int) : List[Parameter] = {
    (1 to count).toList.map{_ => model.addParameters((inDim, outDim))}
  }

  override def compose(childrenStates: List[State], parentRep: Expression): State = {
    assert(childrenStates.size <= config.maxArity)
    val hs = childrenStates.asInstanceOf[List[TreeLSTMState]].map{_.h}
    val cs = childrenStates.asInstanceOf[List[TreeLSTMState]].map{_.c}
    val i = superDuoLayer(sigmoid, Wi, parentRep, Ui, hs, bi)
    val o = superDuoLayer(sigmoid, Wo, parentRep, Uo, hs, bo)
    val fs= Ufs.map{Uf => superDuoLayer(sigmoid, Wf, parentRep, Uf, hs, bf)}
    val u = superDuoLayer(tanh, Wu, parentRep, Uu, hs, bu)
    val c = cmult(i, u) + (fs zip cs).map{case (f, c) => dropout(cmult(f, c), config.dropout)}.esum
    val h = cmult(o, tanh(c))
    TreeLSTMState(h, c)
  }


  override def initState(h: Expression): State = {
    TreeLSTMState(initCompressor(h), zeros(config.outDim))
  }

  private case class TreeLSTMState(h:Expression, c:Expression) extends StateClosed

  private def superDuoLayer(a:Activation, W:Expression, x:Expression, Us:List[Expression], hs:List[Expression], b:Expression) : Expression =
    a(W*x + (Us zip hs).map{y => y._1*y._2}.esum + b)

}
