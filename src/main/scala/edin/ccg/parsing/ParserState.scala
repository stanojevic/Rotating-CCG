package edin.ccg.parsing

import edin.ccg.transitions.{Configuration, ShiftOption}
import edin.nn.layers.VocLogSoftmax
import edin.nn.DyFunctions._
import edin.search.PredictionState
import edu.cmu.dynet.Expression

sealed case class ConstrainedInfo(wordsLeftover:List[String], embsLeftover:List[Expression]){
  val isFinished : Boolean = wordsLeftover.isEmpty
  def headWord : String = wordsLeftover.head
  def headEmb  : Expression = embsLeftover.head
  def tail : ConstrainedInfo = ConstrainedInfo(wordsLeftover.tail, embsLeftover.tail)
}
sealed case class GenerativeInfo(
                                  isEOS:Boolean,
                                  wordEmissionTotalScoreExp:Expression,
                                  logSoftmax:VocLogSoftmax[String]
                                ){
  lazy val wordEmissionTotalScoreFloat:Float = wordEmissionTotalScoreExp.toFloat
}

class ParserState(
                   aConf: Configuration,
                   aConstrainedInfo : Option[ConstrainedInfo],
                   generativeInfo   : Option[GenerativeInfo]
                   ) extends PredictionState{

  private val isDiscriminative = generativeInfo.isEmpty

  val (conf: Configuration, constrainedInfo: Option[ConstrainedInfo]) = if(aConf.isBlocked && isDiscriminative){
    val info = aConstrainedInfo.get
    (
      aConf.unblockWithWord(
        info.headEmb,
        info.headWord,
        info.tail.isFinished),
      Some(info.tail)
    )
  }else{
    (
      aConf,
      aConstrainedInfo
    )
  }

  def isPreemissionAction(a:Int) : Boolean = {
    !isFinished && !isReadyToEmit && conf.transitionLogDistribution._1(a).isInstanceOf[ShiftOption]
  }

  override val scoreExp: Expression =
    if(isDiscriminative) conf.totalScoreExpression
    else                 conf.totalScoreExpression + generativeInfo.get.wordEmissionTotalScoreExp

  override val isReadyToEmit: Boolean = ! isDiscriminative && conf.isBlocked

  override val isFinished: Boolean =
    if(isDiscriminative) conf.isFinal
    else                 generativeInfo.get.isEOS // lastGeneratedWord == conf.allS2I.w2i.EOS_i

  override val nextActionLogProbsLocal: Expression =
    if(isDiscriminative || ! conf.isBlocked)
      conf.transitionLogDistribution._2
    else
      generativeInfo.get.logSoftmax(conf.h)

  override def applyAction(a: Int): PredictionState = {
    assert(! isFinished)
    if(isDiscriminative || ! conf.isBlocked){
      val newConf = conf.transitionLogDistribution._1(a).apply(conf)
      new ParserState(newConf, constrainedInfo, generativeInfo)
      // new ParserState(newConf, words, embs, logSoftmax)
    }else{
      /// WE KNOW: IT IS GENERATIVE EMITTING A WORD
      val w2i = generativeInfo.get.logSoftmax.s2i
      val localCost:Expression = nextActionLogProbsLocal(a)
      if(a == w2i.EOS_i){
        // IF EOS
        new ParserState(conf, constrainedInfo, generativeInfo.map(info =>
            info.copy(
              isEOS = true,
              wordEmissionTotalScoreExp = info.wordEmissionTotalScoreExp+localCost
            )
          )
        )
      }else{
        // IF NOT EOS
        val newGenerativeInfo = generativeInfo.map( info =>
          info.copy(
            isEOS = false,
            wordEmissionTotalScoreExp = info.wordEmissionTotalScoreExp+localCost
          )
        )
        val newConstrainedInfo = constrainedInfo.map{ c =>
          assert(w2i(c.headWord) == a)
          c.tail
        }
        val ( newEmb:Expression, newWord:String ) = constrainedInfo match {
          case Some(constInfo) =>
            ( constInfo.headEmb, constInfo.headWord )
          case None =>
            val word_str = w2i(a)
            val word_emb = ??? // TODO INPUT FEEDING HERE
            (word_emb, word_str)
        }
        val newConf = aConf.unblockWithWord(newEmb, newWord, isFinal = false)
        new ParserState(newConf, newConstrainedInfo, newGenerativeInfo)
      }
    }
  }

}
