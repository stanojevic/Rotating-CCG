package edin.ccg.representation.category

final case class Substitution(subs:Set[(Boolean, NonTerm, Feature)]){
  def isEmpty : Boolean = subs.isEmpty
  def isLeftMostly : Boolean = subs.head._1
  def apply(nt:NonTerm) : Feature = subs.find(_._2 == nt).get._3
  def contains(nt: NonTerm) : Boolean = subs.exists(_._2 == nt)
  def get(nt:NonTerm) : Option[Feature] = subs.find(_._2 == nt).map(_._3)
  def + (other:Substitution) : Substitution = Substitution(subs union other.subs)
  def onlyLeft : Substitution = Substitution(subs.filter(_._1)) // features coming from the left category, assing it to right
  def onlyRight : Substitution = Substitution(subs.filter(! _._1))
}

trait Category extends Serializable {

  val isModifier: Boolean

  val isFeatureLess: Boolean

  val isTypeRaised: Boolean

  def isVerbal : Boolean = this match {
    case Functor(_, x, _) => x.isVerbal
    case Atomic(NonTerm("S"), _) => true
    case _ => false
  }

  def isBackSlashAdjunctCategory : Boolean = this match {
    case Functor(Slash.BCK, x, y) if x == y => true
    case _ => false
  }

  def isFWDSlashAdjunctCategory : Boolean = this match {
    case Functor(Slash.FWD, x, y) if x == y => true
    case _ => false
  }

  def matches(other:Category) : Boolean
  def matchesFeatureless(other:Category) : Boolean

  def doSubstitution(substitute: Substitution) : Category

  def getSubstitution(other: Category) : Substitution

//  val arguments:Int = this match{
//    case Atomic(_, _) => 0
//    case Functor(_, l, _) => 1+l.arguments
//  }

  def forPrinting(concise:Boolean) : String

}

object Category{

  def apply(s:String) : Category = CategoryLoader.fromString(s)
  def fromString(s:String) : Category = CategoryLoader.fromString(s)

  val N          = Category("N")
  val NP         = Category("NP")
  val PP         = Category("PP")
  val NP_back_NP = Category("""NP\NP""")
  val NP_fwd_NP  = Category("""NP/NP""")
  val N_back_N   = Category("""N\N""")
  val S_back_NP  = Category("""S\NP""")
  val S_fwd_S    = Category("""S/S""")
  val S_fwd_NP   = Category("""S/NP""")
  val S_fwd_O_S_back_NP_OO = Category("""S/(S\NP)""") // S/(S\NP)
  val O_S_back_NP_OO_back_O_O_S_back_NP_OO_fwd_PP_OO = Category("""(S\NP)\((S\NP)/PP)""")
  val O_S_back_NP_OO_back_O_O_S_back_NP_OO_fwd_NP_OO = Category("""(S\NP)\((S\NP)/NP)""")
  val O_S_back_NP_OO_fwd_O_S_back_NP_OO = Category("""(S\NP)/(S\NP)""")

  private object CategoryLoader {

    def fromString(s: String) : Category =
      if(s.endsWith("[conj]")) {
        val core = s.replace("[conj]", "")
        val (cat, Nil) = processTokens(tokenize(core))
        ConjCat(cat)
      }else if(s == "((S[b]\\NP)/NP)/"){ // this is a bug in the treebank
        processTokens(tokenize("(S[b]\\NP)/NP"))._1
      }else{
        processTokens(tokenize(s))._1
      }

    private def processTokens(tokens:List[String]) : (Category, List[String]) = {
      var currTokens = tokens
      var cats = List[Category]()
      var ops = List[Slash]()

      while( currTokens.nonEmpty && currTokens.head != ")"){
        if(currTokens.head == "/"){
          ops ::= Slash.FWD
          currTokens = currTokens.tail
        }else if(currTokens.head == "\\"){
          ops ::= Slash.BCK
          currTokens = currTokens.tail
        }
        if(currTokens.head == "("){
          val (cat, leftover) = processTokens(currTokens.tail)
          cats ::= cat
          currTokens = leftover
        }else{
          cats ::= Atomic.fromString(currTokens.head)
          currTokens = currTokens.tail
        }
      }
      if(currTokens.nonEmpty && currTokens.head == ")"){
        currTokens = currTokens.tail
      }
      cats = cats.reverse
      ops = ops.reverse
      while(cats.size > 1){
        //noinspection ZeroIndexToHead
        val x = Functor(ops(0), cats(0), cats(1))
        cats = x:: cats.drop(2)
        ops = ops.tail
      }
      (cats.head, currTokens)
    }


    private def tokenize(s:String) : List[String] = {
      s.
        replaceAllLiterally("("," ( ").
        replaceAllLiterally(")"," ) ").
        replaceAllLiterally("/"," / ").
        replaceAllLiterally("\\"," \\ ").
        trim().split(" +").toList
    }

  }
}