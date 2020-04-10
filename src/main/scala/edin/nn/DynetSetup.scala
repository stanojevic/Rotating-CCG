package edin.nn

import edu.cmu.dynet.internal.DynetParams
import edu.cmu.dynet.{ComputationGraph, internal}

object DynetSetup {

  def init_dynet(dynet_mem:String, autobatch:Int) : Unit = init_dynet(dynet_mem, autobatch, null)

  def init_dynet(dynet_mem:String, autobatch:Int, seed:java.lang.Long) : Unit = {
    val params:DynetParams = new internal.DynetParams()

    if(seed != null)
      params.setRandom_seed(seed)

    params.setAutobatch(autobatch)
    if(dynet_mem != null)
      params.setMem_descriptor(dynet_mem)

    internal.dynet_swig.initialize(params)
  }

  private var expressions = List[AnyRef]()
  def safeReference(x:AnyRef) : Unit = {
    expressions ::= x
  }

  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////

  private var cg_counter = 0

  def cg_id : Int = {
    cg_counter
  }

  def cg_renew() : Unit = {
    expressions = List()
    cg_counter += 1
    ComputationGraph.renew()
    if(DEBUGGING_MODE){
      ComputationGraph.cg.set_immediate_compute(true)
      ComputationGraph.cg.set_check_validity(true)
    }
  }

  var DEBUGGING_MODE: Boolean = false

}
