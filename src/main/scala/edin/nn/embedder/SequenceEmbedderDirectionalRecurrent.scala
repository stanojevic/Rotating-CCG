package edin.nn.embedder

import edin.general.YamlConfig
import edin.nn.DyFunctions
import edin.nn.DyFunctions._
import edin.nn.sequence.{MultiRNN, MultiRNNConfig, RecurrentState}
import edu.cmu.dynet.{Expression, ParameterCollection}

/**
  * Directional recurrent embedder
  */
sealed case class SequenceEmbedderDirectionalRecurrentConfig[T](
                                                      subEmbConf:SequenceEmbedderIncrementalConfig[T],
                                                      multRNNconf:MultiRNNConfig
                                                    ) extends SequenceEmbedderIncrementalConfig[T] {
  override val outDim: Int = multRNNconf.outDim
  override def construct()(implicit model: ParameterCollection): SequenceEmbedderIncremental[T] = new SequenceEmbedderDirectionalRecurrent[T](this)
}

object SequenceEmbedderDirectionalRecurrentConfig{

  def fromYaml[K](conf:YamlConfig) : SequenceEmbedderIncrementalConfig[K] =
    SequenceEmbedderDirectionalRecurrentConfig(
      subEmbConf = SequenceEmbedderIncrementalConfig.fromYaml[K](conf("sub-embedder-conf")),
      multRNNconf = MultiRNNConfig.fromYaml(conf("recurrent-conf"))
    )

}

class SequenceEmbedderDirectionalRecurrent[T](config: SequenceEmbedderDirectionalRecurrentConfig[T])(implicit model: ParameterCollection) extends SequenceEmbedderIncremental[T] {

  var embedder : SequenceEmbedderIncremental[T] = _
  var rnn : MultiRNN = _

  define()
  private def define():Unit ={
    embedder = config.subEmbConf.construct()
    rnn = config.multRNNconf.construct()
  }

  override def precomputeEmbeddings(sents:Iterable[List[T]]) : Unit = embedder.precomputeEmbeddings(sents)

  override def zeros : Expression = DyFunctions.zeros(config.multRNNconf.outDim)

  override def initState(): IncrementalEmbedderState[T] = new SequenceEmbedderRecurrentState[T](rnn.initState(), embedder.initState())

  override def cleanPrecomputedCache(): Unit = embedder.cleanPrecomputedCache()
}

class SequenceEmbedderRecurrentState[T](rnnState:RecurrentState, subState:IncrementalEmbedderState[T]) extends IncrementalEmbedderState[T] {

  override def nextStateAndEmbed(x: T): (IncrementalEmbedderState[T], Expression) = {
    val y = subState.nextStateAndEmbed(x)
    val newRnnState = rnnState.addInput( y._2 )
    val newState = new SequenceEmbedderRecurrentState(newRnnState, y._1 )
    (newState, newRnnState.h)
  }

}
