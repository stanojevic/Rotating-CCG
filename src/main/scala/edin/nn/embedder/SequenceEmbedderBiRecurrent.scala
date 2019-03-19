package edin.nn.embedder

import edin.general.YamlConfig
import edin.nn.DyFunctions
import edin.nn.DyFunctions._
import edin.nn.sequence._
import edu.cmu.dynet.{Expression, ParameterCollection}

sealed case class SequenceEmbedderBiRecurrentConfig[T](
                                                        subEmbConf:SequenceEmbedderGeneralConfig[T],
                                                        biRnnConfig: SequenceEncoderConfig
                                                      ) extends SequenceEmbedderGeneralConfig[T] {
  override def construct()(implicit model: ParameterCollection): SequenceEmbedderGeneral[T] = new SequenceEmbedderBiRecurrent[T](this)
}

object SequenceEmbedderBiRecurrentConfig{

  def fromYaml[K](conf:YamlConfig) : SequenceEmbedderGeneralConfig[K] = {
    if(conf("recurrent-conf")("layers").int == 0){
      SequenceEmbedderGeneralConfig.fromYaml[K](conf("sub-embedder-conf"))
    }else{
      SequenceEmbedderBiRecurrentConfig(
        subEmbConf = SequenceEmbedderGeneralConfig.fromYaml[K](conf("sub-embedder-conf")),
        biRnnConfig = SequenceEncoderConfig.fromYaml(conf("recurrent-conf"))
      )
    }
  }

}

class SequenceEmbedderBiRecurrent[T](config: SequenceEmbedderBiRecurrentConfig[T])(implicit model: ParameterCollection) extends SequenceEmbedderGeneral[T] {

  var subEmbedder : SequenceEmbedderGeneral[T] = config.subEmbConf.construct()
  var biRnn : SequenceEncoder = config.biRnnConfig.construct()

  override def zeros : Expression = DyFunctions.zeros(config.biRnnConfig.outDim)

  override def transduce(xs: List[T]): List[Expression] = {
    biRnn.transduce(subEmbedder.transduce(xs))
  }

  override def precomputeEmbeddings(sents: Iterable[List[T]]): Unit = subEmbedder.precomputeEmbeddings(sents)

  override def cleanPrecomputedCache(): Unit = subEmbedder.cleanPrecomputedCache()
}

