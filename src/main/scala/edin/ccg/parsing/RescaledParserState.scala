package edin.ccg.parsing

import edin.search.PredictionState
import edin.nn.DyFunctions._
import edu.cmu.dynet.Expression

object RescaledParserState{
  def apply(state : PredictionState, scalingParams : Option[Expression]) : RescaledParserState = new RescaledParserState(
    state         = state,
    taggingScores = scalar(0),
    taggingCount  = 0,
    wordScores    = scalar(0),
    wordCount     = 0,
    parseScores   = scalar(0),
    parseCount    = 0,
    scalingParams = scalingParams
  )
}

class RescaledParserState(
                           val state     : PredictionState,
                           taggingScores : Expression,
                           taggingCount  : Int,
                           wordScores    : Expression,
                           wordCount     : Int,
                           parseScores   : Expression,
                           parseCount    : Int,
                           scalingParams : Option[Expression],
                         ) extends PredictionState {

  private lazy val confState   = state.unwrapState[ParserState].conf.state

  override lazy val isReadyToEmit : Boolean = state.isReadyToEmit
  override lazy val isFinished    : Boolean = state.isFinished

  private lazy val aTaggingCount = if(taggingCount == 0) 1 else if(confState.isTagging) taggingCount+1 else taggingCount
  private lazy val    aWordCount = if(   wordCount == 0) 1 else if(confState.isBlocked)    wordCount+1 else    wordCount
  private lazy val   aParseCount = if(  parseCount == 0) 1 else if(confState.isParsing)   parseCount+1 else   parseCount

  override lazy val nextActionLogProbsLocalExp : Expression =
    if(confState.isTagging) state.nextActionLogProbsLocalExp/aTaggingCount else
    if(confState.isBlocked) state.nextActionLogProbsLocalExp/aWordCount    else
    if(confState.isParsing) state.nextActionLogProbsLocalExp/aParseCount   else
      ???

  private var tagNode   = taggingScores/aTaggingCount
  private var wordNode  = wordScores   /aWordCount
  private var parseNode = parseScores  /aParseCount

  if(scalingParams.nonEmpty){
    val p = scalingParams.get
    tagNode   = p(0) * Expression.noBackProp(tagNode  )
    wordNode  = p(1) * Expression.noBackProp(wordNode )
    parseNode = p(2) * Expression.noBackProp(parseNode)
  }

  override lazy val scoreExp: Expression = tagNode + wordNode + parseNode

  override def applyAction(a: Int): PredictionState = new RescaledParserState(
    state         = this.state.applyAction(a).unwrapState,
    taggingScores = taggingScores     + (if(confState.isTagging) this.state.nextActionLogProbsLocalExp(a) else scalar(0)),
    taggingCount  = this.taggingCount + (if(confState.isTagging) 1                                     else 0        ),
    wordScores    = wordScores        + (if(confState.isBlocked) this.state.nextActionLogProbsLocalExp(a) else scalar(0)),
    wordCount     = this.wordCount    + (if(confState.isBlocked) 1                                     else 0        ),
    parseScores   = parseScores       + (if(confState.isParsing) this.state.nextActionLogProbsLocalExp(a) else scalar(0)),
    parseCount    = this.parseCount   + (if(confState.isParsing) 1                                     else 0        ),
    scalingParams = scalingParams
  )

  override def unwrapState[T <: PredictionState]: T = state.unwrapState[T]

  override def equals(o: Any): Boolean = o match {
    case that:RescaledParserState => this.state == that.state
    case _ => false
  }

}
