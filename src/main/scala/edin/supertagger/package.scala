package edin

package object supertagger {

  val SUPERTAGGER_NAME = "SUPER_WHATEVER"
  val SUPERTAGGER_VERSION = 0.1

  type SentEmbedding = List[Array[Float]]

  sealed case class TrainInst(words:List[String], tags:List[String], aux_tags:List[String])

}
