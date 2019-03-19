package edin.nn.layers
import edu.cmu.dynet.Expression

final class IdentityLayer(dim:Int) extends Layer {

  inDim = dim
  outDim = dim

  override def apply(x: Expression, targets: List[Long]): Expression = targets match {
    case Nil => x
    case _ => Expression.selectRows(x, targets)
  }

}

object IdentityLayer{

  def apply(dim:Int) : IdentityLayer = new IdentityLayer(dim)

}

