package edin.ccg.representation.category

sealed trait Slash{

  def invert : Slash

}

object Slash{


  case object FWD extends Slash {

    override def toString = "/"

    override def invert : Slash = Slash.BCK

  }

  case object BCK extends Slash {

    override def toString = "\\"

    override def invert : Slash = Slash.FWD
  }


}

