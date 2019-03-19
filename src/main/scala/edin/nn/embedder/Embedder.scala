package edin.nn.embedder

import edin.general.YamlConfig
import edu.cmu.dynet.{Expression, ParameterCollection}

trait EmbedderConfig[T] {
  val outDim:Int
  def construct()(implicit model: ParameterCollection) : Embedder[T]
}

object EmbedderConfig {

  def fromYaml[T](conf:YamlConfig) : EmbedderConfig[T] = {
    conf("emb-type").str match {
      case "char-lstm"        => EmbedderCharLSTMConfig.fromYaml(conf).asInstanceOf[EmbedderConfig[T]]
      case "word-standard"    => EmbedderStandardConfig.fromYaml(conf)
      case "position-learned" => EmbedderPositionsLearnedConfig.fromYaml(conf)
      case "position-sin"     => EmbedderPositionsSinusoidConfig.fromYaml(conf)
      case "combined"         => EmbedderCombinerConfig.fromYaml(conf)
    }
  }

}

trait Embedder[T] {

  val outDim: Int

  def apply(x:T) : Expression

}

