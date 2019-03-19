package edin.general

import java.io._

class ObjectIteratorFromFile[T](file:String) extends Iterator[T]{

  private val fh = new ObjectInputStream(
    new BufferedInputStream(
      new FileInputStream(file)))

  private var nextElement : Option[T] = readInNext()

  private def readInNext() : Option[T] = {
    try{
      val ob = fh.readObject().asInstanceOf[T]
      Some(ob)
    }catch{
      case e:EOFException => None
    }
  }

  override def hasNext: Boolean = nextElement.isDefined

  override def next(): T = {
    nextElement match {
      case None => {
        throw new EOFException("trying to read from a finished file; restart iterator")
      }
      case Some(ob) => {
        nextElement = readInNext()
        if(nextElement.isEmpty)
          fh.close()
        ob
      }
    }
  }
}
