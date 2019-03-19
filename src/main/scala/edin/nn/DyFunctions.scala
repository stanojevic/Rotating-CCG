package edin.nn

import edu.cmu.dynet._
import edin.algorithms.BestKElements._

import scala.language.implicitConversions


object DyFunctions{

  // @inline def argmaxWithScores(es:Seq[Float], k:Int):List[(Int, Float)] = edin.algorithms.BestKElements.extractBestK(es.zipWithIndex.map{_.swap}, k)
  @inline def argmaxWithScores(es:Seq[Float], k:Int):List[(Int, Float)] = es.zipWithIndex.map{_.swap}.bestKBy(k)(_._2)
  @inline def argmaxWithScores(es:Expression, k:Int):List[(Int, Float)] = argmaxWithScores(es.toSeq, k)
  @inline def argmax(es:Seq[Float], k:Int):List[Int] = argmaxWithScores(es, k).map(_._1)
  @inline def argmax(es:Expression, k:Int):List[Int] = argmax(es.toSeq, k)
  @inline def argmax(es:Seq[Float]):Int = argmax(es, 1).head
  @inline def argmax(es:Expression):Int = argmax(es.toSeq, 1).head

  @inline def argmaxBetaWithScores(es:Expression, beta:Float):List[(Int, Float)] = argmaxBetaWithScores(es.toSeq, beta)
  @inline def argmaxBetaWithScores(es:Seq[Float], beta:Float):List[(Int, Float)] = {
    val bestLogProb:Float = es.max
    val treshold = bestLogProb + math.log(1-beta)
    es.zipWithIndex.filter(_._1>=treshold).sortBy(-_._1).toList.map{_.swap}
  }

  @inline def zeros                  : Dim                      => Expression    = Expression.zeros
  @inline def ones                   : Dim                      => Expression    = Expression.ones
  @inline def dotProduct             : (Expression, Expression) => Expression    = Expression.dotProduct
  @inline def cmult                  : (Expression, Expression) => Expression    = Expression.cmult
  @inline def cdiv                   : (Expression, Expression) => Expression    = Expression.cdiv
  @inline def exp                    : Expression               => Expression    = Expression.exp
  @inline def log                    : Expression               => Expression    = Expression.log
  @inline def sumElems               : Expression               => Expression    = Expression.sumElems
  @inline def tanh                   : Expression               => Expression    = Expression.tanh
  @inline def sigmoid                : Expression               => Expression    = Expression.logistic
  @inline def logistic               : Expression               => Expression    = Expression.logistic
  @inline def transpose              : Expression               => Expression    = Expression.transpose
  @inline def softmax                : Expression               => Expression    = Expression.softmax(_)
  @inline def logSoftmax             : Expression               => Expression    = Expression.logSoftmax
  @inline def scalar(x:Double)       : Expression                                = Expression.input(x.toFloat)
  @inline def vector(x:Array[Float]) : Expression = {
    val e = new FloatVector(x)
    val exp = Expression.input(x.length, e)
    DynetSetup.safeReference(x)
    DynetSetup.safeReference(exp)
    exp
  }
  @inline def parameter(x:Parameter) : Expression = {
    val exp = Expression.parameter(x)
    DynetSetup.safeReference(exp)
    exp
  }
  @inline def concat              (es:Expression*       ) : Expression        = Expression concatenate es
  @inline def concatWithNull      (es:Expression*       ) : Expression        = Expression.concatenate(es.filter(_!=null))
  @inline def concatSeq           (es:Seq[Expression]   ) : Expression        = Expression.concatenate(es)
  @inline def concatSeqWithNull   (es:Seq[Expression]   ) : Expression        = Expression.concatenate(es.filter(_!=null))
  @inline def logSumExp           (es:Seq[Expression]   ) : Expression        = Expression.logSumExp(es)
  @inline def max                 (es:Seq[Expression]   ) : Expression        = Expression.max(es)
  @inline def esum                (es:Seq[Expression]   ) : Expression        = Expression.sum(es)
  @inline def averageLogSoftmaxes (es:Seq[Expression]   ) : Expression        = logSumExp(es) - log(es.size)
  @inline def pow                 (e:Expression, p:Float) : Expression        = Expression.pow(e, p)

  private var allDropoutEnabled             =  false
  @inline def dropoutIsEnabled    : Boolean =  allDropoutEnabled
  @inline def disableAllDropout() : Unit    = {allDropoutEnabled = false}
  @inline def enableAllDropout()  : Unit    = {allDropoutEnabled = true }
  @inline def dropout(x:Expression, d:Float) : Expression = {
    if(this.dropoutIsEnabled)
      Expression.dropout(x, d)
    else
      x
  }

  @inline def expandVertically(e:Expression, i:Int) : Expression = {
    val es:Seq[Expression] = for(_ <- 0 until i) yield e
    concatSeq(es)
  }
  @inline def expandHorizontally(e:Expression, i:Int) : Expression = {
    expandVertically(e.T, i).T
  }

  @inline implicit def paramList2exprList(l:List[Parameter]) : List[Expression] = l.map(parameter)
  @inline implicit def paramOpt2exprOpt(l:Option[Parameter]) : Option[Expression] = l.map(parameter)
  @inline implicit def double2expr(x:Double) : Expression = Expression.input(x.toFloat)
  @inline implicit def int2expr(x:Int) : Expression = Expression.input(x.toFloat)
  @inline implicit def float2expr(x:Float) : Expression = Expression.input(x)
  @inline implicit def param2expr(x:Parameter) : Expression = parameter(x)
  @inline implicit def int2Dim(x:Int) : Dim = Dim(x)
  @inline implicit def tupleTwo2Dim(x:(Int, Int)) : Dim = Dim(x._1, x._2)
  @inline implicit def tupleThree2Dim(x:(Int, Int, Int)) : Dim = Dim(x._1, x._2, x._3)

  def trainerFactory(typ:String, lr:Float, clipping: Boolean)(implicit model:ParameterCollection) : Trainer = {
    val trainer = typ match{
      case "Adam" => new AdamTrainer(model, learningRate = lr)
      case "SGD" => new SimpleSGDTrainer(model, learningRate = lr)
    }
    if(clipping)
      trainer.clipGradients()
    trainer
  }

  def activationFactory(activationName:String) : Expression => Expression =
    activationName.toLowerCase match {
      case "tanh" => Expression.tanh
      case "sigmoid" => Expression.logistic
      case "logistic" => Expression.logistic
      case "relu" => Expression.rectify
      case "linear" => identity
      case "nothing" => identity
      case "logsoftmax" => Expression.logSoftmax
      case "log_softmax" => Expression.logSoftmax
      case "log-softmax" => Expression.logSoftmax
    }

  implicit class RichLookupParameter(x:LookupParameter){
    def apply(i:Int):Expression = Expression.lookup(x, i)
  }

  implicit class RichExpression(x:Expression) {

    def toFloat: Float = x.value().toFloat
    def toDouble: Double = x.value().toFloat.toDouble
    def toSeq: Seq[Float] = x.value().toSeq
    def toArray: Array[Float] = this.toSeq.toArray
    def toList: List[Float] = this.toSeq.toList
    def toStr: String = this.str(true)

    def /(y:Expression) : Expression =
      x * pow(y, -1)

    def str(withVals: Boolean): String = {
      if (this.isScalar) {
        val typ = "Scalar"
        if (withVals) {
          val vals = this.toFloat.toString
          s"$typ[$vals]"
        } else {
          typ
        }
      } else {
        val typ = if (this.isVector) "Vector" else if (this.isMatrix) "Matrix" else "Tensor"
        val dimsStr = this.dims.mkString(", ")
        if (withVals) {
          val vals = this.toList.mkString(", ")
          s"$typ($dimsStr)[$vals]"
        } else {
          s"$typ($dimsStr)"
        }
      }
    }

    def dims : List[Long] = {
      val d:Dim = x.dim()
      val z = for(i <- 0L until d.size) yield d.get(i)
      cleanTrailingOnes(z.toList)
    }
    private def dimsBiggerThanOne: Int = this.dims.count(_ > 1)
    def isScalar: Boolean = this.dimsBiggerThanOne == 0
    def isVector: Boolean = this.dimsBiggerThanOne == 1
    def isMatrix: Boolean = this.dimsBiggerThanOne == 2
    def isTensor: Boolean = this.dimsBiggerThanOne >= 3

    def printWithVals(): Unit = System.err.println(this.str(withVals = true))
    def print(): Unit = System.err.println(this.str(withVals = false))

    def apply(i: Int): Expression = Expression.pick(x, i)

    def T: Expression = Expression.transpose(x)

    def reshape(d: Dim): Expression = Expression.reshape(x, d)
    private def cleanTrailingOnes(list:List[Long]) : List[Long] = {
      list.reverse.dropWhile(_==1L).reverse
    }
    // zeros ( 2   ) .expandHorizontally (3) .print() ---> Matrix(2,3)
    // zeros ( 2   ) .expandVertically   (3) .print() ---> Vector(6)
    // zeros ((2,1)) .expandHorizontally (3) .print() ---> Matrix(2,3)
    // zeros ((2,1)) .expandVertically   (3) .print() ---> Vector(6)
    // zeros ((1,2)) .expandHorizontally (3) .print() ---> Vector(1,6)
    // zeros ((1,2)) .expandVertically   (3) .print() ---> Matrix(3,2)
    def expandHorizontally(i:Int): Expression = DyFunctions.expandHorizontally(x, i)
    def expandVertically(i:Int): Expression = DyFunctions.expandVertically(x, i)
  }

}
