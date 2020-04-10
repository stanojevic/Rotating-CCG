package edin.general

import java.io.File
import java.util

import jep.{Jep, JepConfig, NDArray}
import scala.collection.JavaConverters._

object Python {

  private val TMP = "tmp_var_name"

  def addToPath(dir:String) : Unit = {
    importt("sys")
    set(TMP, dir)
    exec(s"sys.path.insert(0, $TMP)")
  }

  def runPythonFile(fn:String) : Unit = {
    addToPath(new File(fn).getParent)
    jep.runScript(fn)
  }

  def activeVars() : List[String] =
    getListOfStrings("dir()").
      filterNot(x => x.startsWith("__") && x.endsWith("__")).
      filterNot(x => x=="jep").
      filterNot(x => imported contains x)

  def delActiveVars() : Unit = activeVars().foreach(delVar)

  def delVar(varr:String) : Unit = exec(s"del $varr")

  def exec(cmd:String*) : Unit = jep.eval(cmd.mkString("\n"))

  def set(varr:String, vall:Any) : Unit = jep.set(varr, vall)

  def setList(varr:String, vall:Seq[_], freeTMP:String=TMP) : Unit = {
    exec(s"$varr = []" )
    for(x <- vall){
      x match {
        case a:Seq[_] => setList(freeTMP, a, freeTMP+"x")
        case _        => set(freeTMP, x)
      }
      exec(s"$varr.append($freeTMP)")
      delVar(freeTMP)
    }
  }

  def getListOfStrings(varr:String) : List[String] = jep.getValue(varr).asInstanceOf[util.ArrayList[String]].asScala.toList

  def getListOfLongs(varr:String) : List[Long] = jep.getValue(varr).asInstanceOf[util.ArrayList[Long]].asScala.toList

  def getListOfDoubles(varr:String) : List[Double] = jep.getValue(varr).asInstanceOf[util.ArrayList[Double]].asScala.toList

  def getListOfNumPyArrays(varr:String) : List[Array[Float]] = jep.getValue(varr).asInstanceOf[util.ArrayList[NDArray[Array[Float]]]].asScala.toList.map(_.getData)

  def getLong(varr:String) : Long = jep.getValue(varr).asInstanceOf[Long]

  def getDouble(varr:String) : Double = jep.getValue(varr).asInstanceOf[Double]

  def getString(varr:String) : String = jep.getValue(varr).asInstanceOf[String]

  def getNumPyDouble(varr:String) : Double = jep.getValue(s"np.asscalar($varr)").asInstanceOf[Double]

  def setNumPyArray(varr:String, vall:Array[Float]) : Unit = jep.set(varr, new NDArray[Array[Float]](vall, vall.length))

  def getNumPyArray(cmd:String) : Array[Float] = jep.getValue(cmd).asInstanceOf[NDArray[Array[Float]]].getData

  private var imported = Set[String]()
  def importt(module:String) : Unit = {
    imported += module
    exec(s"import $module")
  }
  def importtAs(module:String, as:String) : Unit = {
    imported += as
    exec(s"import $module as $as")
  }
  def importt(from:String, module:String) : Unit = {
    imported += module
    exec(s"from $from import $module")
  }
  def importtAs(from:String, module:String, as:String) : Unit = {
    imported += as
    exec(s"from $from import $module as $as")
  }
  def importNumPy() : Unit = importtAs("numpy", "np")

  val apply : (String*)=>Unit = exec

  def jep: Jep = {
    if(jepInstance == null){
      val JEP_DIR = Global.projectDir+"/dependencies"
      if(! new File(JEP_DIR).exists())
        throw new Exception(s"jep dir $JEP_DIR not found!")
      val jepConfig = new JepConfig()
        .setInteractive(false)
        .setIncludePath(JEP_DIR)
        .setClassLoader(null)
        .setClassEnquirer(null)
      jepInstance = new Jep(jepConfig)
      importt("sys")
      exec("sys.argv = ['program_name'] ")
    }
    jepInstance
  }

  def closeJep() : Unit = {
    if(jepInstance != null)
      jepInstance.close()
  }

  override protected def finalize(): Unit = { closeJep() }

  private var jepInstance:Jep = _

  def main(args:Array[String]) : Unit = {

    val bertModelDir = "/home/milos/Desktop/BERT_playground/multi_cased_L-12_H-768_A-12"

    // Loading the model
    val myExtractScript  = Global.projectDir+"/scripts/embedding/bert_extract.py"
    val bertCodeDir = Global.projectDir+"/scripts/embedding/bert"
    Python.addToPath(bertCodeDir)
    Python.runPythonFile(myExtractScript)
    Python.set("bert_model_dir", bertModelDir)
    Python.exec("tokenizer = load_tokenizer(bert_model_dir)")
    Python.exec("estimator = load_BERT_estimator(bert_model_dir)")

    // Defining embeddings
    val input = List(
      "This is a sentence that contains some input about some city somewhere",
      "This is a sentence that contains some input about some city somewhere".reverse
    )
    // Computing embeddings
    Python.setList("input", input)
    Python.exec("res = embed_sent_pairs(estimator, tokenizer, input)")

    // Normalization
    Python.exec("res = list(map(normalize_embedings_dict, res))")

    // Reading out
    val all_embs = input.indices.map( i => Python.getListOfNumPyArrays(s"res[$i]['A']") )

    // each embedding is of size 3072

    println("DONE")
  }

}
