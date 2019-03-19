package edin.nn.embedder

import java.io._
import java.net.ServerSocket

import edin.algorithms.Pointer
import edin.general.{Global, YamlConfig}
import edin.nn.DyFunctions._
import edin.nn.layers.{Layer, SingleLayer}
import edu.cmu.dynet.{Expression, ParameterCollection}

import scala.collection.mutable.{Map => MutMap}
import scala.collection.JavaConverters._
import org.apache.thrift.transport.TSocket
import org.apache.thrift.protocol.TBinaryProtocol

import scala.util.{Failure, Success, Try}


case class SequenceEmbedderELMoConfig(
                                       embeddingType           : String,
                                       withoutCompression      : Boolean,
                                       normalize               : Boolean,
                                       dropout                 : Float,
                                       outDim                  : Int,
                                       elmoPointer             : Pointer[SequenceEmbedderELMo]
                                     ) extends SequenceEmbedderGeneralConfig[String]{
  override def construct()(implicit model: ParameterCollection): SequenceEmbedderGeneral[String] = new SequenceEmbedderELMo(this)
}

object SequenceEmbedderELMoConfig{

  def fromYaml(origConf:YamlConfig) : SequenceEmbedderGeneralConfig[String] = {
    val embType = origConf("ELMo-type").str
    assert(Set("average-top", "concat-top", "forward-top", "backward-top", "local") contains embType)
    val outDim = origConf("out-dim").int
    val withoutCompression = origConf.getOrElse("withoutCompression", false)
    SequenceEmbedderELMoConfig(
      embeddingType      = embType,
      withoutCompression = withoutCompression,
      normalize          = origConf.getOrElse("normalize", false),
      dropout            = origConf.getOrElse("dropout", 0f),
      outDim             = outDim,
      elmoPointer        = origConf.getOrElse("elmo-pointer", new Pointer[SequenceEmbedderELMo])
    )
  }

}

object SequenceEmbedderELMo{

  /**
    * @param reader
    *               reader is a funciton that transforms filename into a iterator over sentences
    *               where each sentence is a list of words
    *               this is useful in case line is not a normal sequenc of words but for example a penn tree
    */
  def precomputeEmbsSafe(sentsFile:String, reader:String => Iterator[List[String]], elmoType:String) : Unit = {
    val lockFile = new File(s"$sentsFile.elmo.$elmoType.lock")

    if(lockFile.exists()){
      var i = 0
      while(lockFile.exists()){
        if(i%30==0)
          System.err.println("waiting for embeddings ; 5 minute check")
        Thread.sleep(10*1000)
        i+=1
      }
    }else{
      if(! new File(s"$sentsFile.elmo.$elmoType").exists()){
        lockFile.createNewFile()
        precomputeEmbs(sentsFile, reader, elmoType)
        lockFile.delete()
        Thread.sleep( 1*1000)
      }
    }
  }

  private def precomputeEmbs(sentsFile:String, reader:String => Iterator[List[String]], elmoType:String) : Unit = {
    val pw = new ObjectOutputStream(
      new BufferedOutputStream(
        new FileOutputStream(s"$sentsFile.elmo.$elmoType")))

    val batchSize = 64

    var lastPrinted = 0
    var processed = 0

    reader(sentsFile).sliding(batchSize, batchSize).foreach{ batch =>
      val embs = SequenceEmbedderELMo.embed_sents(elmoType, batch.toList)
      for(emb <- embs){
        pw.writeObject(emb)
      }
      processed += batch.size
      if(processed - lastPrinted > 100){
        lastPrinted = processed
        System.err.println(s"processed $processed")
      }
    }

    pw.close()

    SequenceEmbedderELMo.endServer()
  }

  private def findFreePort(start:Int, end:Int) : Int = {
    for(i <- start to end){
      Try(new ServerSocket(i)) match {
        case Success(socket) =>
          socket.close()
          return i
        case Failure(_) =>
      }
    }
    throw new Exception("couldn't find a free port")
  }


  def embed_sents(
             emb_type     : String,
             sents        : List[List[String]]
           ) : List[List[Array[Float]]] = {
    if(sents.isEmpty){
      return Nil
    }
    val java_sents = sents.map{_.asJava}.asJava
    val embs = elmo_service.embed_sents(java_sents, emb_type)
    val scala_embs = embs.asScala.toList.map{ sent_embs =>
      sent_embs.asScala.toList.map{ emb =>
        emb.asScala.map{_.toFloat}.toArray
      }
    }
    scala_embs
  }

  private var _memo_elmo_service : SequenceEmbedderELMo_Service.Client = _
  private var _tsocket : TSocket = _
  def elmo_service : SequenceEmbedderELMo_Service.Client = {
    if(_memo_elmo_service == null){
      System.err.println("\nStarting ELMo server START")
      val port = findFreePort(9000, 9100)
      System.err.println(s"using port $port for ELMo")

      val script = Global.projectDir+"/scripts/embedding/elmo_embed_server.py"
      val cmd = s"$script $port"
      Runtime.getRuntime.exec(Array("/bin/sh", "-c", cmd))
      Thread.sleep(1000) // to give server enough time to start ; just in case

      _tsocket = new TSocket("localhost", port)
      _tsocket.open()
      _memo_elmo_service = new SequenceEmbedderELMo_Service.Client(new TBinaryProtocol(_tsocket))
      _memo_elmo_service.start_elmo()
      System.err.println("Starting ELMo server DONE")
    }
    _memo_elmo_service
  }

  def endServer() : Unit = {
    if(_memo_elmo_service != null){
      _memo_elmo_service.quit()
      _memo_elmo_service = null
    }
    if(_tsocket != null){
      _tsocket.close()
      _tsocket = null
    }
  }

  override def finalize(): Unit = {
    endServer()
  }

}

class SequenceEmbedderELMo(config: SequenceEmbedderELMoConfig)(implicit model: ParameterCollection) extends SequenceEmbedderGeneral[String] {

  config.elmoPointer.content = this

  private val half_dimension = 512

  private val outDim = config.outDim
  private val embeddingType = config.embeddingType

  private val compressor : Layer = if(config.withoutCompression){
    assert(outDim == ELMoDim)
    null
  }else{
    SingleLayer.compressor(ELMoDim, outDim)
  }

  private def compress(x:Expression) :Expression = if(compressor == null) x else compressor(x)

  var cachedEmbeddings : MutMap[List[String], List[Array[Float]]] = MutMap()

  // override def precomputeEmbeddings(sents:Iterable[List[String]]) : Unit = {}
  override def precomputeEmbeddings(sents:Iterable[List[String]]) : Unit =
    for((sent, emb) <- sents zip embedSents(sents))
      cachedEmbeddings(sent) = emb

  private var lastKCache = List[(List[String], List[Array[Float]])]()
  private val lastKToCache = 3

  override def transduce(xs: List[String]): List[Expression] = {
    val vectors = if(cachedEmbeddings contains xs) {
      cachedEmbeddings(xs)
    }else if(lastKCache.exists(_._1 == xs)){
      lastKCache.find(_._1 == xs).get._2
    }else{
      embedSent(xs)
    }
    if(lastKToCache>0){
      val rest = if(lastKCache.size >= lastKToCache){
        lastKCache.init
      }else{
        lastKCache
      }
      lastKCache = (xs, vectors) :: rest
    }
    vectors.map{x =>
      val xExp = vector(x)
      val xNormalized = if(config.normalize) xExp/(Expression.l2Norm(xExp)+1e-10) else xExp
      compress(dropout(xNormalized, config.dropout))
    }
  }

  override def zeros: Expression = Expression.zeros(outDim)

  private def ELMoDim: Int = embeddingType match {
    case "concat-top" => 2*half_dimension
    case _            =>   half_dimension
  }

  override def cleanPrecomputedCache(): Unit = {
    cachedEmbeddings = MutMap()
  }

  private def embedSent(sent:List[String]) : List[Array[Float]] = {
    embedSents(List(sent)).head
  }

  private def embedSents(sents:Iterable[List[String]]) : Iterable[List[Array[Float]]] = {
    val (processed, toProcess) = sents.zipWithIndex.partition{case (sent, _) => cachedEmbeddings.contains(sent)}
    val toProcessSents = toProcess.map(_._1).toList
    val allEmbs = SequenceEmbedderELMo.embed_sents(embeddingType, toProcessSents)
    val toProcessResult = (toProcess zip allEmbs).map{case ((_, i), emb) => (emb, i)}
    val processedResult = processed.map{case (sent, i) => (cachedEmbeddings(sent), i)}
    val result = (toProcessResult ++ processedResult).
      toList.
      sortBy(_._2).
      map( _._1.map(EmbedderStandard.fixEmbedding) )
    if(! (sents zip result).forall(x => x._1.size == x._2.size)){
      throw new Exception("number of words and the number of precomputed embeddings doesn't match ; need to recompute them?")
    }
    result
  }

}

