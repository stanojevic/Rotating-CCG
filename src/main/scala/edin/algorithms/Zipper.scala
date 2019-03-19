package edin.algorithms

object Zipper {

  def zip2[A,B](xs:Iterator[A], ys:Iterator[B]) : Iterator[(A, B)] =
    (xs zip ys).map{ case (x, y) =>
      (x, y)
    }

  def zip2[A,B](xs:Iterable[A], ys:Iterable[B]) : Iterable[(A, B)] =
    (xs zip ys).map{ case (x, y) =>
      (x, y)
    }

  def zip2WithNullStream[A, B](xs:Iterator[A]) : Iterator[(A, B)] =
    xs.map( x => (x, null.asInstanceOf[B]))

  def zip2WithNullStream[A, B](xs:Iterable[A]) : Iterable[(A, B)] =
    xs.map( x => (x, null.asInstanceOf[B]))

  def zip3[A,B,C](xs:Iterator[A], ys:Iterator[B], zs:Iterator[C]) : Iterator[(A, B, C)] =
    ((xs zip ys) zip zs).map{ case ((x, y), z) =>
      (x, y, z)
    }

  def zip3[A,B,C](xs:Iterable[A], ys:Iterable[B], zs:Iterable[C]) : Iterable[(A, B, C)] =
    ((xs zip ys) zip zs).map{ case ((x, y), z) =>
      (x, y, z)
    }

  def zip3WithNullStream[A, B, C](xs:Iterator[A], ys:Iterator[B]) : Iterator[(A, B, C)] =
    (xs zip ys).map{ case (x, y) => (x, y, null.asInstanceOf[C])}

  def zip3WithNullStream[A, B, C](xs:Iterable[A], ys:Iterable[B]) : Iterable[(A, B, C)] =
    (xs zip ys).map{ case (x, y) => (x, y, null.asInstanceOf[C])}

}

