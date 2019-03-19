package edin.general.trees.dependency

import java.io.{BufferedReader, EOFException, FileReader}

import edin.nn.tree.EncodableNode
import scala.collection.mutable.{Map => MutMap}

sealed case class DepNode(
                        word     : String,
                        posTag   : String,
                        position : Int   ,
                        relation : String,
                        children : List[DepNode]
                      ) extends EncodableNode with Serializable {

  lazy val nodeCount : Int = 1 + (if(children.isEmpty) 0 else children.map{_.nodeCount}.sum)
  lazy val leafCount : Int = if(children.isEmpty) 1 else children.map{_.leafCount}.sum

  def allEdges         : List[(DepNode, DepNode)] = children.flatMap(_.allEdges) ++ children.map{(this, _)}
  def allNodes         : List[DepNode]            = allNodesTopDown
  def allNodesBottomUp : List[DepNode]            = children.flatMap(_.allNodes) ++ List(this)
  def allNodesTopDown  : List[DepNode]            = this :: children.flatMap(_.allNodes)
  def allNodesLinear   : List[DepNode]            = allNodes.sortBy(_.position)
}

class ConllReader(fn:String) extends Iterable[DepNode] {

  override def iterator: Iterator[DepNode] = new ConllIterator(fn)

  private class ConllIterator(fn:String) extends Iterator[DepNode]{

    private val fh = new BufferedReader(new FileReader(fn))

    private var bufferedTree:Option[DepNode] = readInNext()

    override def hasNext: Boolean = bufferedTree.isDefined

    override def next(): DepNode = {
      bufferedTree match {
        case None =>
          throw new EOFException("trying to read from a finished file; restart iterator")
        case Some(ob) =>
          bufferedTree = readInNext()
          ob
      }
    }

    private case class Info(word:String, posTag:String, rel:String)
    private type WordInfos        = MutMap[Int, Info]
    private type WordConnections = MutMap[Int, List[Int]]

    private def readInNext(): Option[DepNode] = {
      var lines = List[String]()
      lines ::= fh.readLine()
      if(lines.head == null){
        fh.close()
        return None
      }
      while(! lines.head.isEmpty){
        lines ::= fh.readLine()
      }
      lines = lines.tail.reverse

      val wordConnections: WordConnections = MutMap().withDefaultValue(List())
      val wordInfo: WordInfos = MutMap(0 -> Info("ROOT", "ROOT", "ROOT"))
      for(line <- lines){
        val fields = line.split("\\s+")
        val head_i = Integer.parseInt(fields(5))
        val curr_i = Integer.parseInt(fields(0))
        val curr_word = fields(1)
        val curr_pos = fields(3)
        val curr_relation = fields(6)
        wordInfo(curr_i) = Info(curr_word, curr_pos, curr_relation)
        wordConnections(head_i) ::= curr_i
      }
      val tree = buildTree(wordInfo, wordConnections, 0)
      Some(tree)
    }

    private def buildTree(wordInfos: WordInfos, wordConnections: WordConnections, root:Int) : DepNode = {
      val children = wordConnections(root).sorted.map{ i =>
        buildTree(wordInfos, wordConnections, i)
      }
      DepNode(
        word     = wordInfos(root).word,
        posTag   = wordInfos(root).posTag,
        position = root-1,
        relation = wordInfos(root).rel,
        children = children
      )
    }
  }

}

