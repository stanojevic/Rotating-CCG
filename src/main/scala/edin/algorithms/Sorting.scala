package edin.algorithms

object Sorting {

  def pigeonholeSort[T](iterable: Iterable[T])(toKey: T => Int): Iterable[T] ={

    val rawNums = iterable.map(toKey)
    val rawMin = rawNums.min
    val max = rawNums.max - rawMin
    val mapped = rawNums.map{_-rawMin} zip iterable

    var storage = Array.fill(max+1)(List[T]())
    for((i, el) <- mapped){
        storage(i) ::= el
    }
    storage.toList.flatMap{_.reverse}
  }

  def radixLsdSort[T](iterable: Iterable[T])(toKey: T => Int): Iterable[T] ={

    val n = iterable.size

    val rawNums = iterable.map(toKey)
    val rawMin = rawNums.min

    val nums = rawNums.map(_-rawMin)
    val max = nums.max

    val maxBits = math.ceil( math.log(max)/math.log(2) ).toInt
    val base:Int = if(maxBits < math.log(n)) maxBits else math.log(n).toInt

    var mapped = nums zip iterable
    var digitPosition = 0
    while(math.pow(base, digitPosition) < max){
      val divider = math.pow(base, digitPosition).toInt
      mapped = pigeonholeSort(mapped)( _._1/divider%base )
      digitPosition += 1
    }

    mapped.map(_._2)
  }

}

