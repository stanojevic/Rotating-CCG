package edin.ccg.a_star

import java.io.PrintWriter
import edin.general.Global.projectDir

import edin.ccg.representation.DerivationsLoader
import edin.ccg.representation.category.Category
import edin.ccg.representation.combinators.Combinator
import edin.ccg.representation.tree.{BinaryNode, UnaryNode}

import edin.algorithms.AutomaticResourceClosing.linesFromFile
import scala.collection.mutable.{Map => MutMap, Set => MutSet}

object ExtractObservedRules {

  val PROGRAM_NAME = "A* CCG PARSER"
  val PROGRAM_VERSION = 0.1

  private val rulesDir = s"$projectDir/src/main/scala/edin/ccg/a_star"

  case class CMDargs(
                      ccg_file       : String = null,
                      output_file    : String = null,
                      normal_form    : String = "right",
                      language       : String = null
                    )

  def main(args:Array[String]) : Unit = {

    val parser = new scopt.OptionParser[CMDargs](PROGRAM_NAME) {
      head(PROGRAM_NAME, PROGRAM_VERSION.toString)
      opt[ String   ]( "ccg_file"           ).action((x,c) => c.copy( ccg_file          = x )).required()
      opt[ String   ]( "output_file"        ).action((x,c) => c.copy( output_file       = x ))
      opt[ String   ]( "normal_form"        ).action((x,c) => c.copy( normal_form       = x ))
      opt[ String   ]( "language"           ).action((x,c) => c.copy( language          = x )).required()
      help("help").text("prints this usage text")
    }

    parser.parse(args, CMDargs()) match {
      case Some(cmd_args) =>

        Combinator.setLanguage(cmd_args.language, null)

        val trees = DerivationsLoader.fromFile(cmd_args.ccg_file).map{tree =>
          cmd_args.normal_form match {
            case "right"     => tree.toRightBranching
            case "left"      => tree.toLeftBranching
            case "revealing" => tree.toRevealingBranching
          }
        }

        val binaryRules = MutMap[(Category, Category, Category), Int]()
        val unaryRules  = MutMap[(Category, Category), Int]()

        for(tree <- trees){
          for(parent@UnaryNode(_, child) <- tree.allNodes){
            val ruleName = (parent.category, child.category)
            unaryRules(ruleName) = unaryRules.getOrElse(ruleName, 0)+1
          }
          for(parent@BinaryNode(_, l, r) <- tree.allNodes){
            val ruleName = (parent.category, l.category, r.category)
            binaryRules(ruleName) = binaryRules.getOrElse(ruleName, 0)+1
          }
        }

        val outFile = Option(cmd_args.output_file).getOrElse(s"$rulesDir/Rules${cmd_args.language}.txt")
        System.err.println(s"Writing to $outFile")
        val pw = new PrintWriter(outFile)
        (unaryRules.toList ++ binaryRules.toList).sortBy(-_._2).foreach{
          case ((parentCat, childCat), count) =>
            pw.println(s"$count $parentCat $childCat")
          case ((parentCat, leftCat, rightCat), count) =>
            pw.println(s"$count $parentCat $leftCat $rightCat")
          case _ =>
            sys.error("unexpected")
        }
        pw.close()

        System.err.println("Done")

      case None =>
        System.err.println("You didn't specify all the required arguments")
        System.exit(-1)
    }
  }

  def loadDefaultObservedRules() : (MutSet[(Category, Category)], MutSet[(Category, Category, Category)]) = Combinator.language match {
    case "Chinese"         => loadObservedRules(rulesDir+"/RulesChinese.txt", 1)
    case "English"         => loadObservedRules(rulesDir+"/RulesEnglish.txt", 1)
    case "English_EasyCCG" => loadObservedRules(rulesDir+"/RulesEnglish_EasyCCG.txt", 1)
    case "English_CnC"     => loadObservedRules(rulesDir+"/RulesEnglish_CnC.txt", 1)
    case _ => throw new Exception("nor ules for language "+Combinator.language)
  }

  def loadObservedRules(fn:String, minCount:Int) : (MutSet[(Category, Category)], MutSet[(Category, Category, Category)]) = {
    val unaries = MutSet[(Category, Category)]()
    val binaries = MutSet[(Category, Category, Category)]()
    linesFromFile(fn).map(_.split(" ").toList).foreach{
      case List(countS, parentCatS, childCatS) if countS.toInt >= minCount =>
        unaries += ((Category(parentCatS), Category(childCatS)))
      case List(countS, parentCatS, leftCatS, rightCatS) if countS.toInt >= minCount =>
        binaries += ((Category(parentCatS), Category(leftCatS), Category(rightCatS)))
      case _ =>
    }
    (unaries, binaries)
  }

}
