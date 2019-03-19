package edin.nn.embedder

import edin.general.Any2Int
import edin.general.trees.dependency.DepNode
import edin.nn.DyFunctions.{dropout, _}
import edin.nn._
import edin.nn.layers.{Layer, SingleLayer}
import edu.cmu.dynet.{Expression, ParameterCollection}

sealed case class EmbedderDepTreeConfig(
                                       w2i         : Any2Int[String],
                                       p2i         : Any2Int[String],
                                       e2i         : Any2Int[String],
                                       wDim        : Int,
                                       pDim        : Int,
                                       eDim        : Int,
                                       nodeDim     : Int,
                                       wDropout    : Float,
                                       pDropout    : Float,
                                       eDropout    : Float,
                                       nodeDropout : Float
                                       ){
  def construct()(implicit model: ParameterCollection) = new EmbedderDepTree(this)
}


final case class EmbedderDepTreeState(h:Expression) extends StateClosed

// Not your typical embedder : doesn't implement Embedder interface
class EmbedderDepTree(c:EmbedderDepTreeConfig)(implicit model:ParameterCollection){

  var wE : EmbedderStandard[String] = _
  var pE : EmbedderStandard[String] = _
  var eE : EmbedderStandard[String] = _

  var compressor : Layer = _

  var wDropout    : Float = _
  var pDropout    : Float = _
  var eDropout    : Float = _
  var nodeDropout : Float = _

  define()

  def embed(root: DepNode): Unit = {
    root.allNodes.foreach{ node =>
      val wEmb = dropout(wE(node.word), wDropout)
      val pEmb = dropout(pE(node.posTag), pDropout)
      val eEmb = dropout(eE(node.relation), eDropout)
      val h = dropout(compressor(concat(wEmb, pEmb, eEmb)), nodeDropout)
      val state = EmbedderDepTreeState(h)
      node.nn   = state
    }
  }

  protected def define()(implicit model: ParameterCollection): Unit = {

    wDropout    = c.wDropout
    pDropout    = c.pDropout
    eDropout    = c.eDropout
    nodeDropout = c.nodeDropout

    wE = EmbedderStandardConfig(
      s2i       = c.w2i,
      outDim    = c.wDim,
      normalize = false,
      dropout   = c.wDropout
    ).construct()

    pE = EmbedderStandardConfig(
      s2i       = c.p2i,
      outDim    = c.pDim,
      normalize = false,
      dropout   = c.pDropout
    ).construct()

    eE = EmbedderStandardConfig(
      s2i       = c.e2i,
      outDim    = c.eDim,
      normalize = false,
      dropout   = c.eDropout
    ).construct()

    compressor = SingleLayer.compressor(c.wDim + c.pDim + c.eDim , c.nodeDim)

  }

}

