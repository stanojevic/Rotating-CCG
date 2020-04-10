package edin.nn.embedder

import java.io._

import edin.general.Python

import scala.collection.mutable.{Map => MutMap}
import scala.util.{Failure, Success, Try}

object SequenceEmbedderPrecomputable{

  private var seqEmbPrecomputers : List[SequenceEmbedderPrecomputable] = List()

  def registerSeqEmb(semb:SequenceEmbedderPrecomputable) : Unit =
    seqEmbPrecomputers ::= semb

  def precompute(sents:List[List[String]], fn:String) : Unit =
    seqEmbPrecomputers.foreach(_.precompute(sents, fn))

  def precomputeParticular(embName:String, sents:List[List[String]], fn:String) : Unit =
    seqEmbPrecomputers.find(_.name==embName).foreach(_.precompute(sents, fn))

}

trait SequenceEmbedderPrecomputable{

  protected type T

  protected def embedBatchDirect(sents: Seq[List[String]]) : List[T]

  protected val cache: MutMap[List[String], T] = MutMap()

  val name: String

  SequenceEmbedderPrecomputable.registerSeqEmb(this)

  def precompute(sents: List[List[String]], fn: String): Unit = {

    Python.exec("")

    val file = new File(fn+"."+name)
    val lockFile = new File(fn+"."+name+".lock")

    if(lockFile.exists()){
      System.err.println(s"I believe some other process is precomputing embeddings so I'm waiting for it to finish")
      System.err.println(s"if that is not the case:")
      System.err.println(s" - kill this process")
      System.err.println(s" - delete $file")
      System.err.println(s" - delete $lockFile")
      System.err.println(s" - start this process again")
      var i = 0
      while(lockFile.exists()){
        if(i%30==0)
          System.err.println(s"waiting for $name embeddings ; 5 minute check")
        Thread.sleep(10*1000)
        i+=1
      }
    }

    if(! file.exists()){
      System.err.println(s"Precomputing $name START")
      lockFile.createNewFile()
      val pw = new ObjectOutputStream(
        new BufferedOutputStream(
          new FileOutputStream(file)))
      for((batch, i) <- sents.sliding(100, 100).zipWithIndex){
        System.err.println(s"Precomputed ${i*100}")
        for( (sent, embs) <- batch zip embedBatchDirect(batch)){
          pw.writeObject((sent, embs))
        }
      }
      pw.close()
      lockFile.delete()
      System.err.println(s"Precomputing $name END")
    }

    System.err.println(s"Loading precomputed $name from $file START")
    val fh = new ObjectInputStream(
      new BufferedInputStream(
        new FileInputStream(file)))
    var stop = false
    var i = 0
    while(! stop){
      if(i%1000==0)
        System.err.println(s"Loaded $i")
      i+=1
      Try(fh.readObject().asInstanceOf[(List[String], T)]) match {
        case Success((sent, embs)) =>
          cache(sent) = embs
        case Failure(_:EOFException) =>
          stop = true
        case Failure(e) =>
          throw e
      }
    }
    fh.close()
    System.err.println(s"Loading precomputed $name from $file DONE")
  }

}

