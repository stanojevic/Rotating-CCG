package edin.ccg.transitions

import java.io.{FileInputStream, FileOutputStream, ObjectInputStream, ObjectOutputStream}

import edin.ccg.representation.category.Category
import edin.ccg.representation.combinators.Combinator
import edin.general.{Any2Int, DefaultAny2Int, String2Int}

class AllS2I (
               val w2i_tgt_gen      : Any2Int[String],
               val w2i_tgt_embed    : Any2Int[String],
               val c2i              : Any2Int[String],
               val cat2i            : Any2Int[Category],
               val comb2i           : Any2Int[Combinator],
               val taggingOptions2i : Any2Int[TransitionOption],
               val reduceOptions2i  : Any2Int[TransitionOption]
             ){

  def this(){
    this(
      w2i_tgt_gen      = new String2Int,
      w2i_tgt_embed    = new String2Int,
      c2i              = new String2Int,
      cat2i            = new DefaultAny2Int[Category](),
      comb2i           = new DefaultAny2Int[Combinator](),
      taggingOptions2i = new DefaultAny2Int[TransitionOption](),
      reduceOptions2i  = new DefaultAny2Int[TransitionOption]()
    )
  }

  import AllS2I.OUT_FN

  def save(modelDir:String) : Unit = {
    val fh = new ObjectOutputStream(new FileOutputStream(s"$modelDir/$OUT_FN"))
    fh.writeObject(w2i_tgt_gen)
    fh.writeObject(w2i_tgt_embed)
    fh.writeObject(c2i)
    fh.writeObject(cat2i)
    fh.writeObject(comb2i)
    fh.writeObject(taggingOptions2i)
    fh.writeObject(reduceOptions2i)
    fh.close()
  }
}

object AllS2I {

  val OUT_FN = "s2i.serialized"

  def load(modelDir:String) : AllS2I = {
    val fh = new ObjectInputStream(new FileInputStream(s"$modelDir/$OUT_FN"))
    val w2i_tgt_gen = fh.readObject().asInstanceOf[Any2Int[String]]
    val w2i_tgt_embed = fh.readObject().asInstanceOf[Any2Int[String]]
    val c2i = fh.readObject().asInstanceOf[Any2Int[String]]
    val cat2i            = fh.readObject().asInstanceOf[Any2Int[Category]]
    val comb2i           = fh.readObject().asInstanceOf[Any2Int[Combinator]]
    val taggingOptions2i = fh.readObject().asInstanceOf[Any2Int[TransitionOption]]
    val reduceOptions2i  = fh.readObject().asInstanceOf[Any2Int[TransitionOption]]
    fh.close()

    new AllS2I(
      w2i_tgt_gen      = w2i_tgt_gen,
      w2i_tgt_embed    = w2i_tgt_embed,
      c2i              = c2i,
      cat2i            = cat2i           ,
      comb2i           = comb2i          ,
      taggingOptions2i = taggingOptions2i,
      reduceOptions2i  = reduceOptions2i
    )
  }

}
