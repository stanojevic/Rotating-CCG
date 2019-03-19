package edin.nn.sequence

import edin.nn.DyFunctions._
import edin.nn.DynetSetup
import edin.nn.layers.{DuoLayer, DuoLayerConfig}
import edu.cmu.dynet._

sealed case class SingleLSTMConfig(
                                  inDim          : Int ,
                                  outDim         : Int ,
                                  dropout        : Float,
                                  withLayerNorm  : Boolean,
                                  withWeightNorm : Boolean
                                  ){
  def construct()(implicit model: ParameterCollection) = new SingleLSTM(this)
}


class SingleLSTM(config:SingleLSTMConfig)(implicit model: ParameterCollection) extends RecurrentNN {

  private var initHidden  : Parameter  = _
  private var outDim      : Int        = _
  private var i_gater     : DuoLayer   = _
  private var f_gater     : DuoLayer   = _
  private var o_gater     : DuoLayer   = _
  private var c_gater     : DuoLayer   = _
  private var dropProb    : Float      = _

  private var inputMask   : Expression = _
  private var hiddenMask  : Expression = _
  private var latestCG_id : Int        = -1

  define()

  protected def define()(implicit model: ParameterCollection) : Unit = {
    val inDim = config.inDim
    outDim = config.outDim
    dropProb = config.dropout

    initHidden = model.addParameters(outDim)

    i_gater = DuoLayerConfig(inDim, outDim, outDim, "logistic", config.withLayerNorm, config.withWeightNorm).construct()
    f_gater = DuoLayerConfig(inDim, outDim, outDim, "logistic", config.withLayerNorm, config.withWeightNorm).construct()
    o_gater = DuoLayerConfig(inDim, outDim, outDim, "logistic", config.withLayerNorm, config.withWeightNorm).construct()
    c_gater = DuoLayerConfig(inDim, outDim, outDim, "tanh"    , config.withLayerNorm, config.withWeightNorm).construct()
  }

  def initState() : StateLSTM = initState(Expression.parameter(initHidden))

  def initState(start_hidden:Expression) : StateLSTM = {
    if(latestCG_id != DynetSetup.cg_id){
      latestCG_id = DynetSetup.cg_id
      inputMask = dropout(ones(config.inDim), dropProb)
      hiddenMask = dropout(ones(config.outDim), dropProb)
    }

    val h = start_hidden
    val c = zeros(outDim)
    new StateLSTM(
      i_gater,
      f_gater,
      o_gater,
      c_gater,
      h,
      c,
      inputMask,
      hiddenMask
    )
  }

  class StateLSTM(
                   i_gater : DuoLayer,
                   f_gater : DuoLayer,
                   o_gater : DuoLayer,
                   c_gater : DuoLayer,
                   val h   : Expression,
                   c       : Expression,
                   i_mask  : Expression,
                   h_mask  : Expression
                 ) extends RecurrentState {

    def addInput(i:Expression) : StateLSTM = {
      val hh      = cmult(h_mask, h)
      val ii      = cmult(i_mask, i)
      val i_gate  = i_gater(ii, hh)
      val f_gate  = f_gater(ii, hh)
      val o_gate  = o_gater(ii, hh)
      val c_prime = c_gater(ii, hh)
      // val c_new = dropout(cmult(c_prime, i_gate), dropProb) + cmult(c, f_gate)
      val c_new = cmult(c_prime, i_gate) + cmult(c, f_gate)
      val h_new = cmult(tanh(c_new), o_gate)
      new StateLSTM(
                    i_gater,
                    f_gater,
                    o_gater,
                    c_gater,
                    h_new,
                    c_new,
                    i_mask,
                    h_mask
      )
    }
  }
}

