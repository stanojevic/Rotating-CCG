package edin.ccg

import java.io.File

import edin.ccg.representation._
import edin.algorithms.evaluation.{FScoreAggregator, PackedScoreAggregator, ScoreAggregator}
import edin.ccg.representation.predarg.DepLink
import edin.ccg.representation.category.Category
import edin.ccg.representation.combinators.Glue
import edin.ccg.representation.tree.{BinaryNode, TreeNode}
import edin.general.DepsVisualizer

object MainEvaluate {

  case class CMDargs(
                      gold_trees      : String         = null,
                      predicted_trees : String         = null,
                      save_images_dir : String         = null,
                    )


  def main(args:Array[String]) : Unit = {
    val parser = new scopt.OptionParser[CMDargs](PROGRAM_NAME) {
      head(PROGRAM_NAME, PROGRAM_VERSION.toString)

      opt[ String      ]( "gold_trees"         ).action((x,c) => c.copy( gold_trees       = x )).required()
      opt[ String      ]( "predicted_trees"    ).action((x,c) => c.copy( predicted_trees  = x )).required()
      opt[ String      ]( "save_images_dir"    ).action((x,c) => c.copy( save_images_dir  = x ))

      help("help").text("prints this usage text")
    }

    parser.parse(args, CMDargs()) match {
      case Some(cmd_args) =>

        val goldTrees = DerivationsLoader.fromFile(cmd_args.gold_trees).toList
        val predTrees = DerivationsLoader.fromFile(cmd_args.predicted_trees).toList

        assert(goldTrees.size == predTrees.size)
        assert(goldTrees.nonEmpty)
        assert((goldTrees zip predTrees).forall{case (x, y) => x.words == y.words})

        var allMainScores = List[Double]()

        val e = newEvaluatorsPackage()
        for((pred, gold) <- predTrees zip goldTrees){
          e.addToCounts(sys = pred, ref = gold)
          allMainScores ::= instanceMainScore(pred, gold)
        }
        println(e.reportString)
        println()
        println(s"MAIN SCORE ${e.mainScore}")

        if(cmd_args.save_images_dir != null && ! new File(cmd_args.save_images_dir).exists())
          new File(cmd_args.save_images_dir).mkdirs()

        println("worst results are on the following 10 sentences:")
        for(((score, sentId), worstRank) <- allMainScores.zipWithIndex.sortBy(_._1).take(10).zipWithIndex){
          println(f"$score%.3f $sentId : "+goldTrees(sentId).words.mkString(" "))
          if(cmd_args.save_images_dir != null){
            val pred = predTrees(sentId)
            val gold = goldTrees(sentId)
            pred.saveVisual(fn=cmd_args.save_images_dir+s"/${worstRank}___pred___$sentId", graphLabel = s"${worstRank}___pred___${sentId}___$score")
            gold.saveVisual(fn=cmd_args.save_images_dir+s"/${worstRank}___gold___$sentId", graphLabel = s"${worstRank}___gold___${sentId}___$score")
            DepsVisualizer.visualizeError(
              words = pred.words,
              tagsSys = pred.leafs.map(_.category.toString),
              tagsRef = pred.leafs.map(_.category.toString),
              depsSys = pred.deps(hockenDeps = true).map(d => (d.headPos, d.depPos, d.arcLabel)),
              depsRef = pred.deps(hockenDeps = true).map(d => (d.headPos, d.depPos, d.arcLabel)),
              fn = cmd_args.save_images_dir+s"/${worstRank}___depsErr___$sentId"
            )
          }
        }

      case None =>
        System.err.println("You didn't specify all the required arguments")
        System.exit(-1)
    }

  }

  def instanceMainScore(pred:TreeNode, gold:TreeNode) : Double = {
    val e = newEvaluatorsPackage()
    e.addToCounts(sys = pred, ref = gold)
    e.mainScore
  }

  def newEvaluatorsPackage() : ScoreAggregator[TreeNode] =
    new PackedScoreAggregator(mainAggName = "labelled dependency", aggregators= List(
        new FScoreAggregator("supertags"                       , typeObfuscator(                   toSupertags                       )),
        new FScoreAggregator("absolute constituency"           , typeObfuscator( rightBrancharize( toAbsoluteConstituency           ))),
        new FScoreAggregator("unlabelled-undirected dependency", typeObfuscator( rightBrancharize( toDependencyUnLabelledUndirected ))),
        new FScoreAggregator("unlabelled-directed dependency"  , typeObfuscator( rightBrancharize( toDependencyUnLabelled           ))),
        new FScoreAggregator("labelled dependency"             , typeObfuscator( rightBrancharize( toDependencyLabelled             ))),
        new FScoreAggregator("long-distance dependency"        , typeObfuscator( rightBrancharize( toLongDistDeps                   ))),
        new GlueScoreAggregator
      )
    )

  class GlueScoreAggregator extends ScoreAggregator[TreeNode] {

    var totalSents = 0
    var gluedSents = 0
    var gluedConsts = 0
    var maxGluedConsts = 0
    def avgGluePerSent: Double = if(totalSents ==0 ) 0.0 else gluedConsts/totalSents

    override def addToCounts(sys: TreeNode, ref: TreeNode): Unit = {
      assert(sys.words == ref.words)
      val gluedCount = sys.allNodes.count{
        case BinaryNode(Glue(), _, _) => true
        case _ => false
      }

      totalSents += 1
      if(gluedCount>0){
        gluedSents += 1
        gluedConsts += gluedCount
        if(gluedCount > maxGluedConsts)
          maxGluedConsts = gluedCount
      }
    }

//    override def reportString: String = {
//      s"glued\nconsts: $gluedConsts\tsents: $gluedSents\tmaxPerSent: $maxGluedConsts\tavgPerSent: $avgGluePerSent"
//    }

    override val name: String = "glued"
    override val mainScoreName: String = "avgPerSent"

    override def exposedScores: List[(String, Double)] = List(
      ("consts", gluedConsts),
      ("sents", gluedSents),
      ("maxPerSent", maxGluedConsts),
      ("avgPerSent", avgGluePerSent)
    )

  }

  private def typeObfuscator[X, Y](f: X=>List[Y]) : X=>List[AnyRef] = { x:X => f(x).map(_.asInstanceOf[AnyRef])}

  private def rightBrancharize[X](f: TreeNode => X) : TreeNode => X = { n:TreeNode =>
    f(n.toRightBranching)
  }

  private def toSupertags(tree:TreeNode) : List[Category] =
    tree.leafs.map(_.category)

  private def toAbsoluteConstituency(tree:TreeNode) : List[(Category, (Int, Int))] =
    tree.allNodes.map(node => (node.category, node.span))

  private def toDependencyUnLabelled(tree:TreeNode) : List[(Int, Int)] =
    tree.deps(hockenDeps = true).map(d => (d.headPos, d.depPos))

  private def toDependencyUnLabelledUndirected(tree:TreeNode) : List[(Int, Int)] =
    tree.deps(hockenDeps = true).map(_.toUnlabelledAndUndirected)

  private def toDependencyLabelled(tree:TreeNode) : List[DepLink] =
    tree.deps(hockenDeps = true)

  private def toLongDistDeps(tree:TreeNode) : List[DepLink] =
    tree.deps(hockenDeps = true).filterNot(_.boundness.isLocal)

}
