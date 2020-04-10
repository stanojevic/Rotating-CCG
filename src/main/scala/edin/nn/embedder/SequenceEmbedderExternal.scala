package edin.nn.embedder

import edin.general.YamlConfig
import edu.cmu.dynet.{Expression, ParameterCollection}
import edin.nn.DyFunctions._
import edin.algorithms.AutomaticResourceClosing._
import edin.nn.layers.SingleLayer

import scala.collection.mutable.{Map => MutMap}

case class SequenceEmbedderExternalConfig(
                                       dropout                 : Float,
                                       origDim                 : Int,
                                       outDim                  : Int,
                                     ) extends SequenceEmbedderGeneralConfig[String]{
  override def construct()(implicit model: ParameterCollection): SequenceEmbedderGeneral[String] = new SequenceEmbedderExternal(this)
}

object SequenceEmbedderExternalConfig{

  def fromYaml(origConf:YamlConfig) : SequenceEmbedderGeneralConfig[String] =
    SequenceEmbedderExternalConfig(
      dropout            = origConf("dropout"  ).float,
      origDim            = origConf("orig-dim"  ).int,
      outDim             = origConf("out-dim"  ).int
    )

}

object SequenceEmbedderExternal{

  private val cache = MutMap[List[String], List[Array[Float]]]()

  def addToCache(sent: List[String], embs: List[Array[Float]]) : Unit =
    cache(sent) = embs

  def lookup(sent: List[String]) : List[Array[Float]] =
    cache(sent)

  def loadEmbeddings(fn:String) : Unit = {
    var sent : List[String] = Nil
    var vectors = List[Array[Float]]()
    for(line <- linesFromFile(fn)){
      if(line startsWith "sent: "){
        if(vectors != Nil){
          cache(sent) = vectors.reverse
          vectors = Nil
        }
        sent = line.substring(6).trim.split(" +").toList
      }else{
        vectors ::= line.split(" +").map(_.toFloat)
      }
    }
    if(vectors != Nil)
      cache(sent) = vectors.reverse
  }

}

class SequenceEmbedderExternal(config: SequenceEmbedderExternalConfig)(implicit model: ParameterCollection) extends SequenceEmbedderGeneral[String] {

  private val compressor = SingleLayer.compressor(config.origDim, config.outDim)

  override def transduce(xs: List[String]): List[Expression] =
    SequenceEmbedderExternal.lookup(xs).map( x => dropout(compressor(vector(x)), config.dropout) )

  override def zeros: Expression = Expression.zeroes(config.outDim)

  override def precomputeEmbeddings(sents: Iterable[List[String]]): Unit = {}

  override def cleanPrecomputedCache(): Unit = {}

}

