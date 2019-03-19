package edin.nn.tree.encoders

import edin.nn.tree.EncodableNode

trait TreeEncoder{

  def reencode(root: EncodableNode) : Unit

}
