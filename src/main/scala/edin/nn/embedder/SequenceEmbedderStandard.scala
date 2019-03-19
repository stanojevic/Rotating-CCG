package edin.nn.embedder

import edin.general.YamlConfig
import edin.nn.DyFunctions
import edin.nn.DyFunctions._
import edu.cmu.dynet.{Expression, ParameterCollection}

sealed case class SequenceEmbedderStandardConfig[T](
                                                     embConf:EmbedderConfig[T]
                                                   ) extends SequenceEmbedderIncrementalConfig[T] {
  override def construct()(implicit model: ParameterCollection): SequenceEmbedderIncremental[T] = new SequenceEmbedderStandard[T](this)
}

object SequenceEmbedderStandardConfig{

  def fromYaml[K](conf:YamlConfig) : SequenceEmbedderIncrementalConfig[K] = {
    SequenceEmbedderStandardConfig[K](
      embConf = EmbedderConfig.fromYaml(conf("embedder-conf"))
    )
  }

}

class SequenceEmbedderStandard[T](config: SequenceEmbedderStandardConfig[T])(implicit model: ParameterCollection) extends SequenceEmbedderIncremental[T] {

  var embedder : Embedder[T] = _

  define()
  private def define():Unit ={
    embedder = config.embConf.construct()
  }

  override def zeros: Expression = DyFunctions.zeros(config.embConf.outDim)

  override def initState(): IncrementalEmbedderState[T] = new SequenceEmbedderStandardState[T](embedder)

  override def precomputeEmbeddings(sents: Iterable[List[T]]): Unit = {}

  override def cleanPrecomputedCache(): Unit = {}

}

class SequenceEmbedderStandardState[T](e:Embedder[T]) extends IncrementalEmbedderState[T] {

  override def nextStateAndEmbed(x: T): (IncrementalEmbedderState[T], Expression) = {
    (this, e(x))
  }

}

