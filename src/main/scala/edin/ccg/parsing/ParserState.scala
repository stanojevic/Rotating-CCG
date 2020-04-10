package edin.ccg.parsing

import edin.ccg.transitions.{Configuration, ConfigurationState}
import edin.nn.layers.VocLogSoftmax
import edin.nn.DyFunctions._
import edin.search.PredictionState
import edu.cmu.dynet.Expression

final case class ConstrainedInfo(wordsLeftover:List[String], embsLeftover:List[Expression]){
  val isFinished : Boolean         = wordsLeftover.isEmpty
  def headWord   : String          = wordsLeftover.head
  def headEmb    : Expression      = embsLeftover.head
  def tail       : ConstrainedInfo = ConstrainedInfo(wordsLeftover.tail, embsLeftover.tail)
}

final case class GenerativeInfo(
                                  wordEmissionTotalScoreExp : Expression,
                                  logSoftmax                : VocLogSoftmax[String]
                                ){
  lazy val wordEmissionTotalScoreFloat:Float = wordEmissionTotalScoreExp.toFloat
}


class ParserState(
               val conf            : Configuration,
                   constrainedInfo : Option[ConstrainedInfo],
                   generativeInfo  : Option[GenerativeInfo ],   // stores emission probabilities and VocLogSoftmax
                   ) extends PredictionState{

  private lazy val isDiscriminative = generativeInfo.isEmpty
  private      def isGenerative = ! isDiscriminative

  override lazy val scoreExp: Expression = conf.totalScoreExpression + generativeInfo.map(_.wordEmissionTotalScoreExp).getOrElse(scalar(0))

  override lazy val isReadyToEmit: Boolean = conf.isBlocked

  override lazy val isFinished: Boolean = {
    assert(constrainedInfo.nonEmpty)
    (constrainedInfo.get.isFinished, ConfigurationState.TAG_FIRST) match {
      case (false, _) => false
      case (_, true ) => conf.state.isTagging
      case (_, false) => conf.state.isBlocked
    }
  }

  override lazy val nextActionLogProbsLocalExp: Expression = (isDiscriminative, conf.isBlocked, constrainedInfo.nonEmpty) match {
    case (true , _   , false) => sys.error("you can't have discriminative model without constraints")
    case (true , true, true ) => vector(Array(0))
    case (false, true, true ) => generativeInfo.get.logSoftmax(conf.h)(generativeInfo.get.logSoftmax.s2i(constrainedInfo.get.headWord))
    case (false, true, false) => generativeInfo.get.logSoftmax(conf.h)
    case (_    , _   , _    ) => conf.transitionLogDistribution._2
  }

  def printOptions() : Unit = {
    System.err.println(s"state = ${conf.state}")
    if(conf.isBlocked){
      for((s, i) <- this.nextActionLogProbsTotalValues.zipWithIndex.sorted.reverse){
        System.err.println(s"\t$s $i")
      }
    }else{
      for((s, t) <- (this.nextActionLogProbsTotalValues zip conf.transitionLogDistribution._1).sortBy(-_._1)){
        System.err.println(s"\t$s $t")
      }
    }
    System.err.println()
  }
  // printOptions()

  override def applyAction(a: Int): PredictionState = {
    require(! isFinished, "can't apply action to a finished configuration")

    (isDiscriminative, conf.isBlocked, constrainedInfo.nonEmpty) match {
      case (_    , true , true ) =>
        // just put in the next word
        require(a == 0)
        val word = constrainedInfo.get.headWord
        val emb  = constrainedInfo.get.headEmb
        val newConf = conf.unblockWithWord(emb, word)
        val newGenerativeInfo = generativeInfo.map(g => g.copy(wordEmissionTotalScoreExp = g.wordEmissionTotalScoreExp+nextActionLogProbsLocalExp(a)))
        new ParserState(newConf, constrainedInfo.map(_.tail), newGenerativeInfo)
      case (false, true , false) =>
        // pick word a from softmax and add it to parse state
        val word = generativeInfo.get.logSoftmax.s2i(a)
        val emb  = ??? // here is where you need to put input feeding and lstm
        val newConf = conf.unblockWithWord(emb, word)
        new ParserState(newConf, constrainedInfo, generativeInfo)
      case (true , true , false) =>
        sys.error("you can't have unconstrained discriminative model")
      case (_    , false, _    ) =>
        // just apply the action a
        val newConf = conf.transitionLogDistribution._1(a).apply(conf)
        new ParserState(newConf, constrainedInfo, generativeInfo)
    }

  }

  override def equals(o: Any): Boolean = o match {
    case that : ParserState => this.conf == that.conf
    case _ => false
  }

}
