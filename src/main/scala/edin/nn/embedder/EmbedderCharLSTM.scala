package edin.nn.embedder

import edin.general.{Any2Int, YamlConfig}
import edin.nn.sequence.{MultiRNN, MultiRNNConfig}
import edin.nn.DyFunctions._
import edu.cmu.dynet.{Expression, ParameterCollection}

sealed case class EmbedderCharLSTMConfig(
                                      c2i                    : Any2Int[String],
                                      char_embedding_raw_dim : Int,
                                      outDim                 : Int,
                                      dropout                : Float
                                   ) extends EmbedderConfig[String]{
  def construct()(implicit model: ParameterCollection) = new EmbedderCharLSTM(this)
}

object EmbedderCharLSTMConfig {

  def fromYaml(conf:YamlConfig) : EmbedderConfig[String] = {
    EmbedderCharLSTMConfig(
      c2i = conf("c2i").any2int,
      char_embedding_raw_dim = conf("char-embedding-raw-dim").int,
      outDim = conf("out-dim").int,
      dropout = conf.getOrElse("dropout", 0f)
    )
  }

}

class EmbedderCharLSTM(config: EmbedderCharLSTMConfig)(implicit model: ParameterCollection) extends Embedder[String] {

  var embedder_raw  : EmbedderStandard[String] = _
  var forward_rnn   : MultiRNN         = _
  var backward_rnn  : MultiRNN         = _
  var dropProb      : Float            = _

  override val outDim: Int = config.outDim

  define()

  def define() : Unit = {
    embedder_raw = EmbedderStandardConfig(
      s2i       = config.c2i,
      outDim    = config.char_embedding_raw_dim,
      normalize = false,
      dropout   = 0f
    ).construct()
    forward_rnn = MultiRNNConfig(
      rnnType           = "lstm",
      inDim             = config.char_embedding_raw_dim,
      outDim            = config.outDim/2,
      layers            = 1,
      withLayerNorm     = false,
      withWeightNorm    = false,
      dropProbRecurrent = 0f
    ).construct()
    backward_rnn = MultiRNNConfig(
      rnnType           = "lstm",
      inDim             = config.char_embedding_raw_dim,
      outDim            = config.outDim/2,
      layers            = 1,
      withLayerNorm     = false,
      withWeightNorm    = false,
      dropProbRecurrent = 0f
    ).construct()
    dropProb = config.dropout
  }

  override def apply(word:String) : Expression = {
    val chars = word.toCharArray.toList
    val chars_i = chars.map{config.c2i(_)}
    val char_embs_raw = chars_i.map{embedder_raw(_)}
    val forward_rnn_out = forward_rnn.transduce(char_embs_raw).last
    val backward_rnn_out = backward_rnn.transduceBackward(char_embs_raw).head
    dropout(Expression.concatenate(forward_rnn_out, backward_rnn_out), dropProb)
  }

}
