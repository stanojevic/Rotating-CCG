package edin.nn

import java.lang.reflect.Method

import edu.cmu.dynet.internal.DynetParams
import edu.cmu.dynet.{ComputationGraph, internal}

object DynetSetup {

  private val setIds_requested_method = find_method("setIds_requested")
  private val setGpu_mask_method = find_method("setGpu_mask_method")

  private def find_method(name:String) : Method = {
    val methods = Class.forName("edu.cmu.dynet.internal.DynetParams").getMethods
    for(method <- methods){
      if(method.getName == name){
        return method
      }
    }
    null
  }

  private def convertListToDynetFormat(xs:List[Int]) : java.util.Vector[Int] = {
    val ys = new java.util.Vector[Int]()
    for(_ <- 0 until 256){
      ys.add(0)
    }
    for(x <- xs){
      ys.set(x, ys.get(x)+1)
    }
    ys
  }

  def init_dynet(dynet_mem:Int, weight_decay:Double, autobatch:Boolean, gpu_ids:List[Int]=List()) : Unit = {
    init_dynet(dynet_mem.toString, weight_decay.toFloat, if(autobatch) 1 else 0, gpu_ids)
  }

  def init_dynet(dynet_mem:String, weight_decay:Float, autobatch:Int, gpu_ids:List[Int]) : Unit = {
    val params:DynetParams = new internal.DynetParams()

    params.setWeight_decay(weight_decay)
    params.setAutobatch(autobatch)
    if(dynet_mem != null)
      params.setMem_descriptor(dynet_mem)

    if(gpu_ids.nonEmpty){
      if(setGpu_mask_method != null){
        // params.setNgpus_requested(bool) /**< GPUs requested by number */ // default false
        // params.setRequested_gpus(int)   /**< Number of requested GPUs */ // default -1
        setIds_requested_method.invoke( params, true.asInstanceOf[Object] ) /**< GPUs requested by ids */
        setGpu_mask_method.invoke( params, convertListToDynetFormat(gpu_ids) )   /**< List of required GPUs by ids */
        System.err.println(s"[dynet] DEVICE GPU $gpu_ids")
      }else{
        throw new Exception("[dynet] DyNet is not compiled for GPU")
      }
    }else{
      System.err.println(s"[dynet] DEVICE CPU")
    }

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
  }

}
