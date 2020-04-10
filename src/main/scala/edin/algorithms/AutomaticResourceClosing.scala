package edin.algorithms

import java.io.File

import scala.io.Source
import scala.util.{Failure, Success, Try}

object AutomaticResourceClosing {

  def linesFromFile(fn:String) : Iterator[String] = linesFromFile(new File(fn))

  def linesFromFile(fn:File) : Iterator[String] = new Iterator[String] {

    private var fh = Source.fromFile(fn)
    private val lines = fh.getLines()

    override def hasNext: Boolean = fh!=null && lines.hasNext

    override def next(): String =
      if (hasNext)
        Try(lines.next) match {
          case Success(s) =>
            if(!hasNext){
              fh.close()
              fh = null
            }
            s
          case Failure(e) =>
            throw e
        }
      else
        sys.error("you are trying to read a reasource that is already closed")

  }

}
