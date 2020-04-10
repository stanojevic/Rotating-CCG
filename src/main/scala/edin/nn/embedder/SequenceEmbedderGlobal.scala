package edin.nn.embedder

import edin.general.YamlConfig
import edin.nn.DyFunctions
import edin.nn.DyFunctions._
import edin.nn.sequence._
import edu.cmu.dynet.{Expression, ParameterCollection}

/**
  * This is for an embedder that embeds each word by taking a more global context into account
  * It can be unidirectional lstm, bidirectional lstm, transformer (not implemented yet) etc.
  */

sealed case class SequenceEmbedderGlobalConfig[T](
                                                   subEmbConf : SequenceEmbedderGeneralConfig[T],
                                                   rnnConfig  : SequenceEncoderConfig
                                                 ) extends SequenceEmbedderGeneralConfig[T] {
  override val outDim: Int = rnnConfig.outDim
  override def construct()(implicit model: ParameterCollection): SequenceEmbedderGeneral[T] = new SequenceEmbedderGlobal[T](this)
}

object SequenceEmbedderGlobalConfig{

  def fromYaml[K](conf:YamlConfig) : SequenceEmbedderGeneralConfig[K] =
    if(conf("recurrent-conf")("layers").int == 0){
      // skip this embedder and expose only sub-embedder
      SequenceEmbedderGeneralConfig.fromYaml[K](
        conf("sub-embedder-conf"))
    }else{
      val rnnYaml = conf("recurrent-conf")
      val rnnConfig = if(rnnYaml("bi-directional").bool){
        BiRNNConfig.fromYaml(rnnYaml)
      }else{
        MultiRNNConfig.fromYaml(rnnYaml)
      }
      SequenceEmbedderGlobalConfig(
        subEmbConf  = SequenceEmbedderGeneralConfig.fromYaml[K](conf("sub-embedder-conf")),
        rnnConfig = rnnConfig )
    }

}

class SequenceEmbedderGlobal[T](config: SequenceEmbedderGlobalConfig[T])(implicit model: ParameterCollection) extends SequenceEmbedderGeneral[T] {

  var subEmbedder : SequenceEmbedderGeneral[T] = config.subEmbConf.construct()
  var rnn : SequenceEncoder = config.rnnConfig.construct()

  override def zeros : Expression = DyFunctions.zeros(config.rnnConfig.outDim)

  override def transduce(xs: List[T]): List[Expression] = {
    rnn.transduce(subEmbedder.transduce(xs))
  }

  override def precomputeEmbeddings(sents: Iterable[List[T]]): Unit = subEmbedder.precomputeEmbeddings(sents)

  override def cleanPrecomputedCache(): Unit = subEmbedder.cleanPrecomputedCache()
}

