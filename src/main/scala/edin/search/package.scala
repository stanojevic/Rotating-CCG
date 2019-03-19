package edin

/**
  * file GeneralBeam.scala
  *         - contains stuff for the most general beam search
  *         - doesn't have to be neural and is not very optimized for neural stuff
  *         - but all neural searches depend on it
  *         ! contains Hypothesis interface that is used everywhere
  * file NeuralBeam
  *         ### look at comparison in the end
  *         - contains just a general interface with search function and one factory method
  *         - mostly irrelevant
  * file NeuralBeamLatent
  *         ### look at comparison in the end
  *         - NeuralBeamLatent extends NeuralBeam
  *         - - - - search - just calls searchAllLatent
  *         - - - - searchAllLatent
  *         - - - - - - - should be private? not used anywhere
  *         - - - - - - - constructs GeneralBeam with HypMain
  *
  *         - HypMain
  *         - - - - packedStates :List[PredictionState]
  *         - - - - bestState -- not important for search only for printing in the end if you really need that
  *         - - - - score -- max or inside sum
  *         - - - - isFinished -- if any of subhyps is finished
  *         - - - - private nextEmittingStates
  *         - - - - topActions(k)
  *         - - - - applyAction(a) returns normal Hypothesis (itself)
  *         - HypSub ( predictionState, isReadyToEmit )
  *         - # models a single state
  *         - - - - isFinished = if it's ready to emit
  *         - - - - score = simply the score of the PackedState it wraps
  *         - - - - topActions(k) = returns most probable actions
  *         - - - - - - TODO memoization? is it done by GeneralBeam?
  *         - - - - applyAction(a) returns normal Hypothesis (itself)
  * file NeuralBeamStandard
  *         - # simply wraps PredictionState and gives normal GeneralBeam
  * file NeuralBeamWordSynchronous
  *         - # explained in page 3 https://arxiv.org/pdf/1806.04127.pdf
  *
  * All options: NeuralBeamLatent, NeuralBeamStandard, NeuralBeamWordSynchronous implement NeuralBeam
  *   - NeuralBeamStandard is just a stupid beam as plain as possible
  *       - constrained generative
  *           if state.isEmitting choose the given word
  *           else as usual
  *       - constrained discriminative
  *           if state.isEmitting choose the single 0 cost emitting transition ASSERT
  *           else as usual
  *       - unconstrained generative
  *           as usual
  *       - unconstrained discriminative
  *           as usual
  *   - NeuralBeamWordSynchronous is like the one from Berkley
  *       - constrained generative
  *           ?????
  *       - constrained discriminative
  *           ?????
  *       - unconstrained generative
  *           ?????
  *       - unconstrained discriminative
  *           ?????
  *   - NeuralBeamLatent is like the one from Berkley
  *       - constrained generative
  *           ?????
  *       - constrained discriminative
  *           ?????
  *       - unconstrained generative
  *           ?????
  *       - unconstrained discriminative
  *           ?????
  *
  */
package object search {

}
