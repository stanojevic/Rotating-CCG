package edin.general

import java.io.{File, FileInputStream, FileOutputStream, PrintWriter}

import scala.io.StdIn.readLine

object TreeVisualizer {

  def main(args:Array[String]) : Unit = {
    var input = readLine("enter the penn tree: ")
    while(input != "" && input.toUpperCase() != "EXIT"){
      val tree = fromPennString(input)
      tree.visualize()
      input = readLine("enter the penn tree: ")
    }
    println("exiting")
  }

  case class SimpleTreeNode(label:String, children:List[SimpleTreeNode]) {
    def sent : List[String] = {
      if(children.isEmpty){
        List(label)
      }else{
        children.flatMap(_.sent)
      }
    }

    private def copyFile(src:String, dest:String) : Unit = {
      val inputChannel = new FileInputStream(src).getChannel()
      val outputChannel = new FileOutputStream(dest).getChannel()
      outputChannel.transferFrom(inputChannel, 0, inputChannel.size())
      inputChannel.close()
      outputChannel.close()
    }

    def saveVisual(outFile:File, graphLabel:String="") : Unit = {
      val dotString = toDotString(this, graphLabel)

      val tmpDotFile = File.createTempFile("visual", ".dot", null)
      tmpDotFile.deleteOnExit()
      val tmpFileName = tmpDotFile.getPath
      val pw = new PrintWriter(tmpDotFile)
      pw.println(dotString)
      pw.close()

      val dotCmd = s"dot -Tpng $tmpFileName -O"
      val pDot = Runtime.getRuntime.exec(dotCmd)
      pDot.waitFor()
      copyFile(s"$tmpFileName.png", outFile.getAbsolutePath)
      new File(tmpFileName).delete()
      new File(s"$tmpFileName.png").delete()
    }

    def visualize(graphLabel:String="") : Unit = {
      val file = File.createTempFile(s"visual_${graphLabel.replace(" ", "_")}_", ".png", null)
      file.deleteOnExit()
      this.saveVisual(file, graphLabel)

      val filename = file.toPath
      val xdgCmd = System.getProperty("os.name") match {
        case "Linux" => s"nohup xdg-open $filename"
        case _       => s"open $filename"
      }
      Runtime.getRuntime.exec(xdgCmd)
      val seconds = 2
      Thread.sleep(seconds*1000)
    }

  }

  // DOT VISUALIZATION

  def toDotString(mainNode:SimpleTreeNode, graphLabel:String) : String = {
    val colorMapping = Map[String, String](
      "unary" -> "green3",
      "non-unary" -> "firebrick1"
    ).withDefaultValue("blue")
    val shapeMapping = Map[String, String](
      "unary" -> "hexagon",
      "nonunary" -> "ellipse"
    ).withDefaultValue("plaintext")

    val terminalColor = "lightblue2"

    def toDotStringRec(node:SimpleTreeNode, nodeId:String) : (String, List[String]) = {
      var outStr = ""

      var terms = List[String]()

      outStr += nodeId+"[label=\""+escapeForDot(node.label)+"\"; "
      val shape = if(node.children.size==1) colorMapping("unary") else if(node.children.isEmpty) terminalColor else colorMapping("non-unary")
      outStr += "shape="+shape+"; "
      val color = if(node.children.size==1) colorMapping("unary") else if(node.label.matches("^B.*<.*")) "purple" else colorMapping("non-unary")
      outStr += s"color=$color; "
      // outStr += "fontname=\"Times-Bold\" ; "
      val fontSize = 20
      outStr += "fontsize="+fontSize+"; "
      outStr += "style=bold];\n"
      node.children.zipWithIndex.foreach{ case (child, index) =>
        val childName = nodeId+"_"+index
        if(child.children.nonEmpty){
          val res = toDotStringRec(child, childName)
          outStr += res._1
          terms ++= res._2
        }else{
          terms ++= List(childName)
        }
        val style = ""
        outStr += s"$nodeId -- $childName $style ;\n"
      }

      (outStr, terms)
    }

    var outStr = ""

    outStr += "graph { \n"
    if(graphLabel != ""){
      outStr += "  label=\""+escapeForDot(graphLabel)+"\"\n"
    }

    val res = toDotStringRec(mainNode, "node0")
    outStr += res._1
    val terms = res._2

    outStr += "  subgraph {rank=same;\n"
    val sent = mainNode.sent
    terms.zipWithIndex.foreach{ case (nodeId, i) =>
      val word = sent(i)
      outStr += "    "+nodeId+"[shape=plaintext; "
      outStr += "label=\""+escapeForDot(word)+"\" "
      outStr += "fontsize=30 "
      outStr += "style=bold; "
      outStr += "color="+terminalColor+"];\n"
    }
    outStr += "    edge[style=\"invis\"];\n"

    outStr += "  }\n"
    outStr += "}\n"

    outStr
  }

  private def escapeForDot(s:String) : String = {
    s.
      replace("\\", "\\\\").
      // replace("<", "<").
      // replace(">", ">").
      // replace("\\N", "\\\\N").
      replace("---", "ABCDEFGXYZ").
      replace("--", "ABCDEFG").
      replace("-", "\\n").
      replace("ABCDEFGXYZ", "\n--").
      replace("ABCDEFG", "--")
  }



  // PARSING

 def fromPennString(s:String) : SimpleTreeNode = {
    val tokens = tokenizePennString(s)
    parsePennRec(tokens)._1
  }

  private def parsePennRec(tokens:List[String]) : (SimpleTreeNode, List[String]) = {
    if(tokens.head == "("){
      val label = tokens.tail.head
      var workingTokens = tokens.tail.tail
      var children = List[SimpleTreeNode]()
      while(workingTokens.head != ")"){
        val (node, leftOver) = parsePennRec(workingTokens)
        children ::= node
        workingTokens = leftOver
      }

      children = children.reverse

      val node = SimpleTreeNode(
        label = unescapeBrackets(label),
        children = children)
      (node, workingTokens.tail)
    }else{
      val label = tokens.head
      val node = SimpleTreeNode(
        label = unescapeBrackets(label),
        children = List()
      )
      (node, tokens.tail)
    }
  }

  private def tokenizePennString(s:String) : List[String] = {
    val s1 = s.replaceAllLiterally("(", "( ")
    val s2 = s1.replaceAllLiterally(")", " )")
    val tokens = s2.split("\\s+").toList
    tokens.filterNot{_.matches("^\\s*$")}
  }

  private def escapeBrackets(label:String) = {
    label.replaceAllLiterally("(", "-LRB-").
      replaceAllLiterally(")", "-RRB-")
  }

  private def unescapeBrackets(label:String) = {
    label.replaceAllLiterally("-LRB-", "(").
      replaceAllLiterally("-RRB-", ")")
  }

}
