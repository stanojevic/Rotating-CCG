package edin.nn.embedder

import edin.general.YamlConfig
import edin.nn.layers.{Layer, SingleLayer}
import edin.nn.DyFunctions._
import edu.cmu.dynet.{Expression, ParameterCollection}


case class EmbedderCombinerConfig[T](
                                    name               : String,
                                    outDim             : Int,
                                    subEmbedderConfigs : List[EmbedderConfig[T]],
                                    dropProb           : Float
                                    ) extends EmbedderConfig[T] {
  override def construct()(implicit model: ParameterCollection): Embedder[T] = new EmbedderCombiner[T](this)
}

object EmbedderCombinerConfig{

  def fromYaml[K](conf:YamlConfig) : EmbedderConfig[K] = {
    EmbedderCombinerConfig[K](
      name = conf.getOrElse("unique-name", "whatever"),
      outDim = conf("out-dim").int,
      dropProb = conf.getOrElse("dropout", 0f),
      subEmbedderConfigs = conf("subembs").list.map{x => EmbedderConfig.fromYaml[K](x)}
    )
  }

}

class EmbedderCombiner[T](conf:EmbedderCombinerConfig[T])(implicit model: ParameterCollection) extends Embedder[T] {

  var compressor   : Layer = _
  var subEmbedders : List[Embedder[T]] = List()
  var dropProb     : Float = _

  override val outDim: Int = conf.outDim

  define()

  private def define():Unit = {
    compressor = SingleLayer.compressor(conf.subEmbedderConfigs.map{_.outDim}.sum, conf.outDim)
    dropProb = conf.dropProb
    subEmbedders = conf.subEmbedderConfigs.map{_.construct()}
  }

  override def apply(x: T): Expression = {
    dropout(compressor(concatSeq(subEmbedders.map{_(x)})), dropProb)
  }

}
