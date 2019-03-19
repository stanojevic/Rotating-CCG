package edin.nn.layers

import edin.general.YamlConfig
import edu.cmu.dynet.{Expression, ParameterCollection}
import edin.nn.DyFunctions._

sealed case class CombinerConfig(
                                  inDims           : List[Int],
                                  midDim           : Int,
                                  outDim           : Int,
                                  combType         : String,
                                  activationName   : String,
                                  dropout          : Float,
                                  withPostencoding : Boolean,
                                  withLayerNorm    : Boolean,
                                  withWeightNorm    : Boolean
                                ){
  def construct()(implicit model: ParameterCollection) = new Combiner(this)
}

object CombinerConfig{

  def fromYaml(conf:YamlConfig) : CombinerConfig = {
    CombinerConfig(
      inDims           = conf("in-dims"        ).intList,
      midDim           = conf("mid-dim"        ).int,
      outDim           = conf("out-dim"        ).int,
      combType         = conf("comb-type"      ).str,
      activationName   = conf("activation"     ).str,
      dropout          = conf.getOrElse("dropout", 0f),
      withPostencoding = conf("with-postencoding").bool,
      withLayerNorm    = conf.getOrElse("with-layer-norm" , false),
      withWeightNorm   = conf.getOrElse("with-weight-norm", false)
    )
  }

}

class Combiner(config:CombinerConfig)(implicit model: ParameterCollection) {

  val outDim : Int = config.outDim

  val finalCompressor : Layer = if(config.withPostencoding){
    SingleLayerConfig(
      inDim = config.midDim,
      outDim = config.outDim,
      activationName = config.activationName,
      withBias = true,
      withLayerNorm = config.withLayerNorm,
      withWeightNorm = config.withWeightNorm,
      dropout = config.dropout
    ).construct()
  }else{
    assert(config.midDim == config.outDim)
    new IdentityLayer(config.outDim)
  }

  val compressors : List[Layer] = config.combType match {
    case "concat" =>
      assert(config.inDims.sum == config.midDim)
      Nil
    case "sum" =>
      config.inDims.filter(_>0).map( x =>
        SingleLayerConfig(
          inDim = x,
          outDim = config.midDim,
          activationName = config.activationName,
          withBias = true,
          withLayerNorm = config.withLayerNorm,
          withWeightNorm = config.withWeightNorm,
          dropout = config.dropout
        ).construct()
      )
  }

  def apply(exps: Expression*): Expression = {
    assert(exps.size == config.inDims.size)
//    if(config.midDim==256){
//      println(exps)
//      println()
//    }
    val preencoding = config.combType match {
      case "concat" =>
        concatSeqWithNull(exps)
      case "sum" =>
        esum((exps.filter(_!=null) zip compressors).map{case (e, c) => c(e)})
    }
    finalCompressor(preencoding)
  }

}

