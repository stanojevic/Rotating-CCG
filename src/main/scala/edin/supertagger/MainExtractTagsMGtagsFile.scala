package edin.supertagger

import java.io.{File, PrintWriter}

import scala.io.Source
import scala.util.Random

object MainExtractTagsMGtagsFile {

  case class CMDargs(
                      mg_file : String    = null,
                      out_dir : String    = null
                    )

  def main(args:Array[String]) : Unit = {

    val parser = new scopt.OptionParser[CMDargs](SUPERTAGGER_NAME) {
      head(SUPERTAGGER_NAME, SUPERTAGGER_VERSION.toString)
      opt[String]("mg_file").action((x, c) => c.copy(mg_file = x)).required()
      opt[String]("out_dir").action((x, c) => c.copy(out_dir = x)).required()
    }

    parser.parse(args, CMDargs()) match {
      case Some(cmd_args) =>

        val out_dir = new File(cmd_args.out_dir)
        if(! out_dir.exists())
          out_dir.mkdir()

        Random.setSeed(42)
        val data = Random.shuffle( loadData(cmd_args.mg_file) )

        val testSize = 753
        val devSize  = 742

        val testData  = data.take(testSize)
        val devData   = data.slice(testSize, testSize + devSize)
        val trainData = data.drop(testSize+devSize)

        for((typ, instances) <- List(
                                      ("train", trainData),
                                      ("dev", devData),
                                      ("test", testData))
                                    ){
          val pw_words    = new PrintWriter(s"${cmd_args.out_dir}/$typ.words")
          val pw_mg_tags  = new PrintWriter(s"${cmd_args.out_dir}/$typ.mg_tags" )
          val pw_ccg_tags  = new PrintWriter(s"${cmd_args.out_dir}/$typ.ccg_tags" )
          val pw_pos_tags  = new PrintWriter(s"${cmd_args.out_dir}/$typ.pos_tags" )
          println(s"processing $typ")
          for(instance <- instances){
            pw_words.println(instance.map{_._1}.mkString(" "))
            pw_pos_tags.println(instance.map{_._2}.mkString(" "))
            pw_ccg_tags.println(instance.map{_._3}.mkString(" "))
            pw_mg_tags.println(instance.map{_._4}.mkString(" "))
          }
          pw_words.close()
          pw_mg_tags.close()
          pw_ccg_tags.close()
          pw_pos_tags.close()
        }

      case None =>
        System.err.println("You didn't specify all the required arguments")
        System.exit(-1)
    }

  }

  def loadData(f:String) : List[List[(String, String, String, String)]] = {
    var instances = List[List[(String, String, String, String)]]()
    Source.fromFile(f).getLines().foreach{ line =>
      instances ::= getTagsFromLine(line)
    }
    instances.reverse
  }

  def getTagsFromLine(line:String) : List[(String, String, String, String)] = {
    line.split(" +").toList.map{ entry =>
      val fields = entry.split("\\|")
      val nonMGsubfields = fields(1).split("_")
      val word   = fields(0)
      val ccgTag = nonMGsubfields(0)
      val posTag = nonMGsubfields(1)
      val mgTag  = fields(2)
      (word, posTag, ccgTag, mgTag)
    }
  }

}

