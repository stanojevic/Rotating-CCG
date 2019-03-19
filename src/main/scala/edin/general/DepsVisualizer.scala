package edin.general

import java.io.{File, PrintWriter}

object DepsVisualizer {

  final case class DepsDesc(
                             label:String,
                             words : List[String],
                             tags  : List[String],
                             deps  : List[(Int, Int, String, String)],/** (head starting from 0, dep  starting from 0, label, color) */

                             tagsBelow: List[String]=Nil,
                             depsBelow: List[(Int, Int, String, String)]=Nil/** (head starting from 0, dep  starting from 0, label, color) */
                           )

  def visualizeError(
                      words:List[String],
                      tagsSys:List[String],
                      tagsRef:List[String],
                      depsSys:List[(Int, Int, String)],
                      depsRef:List[(Int, Int, String)],
                      fn:String
                    ) : Unit = {
    val depsRefSet = depsRef.toSet
    val depsSysSet = depsSys.toSet
    val dd = DepsDesc(
      label = "errors",
      words = words,
      tags = tagsSys,
      deps = depsSys.map{case d@(hw, dw, l) =>
        if(depsRefSet contains d)
          (hw, dw, l, "black")
        else
          (hw, dw, l, "red")
      },
      tagsBelow = tagsRef,
      depsBelow = depsRef.filterNot(depsSysSet.contains).map(d => (d._1, d._2, d._3, "red"))
    )
    visualize(dd, fn)
  }

  def visualize(dd:DepsDesc) : Unit = {
    val file = File.createTempFile(s"deps_${dd.label.replace(" ", "_")}_", ".pdf", null)
    val fStr = file.getAbsolutePath
    val headFStr = fStr.substring(0, fStr.length-4)
    file.deleteOnExit()
    visualize(dd, headFStr)
    openViewer(fStr)
  }

  def visualize(dd:DepsDesc, file:String) : Unit = {
    val pw = new PrintWriter(file+".tex")
    pw.println(latexWhole(dd))
    pw.close()

    val dir = new File(file).toPath.getParent.toFile
    val cmd =s"pdflatex $file"
    val proc = Runtime.getRuntime.exec(cmd, null, dir)
    proc.waitFor()
    new File(s"$file.log").delete()
    new File(s"$file.aux").delete()
    new File(s"$file.tex").delete()
  }

  private def openViewer(file:String) : Unit = {
    val filename = new File(file).toPath
    val xdgCmd = System.getProperty("os.name") match {
      case "Linux" => s"nohup xdg-open $filename"
      case _       => s"open $filename"
    }
    Runtime.getRuntime.exec(xdgCmd)
    val seconds = 5
    Thread.sleep(seconds*1000)
  }

  private def latexWhole(d:DepsDesc) : String =
    latexHeader + latexBody(d) + latexFooter

  private def latexHeader : String =
    "\\documentclass{standalone}\n\n\\usepackage{tikz-dependency}\n\n\n\\begin{document}\n\\begin{dependency}[arc edge,scale=1.7, edge style={very thick}]\n" // +
    // "\\depstyle{outer bubble}{draw=gray!80, minimum height=26pt, rounded corners=10pt,\ninner sep=5pt, top color=white, bottom color=gray!40}\n"

  private def latexFooter : String =
    "\\end{dependency}\n\\end{document}\n"

  private def latexBody(dd:DepsDesc) : String = {
    var out = "\\begin{deptext}[column sep=1em]\n"
    if(dd.tags != null && dd.tags.nonEmpty)
      out += dd.tags.map(escape).mkString(" \\& ") + " \\\\\n"
    out += dd.words.map(escape).mkString(" \\& ") + " \\\\\n"
    if(dd.tagsBelow != null && dd.tagsBelow.nonEmpty)
      out += dd.tagsBelow.map(escape).mkString(" \\& ") + " \\\\\n"
    out += "\\end{deptext}\n"
    // out += (1 to words.size).map(i => s"\\wordgroup[outer bubble]{1}{$i}{$i}{name}\n").mkString("")
    out += dd.deps.map{case (h, d, label, color) => s"\\depedge[label style={scale=1.2, text=$color}, arc angle=60, edge style = {$color}]{${h+1}}{${d+1}}{$label}\n"}.mkString("")
    out += dd.depsBelow.map{case (h, d, label, color) => s"\\depedge[scale=1.2, edge below, label style={below, scale=1.2, text=$color}, arc angle=60, edge style = {$color}]{${h+1}}{${d+1}}{$label}\n"}.mkString("")
    out
  }

  private def escape(s:String) : String =
    s.replace("\\", "{\\textbackslash}")
     .replace("$", "\\$")
     .replace("}", "\\}")
     .replace("{", "\\{")
     .replace("\\{\\textbackslash\\}", "{\\textbackslash}")

  def main(args:Array[String]) : Unit = {
    val deps = List(
      (1, 2, "1:B", "blue"),
      (0, 2, "0:L", "red"),
      (1, 0, "3", "black")
    )
    val words = List("Hello", "there", "Man")
    val tags = List("N", "V", "N\\V")
    val dd =
      DepsDesc(
        label="",
        words=words,
        tags=tags,
        deps=deps,
        depsBelow=deps
      )
    System.err.println(latexWhole(dd))
    visualize(dd)
//    visualize(
//      dd,
//      "proba"
//    )
  }

}
