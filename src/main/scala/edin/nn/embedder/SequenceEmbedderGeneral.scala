package edin.nn.embedder

import edin.general.YamlConfig
import edu.cmu.dynet.{Expression, ParameterCollection}

trait SequenceEmbedderGeneralConfig[T] {

  def construct()(implicit model: ParameterCollection): SequenceEmbedderGeneral[T]

}

object SequenceEmbedderGeneralConfig{

  def fromYaml[K](origConf:YamlConfig) : SequenceEmbedderGeneralConfig[K] = {
    origConf("seq-emb-type").str match {
      case "standard"     => SequenceEmbedderStandardConfig.fromYaml(origConf)
      case "global"       => SequenceEmbedderBiRecurrentConfig.fromYaml(origConf)
      case "ELMo"         => SequenceEmbedderELMoConfig.fromYaml(origConf).asInstanceOf[SequenceEmbedderGeneralConfig[K]]
    }
  }

}

trait SequenceEmbedderGeneral[T] {

  def transduce(xs: List[T]) : List[Expression]

  def zeros : Expression

  def precomputeEmbeddings(sents:Iterable[List[T]]) : Unit

  def cleanPrecomputedCache() : Unit

  def fakeIncrementalEmbedder(xs: List[T]) : IncrementalEmbedderState[T] =
    new FakeIncrementalEmbedderState(transduce(xs))

}

private class FakeIncrementalEmbedderState[T](embs:List[Expression]) extends IncrementalEmbedderState[T] {

  override def nextStateAndEmbed(x: T): (IncrementalEmbedderState[T], Expression) =
    (new FakeIncrementalEmbedderState[T](embs.tail), embs.head)

}

