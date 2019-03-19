package edin.general


import java.io.{FileInputStream, PrintWriter}

import org.yaml.snakeyaml.Yaml
import java.util.{List => JavaList, Map => JavaMap}
import scala.collection.JavaConverters._

object YamlConfig{

  def fromFile(file:String) : YamlConfig =
    new YamlConfig(HelperFunctions.parseConfFile(file))

}

class YamlConfig(val conf:AnyRef){

  def contains(key:String) : Boolean = conf.asInstanceOf[Map[String, AnyRef]].contains(key)

  def deepSearch(key:String) : List[YamlConfig] = conf match {
    case x:Map[_, _] =>
      val c = x.asInstanceOf[Map[String, AnyRef]]
      val begining = if(this.contains(key))
        this(key)::Nil
      else
        Nil
      begining ++ c.values.flatMap(new YamlConfig(_).deepSearch(key))
    case x:List[_] =>
      val c = x.asInstanceOf[List[AnyRef]]
      c.flatMap(new YamlConfig(_).deepSearch(key))
    case c:String =>
      if(c == key)
        this::Nil
      else
        Nil
    case _ =>
      Nil
  }

  def getOrElse(key:String, default:Boolean) : Boolean =
    if(contains(key))
      this(key).bool
    else
      default

  def getOptionalListFloat(key:String) : List[Float] =
    if(contains(key))
      this(key).floatList
    else
      List()

  def getOptionalListInt(key:String) : List[Int] =
    if(contains(key))
      this(key).intList
    else
      List()

  def getOrElse(key:String, default:Float) : Float =
    if(contains(key))
      this(key).float
    else
      default

  def getOrElse(key:String, default:Int) : Int =
    if(contains(key))
      this(key).int
    else
      default

  def getOrElse[T](key:String, default : =>T) : T =
    if(conf.asInstanceOf[Map[String, AnyRef]].contains(key))
      conf.asInstanceOf[Map[String, AnyRef]](key).asInstanceOf[T]
    else
      default

  def apply(index:Int) : YamlConfig =
    new YamlConfig(conf.asInstanceOf[List[AnyRef]](index))

  def apply(key:String): YamlConfig =
    new YamlConfig(conf.asInstanceOf[Map[String, AnyRef]](key))

  def int:Int = toInt(conf)

  def float:Float = toFloat(conf)

  def bool : Boolean = conf.asInstanceOf[Boolean]

  def any2int[T]: Any2Int[T] = conf.asInstanceOf[Any2Int[T]]

  def str:String = conf.asInstanceOf[String]

  def strList : List[String] = conf.asInstanceOf[List[String]]

  def floatList : List[Float] =
    conf.asInstanceOf[List[AnyRef]].map(toFloat)

  def list : List[YamlConfig] =
    conf.asInstanceOf[List[AnyRef]].map{new YamlConfig(_)}

  def intList : List[Int] =
    conf.asInstanceOf[List[AnyRef]].map(this.toInt(_))

  def mapAllTerminals(map:Map[String, AnyRef]) : YamlConfig =
    new YamlConfig(HelperFunctions.mapAllTerminals(conf, map))

  def save(file:String) : Unit =
    HelperFunctions.saveConfFile(conf.asInstanceOf[Map[String, Object]], file)

  private def toInt(x:AnyRef) : Int =
    if(x.isInstanceOf[Int])
      x.asInstanceOf[Int]
//    else if(x.isInstanceOf[String])
//      Integer.parseInt(x.asInstanceOf[String])
    else
      x.asInstanceOf[List[Int]].sum

  private def toFloat(x:AnyRef) : Float =
    if(x.isInstanceOf[Float])
      x.asInstanceOf[Float]
    else
      x.asInstanceOf[Double].toFloat

}

private object HelperFunctions{

  def mapAllTerminals(conf:AnyRef, map:Map[String, AnyRef]) : AnyRef = {
    map.toList.foldLeft(conf){case (c, (s, x)) => mapTerminal(c, s, x)}
  }

  private def mapTerminal(conf:AnyRef, s:String, x:AnyRef) : AnyRef = {
    if(conf.isInstanceOf[Map[_, _]]){
      conf.asInstanceOf[Map[String, AnyRef]].mapValues(mapTerminal(_, s, x))
    }else if(conf.isInstanceOf[List[_]]){
      conf.asInstanceOf[List[AnyRef]].map{mapTerminal(_, s, x)}
    }else if(conf == s){
      x
    }else{
      conf
    }
  }

  def parseConfFile(confFile:String) : Map[String, Object] = {
    val yaml = new Yaml()
    val stream = new FileInputStream(confFile)
    val configInJava = yaml.load(stream)
    deepScalatize(configInJava).asInstanceOf[Map[String, Object]]
  }

  def saveConfFile(conf:Map[String, Object], file:String) : Unit = {
    val yaml = new Yaml()
    val fh = new PrintWriter(file)
    yaml.dump(deepJavatize(conf), fh)
    fh.close()
  }

  private def deepScalatize(x:Any) : Any = x match {
    case aList : JavaList[_] => aList.asScala.map{deepScalatize(_)}.toList
    case aMap  : JavaMap[_, _] => aMap.asScala.mapValues{deepScalatize(_)}.toMap
    case _ => x
  }

  private def deepJavatize(x:Any) : Any = x match{
    case aList: List[_] => seqAsJavaList(aList.map{deepJavatize(_)})
    case aMap : Map[_,_]  => mapAsJavaMap(aMap.map{case (x,y) => (x,deepJavatize(y))})
    case _ => x
  }

}

