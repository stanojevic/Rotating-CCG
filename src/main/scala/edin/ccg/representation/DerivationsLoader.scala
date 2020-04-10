package edin.ccg.representation

import java.io.File

import edin.ccg.representation.category.Category
import edin.ccg.representation.combinators._
import edin.ccg.representation.tree._
import edin.algorithms.AutomaticResourceClosing._

object DerivationsLoader {

  def fromFile(trees_file:String) : Iterator[TreeNode] =
    fromFile(new File(trees_file))

  def assignSpans(root:TreeNode) : Unit = {
    root.leafs.zipWithIndex.foreach{case (n, i) => n.position = i}
    root.span
  }

  def fromFile(trees_file:File) : Iterator[TreeNode] =
    for{
      line <- linesFromFile(trees_file)
      if line startsWith "("
      tree = fromString(line)
      _    = assignSpans(tree)
    }
      yield tree

  def fromString(s:String) : TreeNode = {
    val tokens = tokenize(s)
    val (node, consumedTokensCount, _) = processTokens(tokens, 0, 0)
    require(consumedTokensCount == tokens.length)
    assignSpans(node)
    node
  }

  private def processTokens(tokens:Array[String], i:Int, j:Int) : (TreeNode, Int, Int) = {
    var next_token = i
    var next_word = j
    assert(tokens(next_token) == "("); next_token += 1
    assert(tokens(next_token) == "<"); next_token += 1
    tokens(next_token) match {
      case "T" =>
        next_token += 1
        val category:Category = Category.fromString(tokens(next_token)); next_token += 1
        val head_index = tokens(next_token); next_token += 1
        val head_left = head_index == "0"
        val children_count = tokens(next_token); next_token += 1
        assert(tokens(next_token) == ">"); next_token += 1

        var children = List[TreeNode]()

        while(tokens(next_token) == "(") {
          val (child, nextyTokeny, nextyWordy) = processTokens(tokens, next_token, next_word)
          next_token = nextyTokeny
          next_word = nextyWordy
          children ::= child
        }

        children = children.reverse
        assert(tokens(next_token)==")")
        assert(children.size <= 2)

        val node = children match {
          case List(x, y) =>
            val combinator = recognizeCCGcombinatorBinary(x, y, head_left, category)
            BinaryNode(combinator, x, y)
          case List(x) =>
            val combinator = recognizeCCGcombinatorUnary(x.category, category)
            val p = UnaryNode(combinator, x)
            if(p.category != x.category) // this is needed because of the error in the treebank
              p
            else
              x
          case _ =>
            throw new Exception("Input parsing error")
        }

        next_token += 1
        (node, next_token, next_word)
      case "L" =>
        next_token += 1
        val category = Category.fromString(tokens(next_token)); next_token += 1
        val modified_pos = tokens(next_token); next_token += 1
        val original_pos = tokens(next_token); next_token += 1
        val word = tokens(next_token); next_token += 1
        val pred_arg_CCG = tokens(next_token); next_token += 1
        assert(tokens(next_token) == ">"); next_token += 1
        assert(tokens(next_token) == ")"); next_token += 1
        val node = TerminalNode(word, category) // (next_word)
        node.posTag = original_pos // remove?
        next_word += 1
        (node, next_token, next_word)
      case _ =>
        throw new Exception("unkown node type")
    }
  }


  private def tokenize(s:String) : Array[String] = s.
      replaceAllLiterally("<"," < ").
      replaceAllLiterally(">"," > ").
      trim().
      split(" +")

  private def recognizeCCGcombinatorUnary(childCat:Category, parentCat:Category) : CombinatorUnary =
    CombinatorUnary.allPredefined
                   .find(c => c.canApply(childCat) && c(childCat) == parentCat)
                   .getOrElse(TypeChangeUnary(childCat, parentCat))

  private def recognizeCCGcombinatorBinary(left:TreeNode, right:TreeNode, headLeft:Boolean, category:Category) : CombinatorBinary =
    if(category.toString == "GLUE"){
      Glue()
    }else{
      val l = left.category
      val r = right.category
      CombinatorBinary.allPredefined
        .find(c => c.canApply(l, r) && c(l, r) == category)
        .getOrElse(TypeChangeBinary(l, r, category))
    }


}


