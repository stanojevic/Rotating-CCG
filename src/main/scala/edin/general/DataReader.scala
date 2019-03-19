package edin.general

import java.io._

import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}
import sys.process._


class TypedObjectWriter[T](val fn:String){

  // private val fh = new BufferedWriter(new FileWriter(fn))
  private val fh = new BufferedOutputStream(new FileOutputStream(fn))

  def write(ob:T) : Unit =
    TypedObjectIO.writeObject(ob.asInstanceOf[AnyRef], fh)

  def close(): Unit = fh.close()

}

class TypedObjectReader[T](val fn:String) extends Iterable[T]{

  def reshuffle() : Unit = {
    // use sample (http://alexpreynolds.github.io/sample/) if there is not enough memory
    // Seq("/bin/sh", "-c", s"sample $fn > $fn.tmp").!
    Seq("/bin/sh", "-c", s"shuf $fn > $fn.tmp").!
    s"mv $fn.tmp $fn".!
  }

  override def iterator: Iterator[T] = new OIterator(fn)

  private class OIterator(val fn:String) extends Iterator[T]{

    private val fh = new ByteReader(fn)

    private var bufferedObject:Option[T] = readInNext()

    override def hasNext: Boolean = bufferedObject.isDefined

    override def next(): T =
      bufferedObject match {
        case None =>
          throw new EOFException("trying to read from a finished file; restart iterator")
        case Some(ob) =>
          bufferedObject = readInNext()
          if(bufferedObject.isEmpty)
            fh.close()
          ob
      }

    private def readInNext():Option[T] = Try(TypedObjectIO.readObject(fh)) match {
      case Success(value) => Some(value.asInstanceOf[T])
      case Failure(_) => None
    }

  }

}

private object TypedObjectIO{

  def writeObject(obj:AnyRef, fos:BufferedOutputStream) : Unit = {
    val bytes = object2bytes(obj)
    val escapedBytes = escapeNewLine(bytes)
    fos.write(escapedBytes)
    fos.write('\n')
  }

  def readObject(fis:ByteReader): AnyRef = {
    val escapedBytes = fis.nextLine()
    val unescapedBytes = unEscapeNewLine(escapedBytes)
    val obj2 = bytes2object(unescapedBytes)
    obj2
  }

  def object2bytes(obj:AnyRef) : Array[Byte] = {
    val bos = new ByteArrayOutputStream()
    val obos = new ObjectOutputStream(bos)
    obos.writeObject(obj)
    obos.flush()
    bos.toByteArray
  }

  def bytes2object(bs: Array[Byte]) :AnyRef = {
    val bfh = new ByteArrayInputStream(bs)
    val obfh = new ObjectInputStream(bfh)
    val obj = obfh.readObject()
    obfh.close()
    obj
  }

  private val ESCAPE = "____ESCAPE_KEY____".map{_.toByte}.toArray

  def unEscapeNewLine(bs:Array[Byte]) : Array[Byte] = {
    var escapePositions = List[Int]()
    for(i <- 0 until bs.length-ESCAPE.length){
      var isEscape = true
      var k = 0
      while(isEscape && k<ESCAPE.length){
        if(ESCAPE(k) != bs(i+k))
          isEscape = false
        k += 1
      }
      if(isEscape)
        escapePositions ::= i
    }
    escapePositions = escapePositions.reverse

    val new_bs = Array.ofDim[Byte](bs.length - escapePositions.size * ESCAPE.length + escapePositions.size)
    var i = 0
    var j = 0
    while(i < bs.length){
      if(escapePositions.nonEmpty && i == escapePositions.head){
        new_bs(j)='\n'
        j+=1
        i+=ESCAPE.length
        escapePositions = escapePositions.tail
      }else{
        new_bs(j)=bs(i)
        j+=1
        i+=1
      }
    }
    new_bs
  }

  def escapeNewLine(bs:Array[Byte]) : Array[Byte] = {
    val nlns = bs.count(_ == '\n')
    val new_bs = Array.ofDim[Byte](bs.length - nlns + nlns*ESCAPE.length)
    var j = 0
    for(i <- bs.indices){
      if(bs(i) == '\n'){
        for(k <- ESCAPE.indices){
          new_bs(j) = ESCAPE(k)
          j += 1
        }
      }else{
        new_bs(j) = bs(i)
        j += 1
      }
    }
    new_bs
  }
}

private class ByteReader(fn:String){

  private val fh = new BufferedInputStream(new FileInputStream(fn))

  private val buffer = Array.ofDim[Byte](fh.available())
  private var endPointer = fh.read(buffer, 0, buffer.length)
  private var startPointer = 0

  @inline
  private def nextByte():Byte = {
    if(endPointer == -1){
      throw new EOFException
    }
    if(startPointer == endPointer){
      if(endPointer < buffer.length){
        throw new EOFException
      }else{
        endPointer = fh.read(buffer, 0, buffer.length)
        startPointer = 1
        buffer(0)
      }
    }else{
      startPointer+=1
      buffer(startPointer-1)
    }
  }

  def nextLine() : Array[Byte] = {
    var bytes = new ArrayBuffer[Byte](fh.available())

    var byte = nextByte()
    while(byte != '\n'){
      bytes += byte
      byte = nextByte()
    }

    bytes.toArray
  }

  def close():Unit = fh.close()

}

