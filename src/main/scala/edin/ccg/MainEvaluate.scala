package edin.ccg

import java.io.File

import edin.ccg.representation._
import edin.algorithms.evaluation.{FScoreAggregator, PackedScoreAggregator, ScoreAggregator}
import edin.ccg.representation.predarg.DepLink
import edin.ccg.representation.category.Category
import edin.ccg.representation.combinators.{Combinator, Glue}
import edin.ccg.representation.tree.{BinaryNode, TreeNode}

object MainEvaluate {

  case class CMDargs(
                      gold_trees      : String         = null,
                      predicted_trees : String         = null,
                      language        : String         = "English",
                      save_images_dir : String         = null,
                    )

  def main(args:Array[String]) : Unit = {
    val parser = new scopt.OptionParser[CMDargs](PROGRAM_NAME) {
      head(PROGRAM_NAME, PROGRAM_VERSION.toString)

      opt[ String ]( "gold_trees"         ).action((x,c) => c.copy( gold_trees       = x )).required()
      opt[ String ]( "predicted_trees"    ).action((x,c) => c.copy( predicted_trees  = x )).required()
      opt[ String ]( "save_images_dir"    ).action((x,c) => c.copy( save_images_dir  = x ))
      opt[ String ]( "language"           ).action((x,c) => c.copy( language         = x )).required()

      help("help").text("prints this usage text")
    }

    parser.parse(args, CMDargs()) match {
      case Some(cmd_args) =>

        val goldTrees = DerivationsLoader.fromFile(cmd_args.gold_trees).toList
        val predTrees = DerivationsLoader.fromFile(cmd_args.predicted_trees).toList

        Combinator.setLanguage(cmd_args.language, null)

        assert(goldTrees.size == predTrees.size, s"input files contain different number of trees ${goldTrees.size} and ${predTrees.size}")
        assert(goldTrees.nonEmpty, "gold trees file is empty")
        val badTrees = (goldTrees zip predTrees).filterNot{case (x, y) => x.words == y.words}
        if(badTrees.nonEmpty){
          System.err.println(s"There are ${badTrees.size} trees with wrong yield")
          System.err.println(s"example gold: ${badTrees.head._1.words}")
          System.err.println(s"example pred: ${badTrees.head._2.words}")
          sys.exit(-1)
        }
        assert((goldTrees zip predTrees).forall{case (x, y) => x.words == y.words})

        var allMainScores = List[Double]()

        println("*"*60)
        println("*** NOW RESULTS ONLY ON ALL (PARSED AND GLUED) SENTENCES ***")
        println("*"*60)
        val e = newEvaluatorsPackage()
        for((pred, gold) <- predTrees zip goldTrees){
          e.addToCounts(sys = pred, ref = gold)
          allMainScores ::= instanceMainScore(pred, gold)
        }
        println(e.reportString)
        println(s"MAIN SCORE ${e.mainScore}")
        println()

        println("*"*57)
        println("*** NOW RESULTS ONLY ON SUCCESSFULLY PARSED SENTENCES ***")
        println("*"*57)
        val e2 = newEvaluatorsPackage()
        (predTrees zip goldTrees).foreach{
          case (BinaryNode(Glue(), _, _), _   ) => // skip sentences with glued consts
          case (pred                    , gold) => e2.addToCounts(sys = pred, ref = gold)
        }
        println(e2.reportString)
        println(s"MAIN SCORE ${e2.mainScore}")
        println()

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
//            edin.general.DepsVisualizer.visualizeError(
//              words = pred.words,
//              tagsSys = pred.leafs.map(_.category.toString),
//              tagsRef = pred.leafs.map(_.category.toString),
//              depsSys = pred.deps(hockenDeps = true).map(d => (d.headPos, d.depPos, d.arcLabel)),
//              depsRef = pred.deps(hockenDeps = true).map(d => (d.headPos, d.depPos, d.arcLabel)),
//              fn = cmd_args.save_images_dir+s"/${worstRank}___depsErr___$sentId"
//            )
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


  def computeLabelledDepsFscore(gold:TreeNode, pred:TreeNode) : Double = {
    val scorer = new FScoreAggregator[TreeNode]("labelled dependency", typeObfuscator( rightBrancharize( toDependencyLabelled )))
    scorer.addToCounts(sys = pred, ref = gold)
    scorer.mainScore
  }

  def computeSupertagFscore(gold:TreeNode, pred:TreeNode) : Double = {
    val scorer = new FScoreAggregator[TreeNode]("labelled dependency", typeObfuscator(                   toSupertags            ))
    scorer.addToCounts(sys = pred, ref = gold)
    scorer.mainScore
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

    override val name: String = "glued"
    override val mainScoreName: String = "avgPerSent"

    override def exposedScores: List[(String, Double)] = List(
      ("glued_consts", gluedConsts),
      ("glued_sents", gluedSents),
      ("total_sents", totalSents),
      ("max_consts_glued_per_sent", maxGluedConsts),
      ("avg_consts_glued_per_sent", avgGluePerSent)
    )

  }

  private def typeObfuscator[X, Y](f: X        => List[Y]) : X        => List[AnyRef] = { x:X        => f(x).map(_.asInstanceOf[AnyRef]) }

  private def rightBrancharize[X] (f: TreeNode => X      ) : TreeNode => X            = { n:TreeNode => f(n.toRightBranching)            }

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
