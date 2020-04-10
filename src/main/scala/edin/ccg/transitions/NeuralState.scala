package edin.ccg.transitions

import edin.algorithms.LazyVector
import edin.ccg.representation.category.Category
import edin.ccg.representation.combinators.Combinator
import edin.ccg.representation.predarg.DepLink
import edin.ccg.representation.tree._
import edin.general.YamlConfig
import edin.nn.WrappedStateLazy
import edin.nn.attention.{Attention, AttentionConfig}
import edin.nn.embedder.{Embedder, EmbedderConfig}
import edin.nn.layers._
import edin.nn.sequence._
import edin.nn.set.{DeepSet, DeepSetConfig, DeepSetStateTyped}
import edin.nn.tree.composers.{CompositionFunction, MultiCompositionFunctionConfig}
import edu.cmu.dynet.{Expression, ParameterCollection}

final case class NeuralState(
                              compositionFunction       : CompositionFunction,
                              embedderCategory          : Embedder[Category],
                              embedderCombinator        : Embedder[Combinator],
                              embedderPositionBottomUp  : Embedder[Int],
                              embedderPositionTopDown   : Embedder[Int],
                              combinerTerminal          : Combiner,
                              combinerNonTerminal       : Combiner,
                              configurationCombiner     : Combiner,
                              logSoftmaxTags            : Layer,
                              logSoftmaxTrans           : Layer,
                              attentionRightAdjunction  : Attention,
                              depsSetState              : DeepSetStateTyped[WrappedStateLazy[DepLink]],
                              headsSet                  : DeepSet,
                              headsPreencoding          : LazyVector[Expression] = LazyVector(),
                              depsPreencodingHead       : LazyVector[Expression] = LazyVector(),
                              depsPreencodingDependent  : LazyVector[Expression] = LazyVector(),
                              depsPrepLayerHead         : Layer,
                              depsPrepLayerDependent    : Layer,
                              recursiveNNEarly          : Boolean,
                              locallyNormalized         : Boolean,
//                              errorStateMLP             : MLP,
){

  def isUseNodeContentInRevealing : Boolean =
    attentionRightAdjunction.inDim -
      configurationCombiner.outDim -
      embedderPositionTopDown.outDim -
      embedderPositionBottomUp.outDim > 0

  /**
    * outsideRepr    -- dim_LSTM_Out
    * tagEmb         -- dim_Category
    * node.hierState -- node_dim
    */
  def encodeTerminal(node:TerminalNode, outsideRepr:Expression) : NeuralState = {
    lazy val tagEmb = embedderCategory(node.cat)
    lazy val compressedH = combinerTerminal(outsideRepr, tagEmb)
    node.hierState = compositionFunction.initState(compressedH)
    this.copy(
      headsPreencoding         = headsPreencoding         :+ headsSet.preEncode(     outsideRepr ),
      depsPreencodingHead      = depsPreencodingHead      :+ depsPrepLayerHead(      outsideRepr ),
      depsPreencodingDependent = depsPreencodingDependent :+ depsPrepLayerDependent( outsideRepr )
    )
  }

  def refreshNodeRepresentation(oldNode:TreeNode)(newNode: => TreeNode) : Unit =
    if(recursiveNNEarly){
      // assert(! conf.parserProperties.useRevealing || ! isUseNodeContentInRevealing)
      assert(! isUseNodeContentInRevealing)
      refreshEncoding(oldNode)
      newNode.hierState = oldNode.hierState
    }else{
      refreshEncoding(newNode)
    }

  private def refreshEncoding(node:TreeNode) : Unit =
    if(node.hierState == null)
      node match {
        case TerminalNode(_, _) =>
          throw new Exception("you must embed the terminals yourself")
        case BinaryNode(_, l, r) =>
          refreshEncoding(l)
          refreshEncoding(r)
          node.hierState = compositionFunction.compose(l.hierState, r.hierState, nodeInitialRep(node))
        case UnaryNode(_, child) =>
          refreshEncoding(child)
          node.hierState = compositionFunction.compose(List(child.hierState), nodeInitialRep(node))
      }

  private def nodeInitialRep(node:TreeNode) : Expression =
    combinerNonTerminal(
        embedderCategory(node.category),
        embedderCombinator(node.getCombinator.get),
        headRep(node)
    )

  private def headRep(node:TreeNode) : Expression =
    if(headsSet == null)
      null
    else
      headsSet.postEncode(node.heads.toList.map(headsPreencoding(_)))

  def addDependenciesForNode(node:TreeNode) : NeuralState =
    if(depsSetState == null)
      this
    else
      addDependencies(node.deps(hockenDeps = false))

  private def addDependencies(deps: => List[DepLink]) : NeuralState = this.copy(
    depsSetState = this.depsSetState + deps.map(embedDependency) )

  private def embedDependency(dep:DepLink) : WrappedStateLazy[DepLink] =
    WrappedStateLazy(
      depsPreencodingHead(dep.headPos) + depsPreencodingDependent(dep.depPos),
      dep
    )

}

final case class NeuralParameters(
                                   stackEncoder              : NeuralStackBuilder[TreeNode],
                                   compositionFunction       : CompositionFunction,
                                   embedderCategory          : Embedder[Category],
                                   embedderCombinator        : Embedder[Combinator],
                                   embedderPositionBottomUp  : Embedder[Int],
                                   embedderPositionTopDown   : Embedder[Int],
                                   combinerTerminal          : Combiner,
                                   combinerNonTerminal       : Combiner,
                                   configurationCombiner     : Combiner,
                                   logSoftmaxTags            : MLP,
                                   logSoftmaxTrans           : MLP,
                                   attentionRightAdjunction  : Attention,
                                   headsSet                  : DeepSet,
                                   depsSet                   : DeepSet,
                                   depsPrepLayerHead         : Layer,
                                   depsPrepLayerDependent    : Layer,
                                   recursiveNNEarly          : Boolean,
                                   locallyNormalized         : Boolean,
//                                   errorStateMLP             : MLP
){

  def initState : NeuralState =
    NeuralState(
      compositionFunction      = compositionFunction      ,
      embedderCategory         = embedderCategory         ,
      embedderCombinator       = embedderCombinator       ,
      embedderPositionBottomUp = embedderPositionBottomUp ,
      embedderPositionTopDown  = embedderPositionTopDown  ,
      combinerTerminal         = combinerTerminal         ,
      combinerNonTerminal      = combinerNonTerminal      ,
      configurationCombiner    = configurationCombiner    ,
      logSoftmaxTags           = logSoftmaxTags           ,
      logSoftmaxTrans          = logSoftmaxTrans          ,
      attentionRightAdjunction = attentionRightAdjunction ,
      headsSet                 = headsSet                 ,
      depsSetState             = if(depsSet!=null) depsSet.empty() else null,
      depsPrepLayerHead        = depsPrepLayerHead        ,
      depsPrepLayerDependent   = depsPrepLayerDependent   ,
      recursiveNNEarly         = recursiveNNEarly         ,
      locallyNormalized        = locallyNormalized        ,
//      errorStateMLP            = errorStateMLP            ,
    )

}

object NeuralParameters{

  def fromYaml(conf:YamlConfig)(implicit model: ParameterCollection) : NeuralParameters = {

    val depsSetConfig                = DeepSetConfig.fromYaml(      conf("deps-deep-set"              ))
    val headsSetConfig               = DeepSetConfig.fromYaml(      conf("heads-deep-set"             ))
    val depsPrepLayerHeadConfig      = SingleLayerConfig.fromYaml(  conf("deps-prep-layer-head"       ))
    val depsPrepLayerDependentConfig = SingleLayerConfig.fromYaml(  conf("deps-prep-layer-dependent"  ))

    val (depsSet, depsPrepLayerHead, depsPrepLayerDependent) = if(depsSetConfig.outDim == 0)
      (null, null, null)
    else
      (depsSetConfig.construct(), depsPrepLayerHeadConfig.construct(), depsPrepLayerDependentConfig.construct())
    val headsSet = if(headsSetConfig.outDim == 0) null else headsSetConfig.construct()

    NeuralParameters(
      stackEncoder             = NeuralStackBuilderConfig       .fromYaml[TreeNode  ]( conf( "neural-stack"                   )).construct(),
      compositionFunction      = MultiCompositionFunctionConfig .fromYaml            ( conf( "composition-function"           )).construct(),
      embedderCategory         = EmbedderConfig                 .fromYaml[Category  ]( conf( "embedder-category"              )).construct(),
      embedderCombinator       = EmbedderConfig                 .fromYaml[Combinator]( conf( "embedder-combinator"            )).construct(),
      combinerTerminal         = CombinerConfig                 .fromYaml            ( conf( "combiner-layer-terminals"       )).construct(),
      combinerNonTerminal      = CombinerConfig                 .fromYaml            ( conf( "combiner-layer-nonterminals"    )).construct(),
      configurationCombiner    = CombinerConfig                 .fromYaml            ( conf( "combiner-layer-configuration"   )).construct(),
      logSoftmaxTags           = MLPConfig                      .fromYaml            ( conf( "mlp-logsoftmax-tags"            )).construct(),
      logSoftmaxTrans          = MLPConfig                      .fromYaml            ( conf( "mlp-logsoftmax-trans"           )).construct(),
      attentionRightAdjunction = AttentionConfig                .fromYaml            ( conf( "attention-right-adjunction"     )).construct(),
      embedderPositionBottomUp = EmbedderConfig                 .fromYaml[Int       ]( conf( "embedder-position-bottom-up"    )).construct(),
      embedderPositionTopDown  = EmbedderConfig                 .fromYaml[Int       ]( conf( "embedder-position-top-down"     )).construct(),
      headsSet                 = headsSet,
      depsSet                  = depsSet,
      depsPrepLayerHead        = depsPrepLayerHead,
      depsPrepLayerDependent   = depsPrepLayerDependent,
      recursiveNNEarly         = conf.getOrElse("composition-function-rebranching-free", false),
      locallyNormalized        = conf( "locally-normalized" ).bool,
//      errorStateMLP            = MLPConfig.fromYaml(                          conf( "mlp-error-states"               )).construct(),
    )
  }

}

