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

  object Color extends Enumeration {
    type Color = Value
    val RED        = Value("firebrick1")
    val GREEN      = Value("green3")
    val BLUE       = Value("blue")
    val PURPLE     = Value("purple")
    val LIGHT_BLUE = Value("lightblue2")
    val BLACK      = Value("black")
    val GRAY       = Value("gray")
  }

  object Shape extends Enumeration{
    type Shape = Value
    val TRAPEZIUM = Value("trapezium")
    val HOUSE     = Value("house")
    val BOX       = Value("box")
    val HEXAGON   = Value("hexagon")
    val ELLIPSE   = Value("ellipse")
    val RECTANGLE = Value("rectangle")
    val NOTHING   = Value("plaintext")
  }

  case class SimpleTreeNode(
                             label:String,
                             children:List[SimpleTreeNode],
                             color: Color.Color = null,
                             shape: Shape.Shape = null,
                             textBold:Boolean=false,
                             textColor:Color.Color=Color.BLACK,
                             textSize:Int= -1,
                             position:Int= 0) {

    def sent : List[String] = allTerminals.sortBy(_.position).map(_.label)

    def allTerminals : List[SimpleTreeNode] =
      if(children.isEmpty)
        this :: Nil
      else
        children.flatMap(_.allTerminals)

    private def copyFile(src:String, dest:String) : Unit = {
      val inputChannel = new FileInputStream(src).getChannel
      val outputChannel = new FileOutputStream(dest).getChannel
      outputChannel.transferFrom(inputChannel, 0, inputChannel.size())
      inputChannel.close()
      outputChannel.close()
    }

    def saveVisual(outFile:File, graphLabel:String="", fileType:String="pdf") : Unit = {
      outFile.getName.split(".").lastOption.foreach{ x =>
        if(x != fileType){
          sys.error(s"cannot save $fileType into file with extension $x")
        }
      }
      val dotString = toDotString(this, graphLabel)

      val tmpDotFile = File.createTempFile("visual", ".dot", null)
      tmpDotFile.deleteOnExit()
      val tmpFileName = tmpDotFile.getPath
      val pw = new PrintWriter(tmpDotFile)
      pw.println(dotString)
      pw.close()

      val dotCmd = s"dot -T$fileType $tmpFileName -O"
      val pDot = Runtime.getRuntime.exec(dotCmd)
      pDot.waitFor()
      copyFile(s"$tmpFileName.$fileType", outFile.getAbsolutePath)
      new File(tmpFileName).delete()
      new File(s"$tmpFileName.$fileType").delete()
    }

    def visualize(graphLabel:String="", fileType:String="pdf") : Unit = {
      val file = File.createTempFile(s"visual_${graphLabel.replace(" ", "_")}_", "."+fileType, null)
      file.deleteOnExit()
      this.saveVisual(file, graphLabel=graphLabel, fileType=fileType)

      val filename = file.toPath
      val xdgCmd = System.getProperty("os.name") match {
        /** run this if you use evince and have big pdfs: gsettings set org.gnome.Evince page-cache-size 500 */
        case "Linux" => s"nohup xdg-open $filename"
        case _       => s"nohup firefox  $filename"
      }
      Runtime.getRuntime.exec(xdgCmd)
      val seconds = 2
      Thread.sleep(seconds*1000)
    }

  }

  // DOT VISUALIZATION

  def toDotString(mainNode:SimpleTreeNode, graphLabel:String) : String = {
    def toDotStringRec(node:SimpleTreeNode, nodeId:String) : (String, List[String]) = {
      var outStr = ""

      var terms = List[String]()
      outStr += nodeId+"["
      if(node.textBold)
        outStr += "fontname=\"Times Bold\"; "
      val fontcolor = Option(node.textColor).getOrElse(Color.BLACK).toString
      outStr += s"fontcolor=$fontcolor; "
      outStr += "label=\""+escapeForDot(node.label)+"\"; "
      val shape = Option(node.shape).getOrElse(Shape.ELLIPSE).toString
      outStr += "shape="+shape+"; "
      val color = Option(node.color).getOrElse(Color.BLUE).toString
      outStr += s"color=$color; "
      val fontsize = if(node.textSize < 0) 20 else node.textSize
      outStr += s"fontsize=$fontsize ; "
      outStr += "style=bold; "
      outStr += "];\n"
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

    outStr += "  subgraph {rank=same;rankdir=LR;\n"
    (terms zip mainNode.allTerminals).sortBy(_._2.position).foreach{ case (nodeId, node) =>
      val color = Option(node.color).getOrElse(Color.LIGHT_BLUE).toString
      val shape = Option(node.shape).getOrElse(Shape.NOTHING).toString
      val word = node.label
      outStr += "    "+nodeId+"[ "
      if(node.textBold)
        outStr += "fontname=\"Times Bold\"; "
      val fontcolor = Option(node.textColor).getOrElse(Color.BLACK).toString
      outStr += s"fontcolor=$fontcolor; "
      outStr += "label=\""+escapeForDot(word)+"\" "
      outStr += "style=bold; "
      val fontsize = if(node.textSize < 0) 30 else node.textSize
      outStr += s"fontsize=$fontsize "
      outStr += "color="+color+";"
      outStr += "shape="+shape+";"
      outStr += "];\n"
    }
    outStr += "    edge[style=\"invis\"];\n"

    outStr += (terms zip mainNode.allTerminals).sortBy(_._2.position).map(_._1).mkString("--")+";\n"

    outStr += "  }\n"
    outStr += "}\n"

    outStr
  }

  private def escapeForDot(s:String) : String =
    s.replace("\\", "\\\\")
     // .replace("<", "<")
     // .replace(">", ">")
     // .replace("\\N", "\\\\N")
     // .replace("---", "ABCDEFGXYZ")
     // .replace("--", "ABCDEFG")
     // .replace("-", "\\n")
     // .replace("ABCDEFGXYZ", "\n--")
     // .replace("ABCDEFG", "--")



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

//  private def escapeBrackets(label:String) =
//    label.replaceAllLiterally("(", "-LRB-").
//      replaceAllLiterally(")", "-RRB-")

  private def unescapeBrackets(label:String) : String =
    label.replaceAllLiterally("-LRB-", "(").
      replaceAllLiterally("-RRB-", ")")

}
