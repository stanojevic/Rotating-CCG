package edin.nn.layers

import edin.general.YamlConfig
import edu.cmu.dynet.{Expression, ParameterCollection}

sealed case class MLPConfig(
                           activations    : List[String]         , // n   elements
                           sizes          : List[Int]            , // n+1 elements
                           dropouts       : List[Float] = List() , // n   elements
                           withLayerNorm  : Boolean,
                           withWeightNorm : Boolean,
                           ){
  def construct()(implicit model: ParameterCollection) = new MLP(this)
}

object MLPConfig{

  def fromYaml(conf:YamlConfig) : MLPConfig = {
    MLPConfig(
      activations = conf("activations").strList,
      sizes = conf("sizes").intList,
      dropouts = conf.getOptionalListFloat("dropouts"),
      withLayerNorm = conf.getOrElse("with-layer-norm", false),
      withWeightNorm = conf.getOrElse("with-weight-norm", false)
    )
  }

}

class MLP(config:MLPConfig)(implicit model: ParameterCollection) extends Layer{

  private var layers = List[Layer]()

  define()

  override def toString: String = {
    "MLP(" + layers.mkString(", ") + ")"
  }

  //noinspection ZeroIndexToHead
  protected def define()(implicit model: ParameterCollection): Unit = {

    assert(config.activations.size + 1 == config.sizes.size)
    assert(config.dropouts.isEmpty || config.dropouts.size == config.activations.size)

    var activations = config.activations
    var layer_sizes = config.sizes
    var dropouts    = if(config.dropouts.isEmpty) List.fill[Float](activations.size)(0f) else config.dropouts

    while(activations.nonEmpty){
      val inDim      = layer_sizes(0)
      val outDim     = layer_sizes(1)
      val activation = activations(0)
      val dropout    = dropouts(0)
      layers ::= SingleLayerConfig(
                  inDim = inDim,
                  outDim = outDim,
                  activationName = activation,
                  withBias = true,
                  withLayerNorm = config.withLayerNorm,
                  withWeightNorm = config.withWeightNorm,
                  dropout = dropout
                ).construct()
      activations = activations.tail
      layer_sizes = layer_sizes.tail
      dropouts    = dropouts.tail
    }

    layers = layers.reverse

    inDim  = config.sizes.head
    outDim = config.sizes.last
  }

  def apply(x:Expression, targets:List[Long]=List()) : Expression = {
    var curr_out = x
    for(layer <- layers.init){
      curr_out = layer(curr_out)
    }
    curr_out = layers.last(curr_out, targets)
    curr_out
  }

}
