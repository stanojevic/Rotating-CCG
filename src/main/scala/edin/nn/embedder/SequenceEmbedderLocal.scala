package edin.nn.embedder

import edin.general.YamlConfig
import edin.nn.DyFunctions
import edin.nn.DyFunctions._
import edu.cmu.dynet.{Expression, ParameterCollection}

/**
  * This is just a wrapper to the most standard embedding of words by
  * ether lookup table, char-lstm, combination of them, position emb etc.
  * basically anything that doesn't look at the global context but only
  * the local word.
  */

sealed case class SequenceEmbedderLocalConfig[T](
                                                     embConf:EmbedderConfig[T]
                                                   ) extends SequenceEmbedderIncrementalConfig[T] {
  override val outDim: Int = embConf.outDim
  override def construct()(implicit model: ParameterCollection): SequenceEmbedderIncremental[T] = new SequenceEmbedderLocal[T](this)
}

object SequenceEmbedderLocalConfig{

  def fromYaml[K](conf:YamlConfig) : SequenceEmbedderIncrementalConfig[K] =
    SequenceEmbedderLocalConfig[K](
      embConf = EmbedderConfig.fromYaml(conf("embedder-conf"))
    )

}

class SequenceEmbedderLocal[T](config: SequenceEmbedderLocalConfig[T])(implicit model: ParameterCollection) extends SequenceEmbedderIncremental[T] {

  var embedder : Embedder[T] = config.embConf.construct()

  override def zeros: Expression = DyFunctions.zeros(config.embConf.outDim)

  override def initState(): IncrementalEmbedderState[T] = new SequenceEmbedderStandardState[T](embedder)

  override def precomputeEmbeddings(sents: Iterable[List[T]]): Unit = {}

  override def cleanPrecomputedCache(): Unit = {}

}

class SequenceEmbedderStandardState[T](e:Embedder[T]) extends IncrementalEmbedderState[T] {

  override def nextStateAndEmbed(x: T): (IncrementalEmbedderState[T], Expression) =
    (this, e(x))

}

