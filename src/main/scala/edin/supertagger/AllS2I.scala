package edin.supertagger

import java.io._

import edin.general.{Any2Int, String2Int}

class AllS2I (
               val w2i_of_embedder: Any2Int[String], // word 2 int
               val t2i: Any2Int[String], // pos  2 int
               val c2i: Any2Int[String], // char 2 int
               val aux_t2i: Any2Int[String] // auxiliary tags 2 int
             ){

  import AllS2I.OUT_FN

  def save(modelDir:String) : Unit = {
    val fh = new ObjectOutputStream(new FileOutputStream(s"$modelDir/$OUT_FN"))
    fh.writeObject(w2i_of_embedder)
    fh.writeObject(t2i)
    fh.writeObject(c2i)
    fh.writeObject(aux_t2i)
    fh.close()
  }
}

object AllS2I {

  val OUT_FN = "s2i.serialized"

  def load(modelDir:String) : AllS2I = {
    val fh = new ObjectInputStream(new FileInputStream(s"$modelDir/$OUT_FN"))
    val w2i = fh.readObject().asInstanceOf[Any2Int[String]]
    val t2i = fh.readObject().asInstanceOf[Any2Int[String]]
    val c2i = fh.readObject().asInstanceOf[Any2Int[String]]
    val aux_t2i = fh.readObject().asInstanceOf[Any2Int[String]]
    fh.close()

    new AllS2I(
      w2i_of_embedder = w2i,
      t2i = t2i,
      c2i = c2i,
      aux_t2i = aux_t2i
    )
  }

}

