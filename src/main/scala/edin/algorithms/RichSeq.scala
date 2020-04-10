package edin.algorithms

object RichSeq {

  implicit class Splitting[A](stream:Seq[A]){
    def splitByContent          (p:A=>Boolean) : Stream[List[A]] = splitByContent(stream, withDropping = true )(p)
    def splitByContentNoDropping(p:A=>Boolean) : Stream[List[A]] = splitByContent(stream, withDropping = false)(p)

    private def splitByContent[A](s:Seq[A], withDropping:Boolean)(p:A=>Boolean) : Stream[List[A]] = {
      lazy val (prefix, rest) = s.span(x => ! p(x))
      lazy val subResult:Stream[List[A]] = if(rest.isEmpty){
        Stream.empty
      }else if(withDropping){
        splitByContent(rest.tail, withDropping)(p)
      }else{
        Stream.cons((rest.head :: subResult.head), subResult.tail)
      }
      Stream.cons(prefix.toList, subResult)
    }
  }


}
