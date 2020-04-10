package edin.ccg

import java.io.{File, PrintWriter}

import edin.supertagger.{SUPERTAGGER_NAME, SUPERTAGGER_VERSION}

import edin.algorithms.AutomaticResourceClosing.linesFromFile

object MainExtractCCGBank {

  case class CMDargs(
                      ccg_dir : String    = null,
                      out_dir : String    = null
                    )

  def main(args:Array[String]) : Unit = {

    val parser = new scopt.OptionParser[CMDargs](SUPERTAGGER_NAME) {
      head(SUPERTAGGER_NAME, SUPERTAGGER_VERSION.toString)
      opt[String]("ccg_dir").action((x, c) => c.copy(ccg_dir = x)).required()
      opt[String]("out_dir").action((x, c) => c.copy(out_dir = x)).required()
    }

    val chtbRx = """chtb_(.+).fid""".r

    parser.parse(args, CMDargs()) match {
      case Some(cmd_args) =>

        val out_dir = new File(cmd_args.out_dir)
        if(! out_dir.exists())
          out_dir.mkdir()

        val ccgFiles = allCCGFilesSorted(cmd_args.ccg_dir)
        val trainFiles = ccgFiles.filter{f =>
          if(f.getName.endsWith(".auto")){
            val section = f.getParentFile.getName.toInt
            2 <= section && section <= 21
          }else{
            f.getName match {
              case chtbRx(id) => id.toInt >= 2000 && id.toInt <=2980
              case _ => throw new Exception("not a Chinese treebank")
            }
          }
        }
        val devFiles = ccgFiles.filter{f =>
          if(f.getName.endsWith(".auto")){
            val section = f.getParentFile.getName.toInt
            section == 0
          }else{
            f.getName match {
              case chtbRx(id) => id.toInt >= 2981 && id.toInt <=3029
              case _ => throw new Exception("not a Chinese treebank")
            }
          }
        }
        val testFiles = ccgFiles.filter{f =>
          if(f.getName.endsWith(".auto")){
            val section = f.getParentFile.getName.toInt
            section == 23
          }else{
            f.getName match {
              case chtbRx(id) => id.toInt >= 3030 && id.toInt <=3145
              case _ => throw new Exception("not a Chinese treebank")
            }
          }
        }
        val toyFiles = List(trainFiles.head)

        for((typ, files) <- List(
          ("train", trainFiles),
          ("dev", devFiles),
          ("test", testFiles),
          ("toy", toyFiles))
        ){
          val pw_words = new PrintWriter(s"${cmd_args.out_dir}/$typ.words")
          val pw_tags = new PrintWriter(s"${cmd_args.out_dir}/$typ.tags")
          val pw_trees = new PrintWriter(s"${cmd_args.out_dir}/$typ.trees")
          val pw_parg = new PrintWriter(s"${cmd_args.out_dir}/$typ.parg")
          for(f <- files){
            println(s"processing $f")
            val instances = getTags(f)
            for(instance <- instances){
              pw_words.println(instance.map{_._1}.mkString(" "))
              pw_tags.println(instance.map{_._2}.mkString(" "))
            }
            linesFromFile(f).foreach(pw_trees.println)
            val pargFile = f.getAbsolutePath
              .replace(".auto", ".parg")
              .replace(".fid", ".parg")
              .replace("AUTO", "PARG")
            linesFromFile(pargFile).foreach(pw_parg.println)
          }
          pw_words.close()
          pw_tags.close()
          pw_trees.close()
          pw_parg.close()
        }
      case None =>
        System.err.println("You didn't specify all the required arguments")
        System.exit(-1)
    }

  }

  def getTags(f:File) : List[List[(String, String)]] = {
    var instances = List[List[(String, String)]]()
    linesFromFile(f).foreach{ line =>
      if(! line.startsWith("ID="))
        instances ::= getTagsFromLine(line)
    }
    instances.reverse
  }

  def getTagsFromLine(line:String) : List[(String, String)] =
    line.split("\\(<").toList.map{_.trim}.filter(_.startsWith("L")).map{_.split(">").head}.map{ part =>
      part.split(" ").toList match {
        case List(typ, suptag, tag1, tag2, word, predarg) =>
          // L (S[dcl]\NP)/(S[b]\NP) MD MD will (S[dcl]\NP_10)/(S[b]_11\NP_10:B)_11
          (word, suptag)
      }
    }

  def allCCGFilesSorted(f:String) : List[File] =
    allCCGFilesUnordered(new File(f)).sortBy(_.getName)

  private def allCCGFilesUnordered(f:File) : List[File] =
    if(f.isDirectory)
      f.listFiles().toList.flatMap(allCCGFilesUnordered)
    else if(f.getName.endsWith(".auto") || f.getName.endsWith(".fid"))
      List(f)
    else
      List()

}
