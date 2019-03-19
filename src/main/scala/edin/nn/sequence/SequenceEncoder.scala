package edin.nn.sequence

import edin.general.YamlConfig
import edu.cmu.dynet.{Expression, ParameterCollection}

trait SequenceEncoderConfig{

  val outDim:Int

  def construct()(implicit model: ParameterCollection) : SequenceEncoder
}

object SequenceEncoderConfig{

  def fromYaml(conf:YamlConfig) : SequenceEncoderConfig = {
    if(conf("bi-directional").bool){
      BiRNNConfig.fromYaml(conf)
    }else{
      MultiRNNConfig.fromYaml(conf)
    }
//    conf("bi-directional") match {
//      case "recurrent"    => MultiRNNConfig.fromYaml(conf)
//      case "bi-recurrent" => BiRNNConfig.fromYaml(conf)
//    }
  }

}

trait SequenceEncoder{

  def transduce(x:List[Expression]) : List[Expression]

}
