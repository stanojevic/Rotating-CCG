package edin.nn.embedder

import edin.general.YamlConfig
import edu.cmu.dynet.{Expression, ParameterCollection}

/**
  * This is abstract stuff or creating (possibly non-incremental) sequence embedders
  */

trait SequenceEmbedderGeneralConfig[T] {

  val outDim : Int

  def construct()(implicit model: ParameterCollection): SequenceEmbedderGeneral[T]

}

object SequenceEmbedderGeneralConfig{

  def fromYaml[K](origConf:YamlConfig) : SequenceEmbedderGeneralConfig[K] =
    origConf("seq-emb-type").str match {
      case "local"        => SequenceEmbedderLocalConfig          .fromYaml(origConf)
      case "global"       => SequenceEmbedderGlobalConfig         .fromYaml(origConf)
      case "combine"      => SequenceEmbedderGeneralCombinerConfig.fromYaml(origConf)
      case "ELMo"         => SequenceEmbedderELMoConfig           .fromYaml(origConf).asInstanceOf[SequenceEmbedderGeneralConfig[K]]
      case "BERT"         => SequenceEmbedderBERTConfig           .fromYaml(origConf).asInstanceOf[SequenceEmbedderGeneralConfig[K]]
      case "External"     => SequenceEmbedderExternalConfig       .fromYaml(origConf).asInstanceOf[SequenceEmbedderGeneralConfig[K]]
      case "SynBERT"      => SequenceEmbedderBERTSyntacticConfig  .fromYaml(origConf).asInstanceOf[SequenceEmbedderGeneralConfig[K]]
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

