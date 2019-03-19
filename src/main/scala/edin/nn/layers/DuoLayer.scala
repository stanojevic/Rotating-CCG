package edin.nn.layers

import edin.nn.DyFunctions._
import edin.nn.DynetSetup
import edu.cmu.dynet._

sealed case class DuoLayerConfig(
                                inDimA         : Int,
                                inDimB         : Int,
                                outDim         : Int,
                                activationName : String,
                                withLayerNorm  : Boolean,
                                withWeightNorm : Boolean
                                ){
  def construct()(implicit model: ParameterCollection) = new DuoLayer(this)
}

class DuoLayer(config:DuoLayerConfig)(implicit model:ParameterCollection) {

  if(config.withLayerNorm)
    System.err.println("WARNING 'LAYER NORM' NOT SUPPORTED IN DuoLayer!")

  private var activation : Expression => Expression = _

  private var param_WA : Parameter = _
  private var param_WB : Parameter = _

  private var param_b : Parameter = _

  define()

  private val gA : Parameter = model.addParameters(1)
  private val gB : Parameter = model.addParameters(1)
  private var latestCG_id : Int = -1
  private var activeWA : Expression = _
  private var activeWB : Expression = _

  System.err.println("creating "+this)

  override def toString: String = s"DuoLayer(${config.inDimA}, ${config.inDimB}, ${config.outDim})"

  private def refreshWeights() : Unit =
    if(latestCG_id != DynetSetup.cg_id){
      latestCG_id = DynetSetup.cg_id
      activeWA = if(config.withLayerNorm) Expression.weightNorm( param2expr(param_WA), gA ) else param2expr(param_WA)
      activeWB = if(config.withLayerNorm) Expression.weightNorm( param2expr(param_WB), gB ) else param2expr(param_WB)
    }

  private def WA_exp : Expression = {
    refreshWeights()
    activeWA
  }

  private def WB_exp : Expression = {
    refreshWeights()
    activeWB
  }

  def apply(inputA:Expression, inputB:Expression): Expression =
    activation(WA_exp*inputA+WB_exp*inputB+param_b)

  protected def define()(implicit model:ParameterCollection) : Unit = {

    activation = activationFactory(config.activationName)

    val inDimA = config.inDimA
    val inDimB = config.inDimB
    val outDim = config.outDim
    param_WA = model.addParameters((outDim, inDimA))
    param_WB = model.addParameters((outDim, inDimB))
    param_b = model.addParameters(outDim)
  }

}

