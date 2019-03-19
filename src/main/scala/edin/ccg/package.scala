package edin

import edin.ccg.representation.tree.TreeNode


package object ccg {

  val PROGRAM_NAME = "RotatingCCG"
  val PROGRAM_VERSION = 0.1

  type SentEmbedding = List[Array[Float]]

  case class Inst(tree:TreeNode, emb:SentEmbedding)

  type TrainInstance = Inst

}
