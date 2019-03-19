package edin.nn.layers

import edin.general.YamlConfig
import edin.nn.DyFunctions._
import edin.nn.DynetSetup
import edu.cmu.dynet._

sealed case class SingleLayerConfig(
                               inDim:Int,
                               outDim:Int,
                               activationName:String,
                               withBias:Boolean,
                               withLayerNorm:Boolean,
                               withWeightNorm:Boolean,
                               dropout:Float
                             ){
  def construct()(implicit model: ParameterCollection) = new SingleLayer(this)
}

object SingleLayerConfig{

  def fromYaml(conf:YamlConfig) : SingleLayerConfig = {
    SingleLayerConfig(
      inDim          = conf("in-dim"         ).int,
      outDim         = conf("out-dim"        ).int,
      activationName = conf("activation"     ).str,
      withBias       = conf("with-bias"      ).bool,
      withLayerNorm  = conf.getOrElse("with-layer-norm" , false),
      withWeightNorm = conf.getOrElse("with-weight-norm", false),
      dropout        = conf.getOrElse("dropout", 0f)
    )
  }

}

class SingleLayer(config:SingleLayerConfig)(implicit model:ParameterCollection) extends Layer {


  assert(!config.withLayerNorm || config.withBias)

  private val activation = activationFactory(config.activationName)
  inDim = config.inDim
  outDim = config.outDim

  private val param_W : Parameter = model.addParameters((outDim, inDim))

  private val param_b      : Option[Parameter] = if(config.withBias)       Some(model.addParameters(outDim)) else None
  private val g_weightNorm : Option[Parameter] = if(config.withWeightNorm) Some(model.addParameters(     1)) else None
  private val g_layerNorm  : Option[Parameter] = if(config.withLayerNorm)  Some(model.addParameters(outDim)) else None

  System.err.println("creating "+this)

  override def toString: String = s"Layer(${inDim.toString}, ${outDim.toString})"

  private def myLayerNorm(x:Expression, g:Expression, b:Expression) : Expression = {
    val mean = Expression.meanElems(x)
    val std  = Expression.stdElems(x)+1e-10
    val renormalizedInput = (x-mean)/std
    cmult(g, renormalizedInput) + b
  }

  private def activate(WW:Expression, input:Expression, bb:Option[Expression], gg_layerNorm:Option[Expression]) : Expression = (bb, gg_layerNorm) match {
    case (Some(b), Some(gg)) =>
      dropout(activation(myLayerNorm(WW*input, gg, b)), config.dropout)
    case (Some(b), None) =>
      dropout(activation(WW*input+b), config.dropout)
    case (None, Some(_)) =>
      throw new Exception("this should not happen")
    case (None, None) =>
      dropout(activation(WW*input), config.dropout)
  }

  private def subSelectForTarget(targets:List[Long], W:Expression, b:Option[Expression], g_layerNorm:Option[Expression]) : (Expression, Option[Expression], Option[Expression]) =
    targets match {
      case Nil =>
        (W, b, g_layerNorm)
      case subselect =>
        (
          Expression.selectRows(W, subselect),
          b.map(Expression.selectRows(_, subselect)),
          g_layerNorm.map(Expression.selectRows(_, subselect))
        )
    }

  private var latestCG_id : Int = -1
  private var activeW : Expression = _

  private def refreshWeights() : Unit =
    if(latestCG_id != DynetSetup.cg_id){
      latestCG_id = DynetSetup.cg_id
      activeW = if(config.withWeightNorm) Expression.weightNorm( param2expr(param_W), g_weightNorm.get ) else param2expr(param_W)
    }

  private def W_exp : Expression = {
    refreshWeights()
    activeW
  }

  def apply(input:Expression, targets:List[Long]=List()): Expression = {
    val (ww, bb, gg_layerNorm) = subSelectForTarget(targets, W_exp, paramOpt2exprOpt(param_b), paramOpt2exprOpt(g_layerNorm))
    activate(ww, input, bb, gg_layerNorm)
  }

}

object SingleLayer{

  def compressor(inDim: Int, outDim: Int)(implicit model:ParameterCollection) : Layer =
    SingleLayerConfig(
      inDim = inDim,
      outDim = outDim,
      activationName = "nothing",
      withBias = false,
      withLayerNorm = false,
      withWeightNorm = false,
      dropout = 0f
    ).construct()

}

