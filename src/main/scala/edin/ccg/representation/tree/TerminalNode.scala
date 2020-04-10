package edin.ccg.representation.tree

import edin.ccg.representation.category.Category

final case class TerminalNode(word:String, cat:Category) extends TreeNode {
  override val category: Category = cat

  var posTag : String = _

  var position : Int = -999
  override def span: (Int, Int) = {
    if(position == -999)
      throw new Exception("span unknown")
    (position, position+1)
  }

  override def toString: String = s"'$word'-$category"

}
