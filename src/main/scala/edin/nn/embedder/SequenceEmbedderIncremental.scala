package edin.nn.embedder

import edin.general.YamlConfig
import edu.cmu.dynet.{Expression, ParameterCollection}

trait SequenceEmbedderIncrementalConfig[T] extends SequenceEmbedderGeneralConfig[T] {

  def construct()(implicit model: ParameterCollection): SequenceEmbedderIncremental[T]

}

object SequenceEmbedderIncrementalConfig{

  def fromYaml[K](origConf:YamlConfig) : SequenceEmbedderIncrementalConfig[K] =
    origConf("seq-emb-type").str match {
      case "local"     => SequenceEmbedderLocalConfig.fromYaml(origConf)
      case "recurrent"    => SequenceEmbedderDirectionalRecurrentConfig.fromYaml(origConf)
    }

}

trait SequenceEmbedderIncremental[T] extends SequenceEmbedderGeneral[T] {

  def initState() : IncrementalEmbedderState[T]

  override def transduce(xs: List[T]): List[Expression] = {
    var state = initState()
    var res = List[Expression]()
    for(x <- xs){
      val y = state.nextStateAndEmbed(x)
      state = y._1
      res ::= y._2
    }
    res.reverse
  }

}

trait IncrementalEmbedderState[T]{

  def nextStateAndEmbed(x: T): (IncrementalEmbedderState[T], Expression)

}

