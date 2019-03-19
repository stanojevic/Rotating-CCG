//===================================================
//  File: /home/julia/CCG/code/StatisticalParser/code/CurrentCVSed/StatCCG/CCGcat.java
//  Author: Julia Hockenmaier                                
//  Purpose:                                                  
//  Created: Thu Feb  7 14:53:50 2002                         
//  Modified: Fri Apr  6 12:14:11 2007 (juliahr)               
//===================================================
// History:
//
// Exports:
// Imports:
//===================================================
package edin.ccg.representation.hockendeps;

import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

/**
 An implementation of CCG categories as recursive data structures, and
 of the combinatory rules as operations on these. At the moment, no
 logical form is constructed. Instead, a list of word-word
 dependencies is generated.

 Each category has the following
 fields:
 <ul>
 <li>A <b>catString</b>, the string representation of the category.<br> (Contrary to the implementation of categories in {BinaryTree}, these are the "real" string representations, not an encoded form of these).
 <li>An <b>id</b> number, which identifies this category token.<br> Required as bookkeeping device for unification.
 <li>A list of lexical heads <b>heads</b>, which is possibly null (in the case of type-raising).
 <li>A <b>HeadId</b> number, identifying the lexical heads; also bookkeeping device for unification.
 <li> A pointer <b>function</b> which points to the parent if this category is part of a complex category, or to null otherwise.
 <li> A list of <b>unfilled dependencies</b>, which is only non-null if the category is an argument.
 <li> A list of <b>filledDependencies</b>, which records the dependencies filled by the last application of a combinatory rule. This is used to compute the dependency probabilities during parsing.
 </ul>

 A complex category has additionally the following fields:
 <ul>
 <li> A <b>result</b> category
 <li> An <b>argument</b> category
 <li> An integer <b>argDir</b>, indicating the directionality of the argument.
 </ul>

 <p> This class implements methods which
 <ul>
 <li> read in a categorial lexicon from a file
 <li> when creating a lexical category for a word, treat special cases of lexical categories so that dependencies between argument slots can be passed around.
 <li> create new categories by performing the operations of the combinatory rules on existing categories
 <li> perform unification operations necessary to fill word-word dependencies.
 </ul>
 */

public class CCGcat implements Cloneable {
	static final String NP = "NP";  
	static final String NOUN = "N"; 
	static final String S = "S";
	static final String VP = "S\\NP";
	static final String conjFeature = "[conj]";
	/** open brackets */
	static final char OB = '(';
	/** closing brackets */
	static final char CB = ')';
	/** forward slash */
	static final char fslash = '/';
	/** backward slash */
	static final char bslash = '\\';

	/** directionality of argument: forward */
	public static final int FW = 0;
	/** directionality of argument: backward */
	public static final int BW = 1;
	/** directionality of argument: unspecified */ 
	public static final int NODIR = -1;

	/** a counter for category instances */
	public static int idCounter = 0;
	/** a counter for head instances */
	public static int headIdCounter = 1;
	/** the ID of this category 
	-- unifiable categories which are part of the same category
	have the same ID */
	int id;        
	/** the ID of the head of this category.
	-- parts of one category with the same head have the same headID.
     Coordination and substitution create a new headID. */   
	int headId; 
	/** the string which denotes this category.  */
	public String catString;
	/** the argument category. Null in an atomic category  */    
	CCGcat argument;
	/** the result category. Null in an atomic category */
	CCGcat result;
	/** a pointer to the 'parent' of this category */
	CCGcat function;
	/** forward or backward */
	int argDir; 
	/** a list of dependencies which should hold for each of the elements of heads */
	public DepList dependencies;
	/** a list of the filled dependencies  */
	public DepList filledDependencies;


	/** a list of heads */
	HeadWordList heads;

	boolean extracted;//long-range, really
	boolean bounded;

	//##########################################################
	//
	//    CONSTRUCTORS
	//    ============
	// 
	//##########################################################

	/** Creates a category from a string denoting the category.
     Called by parseCat(String) */
	CCGcat(String string){
		id = newId();
		headId = newHeadId();
		catString = string;
		argument = null;
		result = null;
		argDir = NODIR;
		function = null;
		dependencies = null;
		heads = null;
		filledDependencies = null;
		extracted = false;
		bounded = true;
	}

	/** Build up a category consisting of a result and argument category.
	This does not clone the result and argument categories!! */
	CCGcat(CCGcat resCat, CCGcat argCat){	
		id = newId();	
		headId = -1; // dummy constructor
		catString = null;
		argument = argCat;
		argument.function = this;
		result = resCat;
		result.function = this;
		argDir = NODIR;
		dependencies = null;
		function = null;
		heads = resCat.heads();
		filledDependencies = null;
		extracted = false;
		bounded = true;
	}

	//##########################################################
	//
	//    ACCESS THE FIELDS     
	// 
	//##########################################################

	/** returns the catString -- the string representation of the category */
	public String catString(){
		return catString;
	}
	public String catStringIndexed(){
		if (argument == null) return catString();
		else return catStringRecIndexedArgs();	
	}
	// top-level method to return category where only indices that are not the same as the head are printed. 
	public String catStringRecIndexedArgs(){
		if (argument != null){
			int head = this.headId;
			String argString = argument.catStringRecIndexedArgs(head);
			String resultString = result.catStringRecIndexedArgs(head);
			String slash = "/";
			if (argDir == BW)
				slash = "\\"; 
			return  new String(resultString + slash + argString) ;
		}
		else return  catString();

	};
	/** print out the category with all head indices that are not the same as "head".
	If an argument is extracted, this is indicated by :U (unbounded) or :B (bounded). 
	Examples: 
	RelPron: (NP_11\NP_11)/(S[dcl]_12/NP_11:U)_12	 what
	RelPron: (NP_11\NP_11)/(S[dcl]_12\NP_11:B)_12	 what
	 */
	public String catStringRecIndexedArgs(int head){
		String thisCatString = null;
		String isBounded = null;
		if (this.extracted){
			if (this.bounded) isBounded = ":B";
			else isBounded = ":U";
		}
		// A complex category...
		if (argument != null){
			String argString = argument.catStringRecIndexedArgs(head);
			String resultString = result.catStringRecIndexedArgs(head);
			String slash = "/";
			if (argDir == BW)
				slash = "\\"; 
			// ... that does not require the head index:
			if (this.headId == head)
				thisCatString =   new String("("+ resultString + slash + argString +")") ;
			//... that does require the head index:
			else {

				thisCatString =  isBounded==null?new String("("+ resultString + slash + argString + ")_" + this.headId):new String("("+ resultString + slash + argString + ")_" + this.headId+isBounded);
			}

		}
		// An atomic category...
		//...that does not require the head index:
		else if (this.headId == head)
			thisCatString = catString();
		//...that does require the head index:
		else thisCatString = isBounded==null?new String(catString() + "_" + this.headId):new String(catString() + "_" + this.headId+isBounded);
		return thisCatString;
	};


	public String catStringRecIndexed(){
		if (argument != null){
			String argString = argument.catStringRecIndexed();
			String resultString = result.catStringRecIndexed();
			String slash = "/";
			if (argDir == BW)
				slash = "\\"; 
			return  new String("(" + resultString + slash + argString + "):"+ headId) ;
		}
		else return  new String(catString() + ":"  + headId);

	};

	/** returns the catString without any features */
	public String catStringNoFeatures(){
		return noFeatures(catString);		
	};
	/** strips off atomic features from category string representations */
	public static String noFeatures(String catString){
		int oIndex = catString.indexOf('[');
		if (oIndex > -1){
			int cIndex;
			StringBuffer nofeatures = 
				new StringBuffer(catString.substring(0, oIndex));
			cIndex = catString.indexOf(']', oIndex);
			oIndex = catString.indexOf('[', cIndex);
			while (cIndex > -1 && oIndex > -1){		
				nofeatures.append(catString.substring(cIndex +1, oIndex));
				cIndex = catString.indexOf(']', oIndex);
				oIndex = catString.indexOf('[', cIndex);
			};
			if (oIndex == -1){
				nofeatures.append(catString.substring(cIndex+1));
			};
			return nofeatures.toString();
		}
		else return catString;
	};

	public String indexedCatString(){
		return catStringIndexed();	
	};

	/** returns the direction of the argument. FW== 0, BW == 1 */
	public int argDir(){
		return argDir;
	};

	/** returns the argument category of this category */
	public CCGcat argument(){
		return argument;
	}
	/** returns the result category of this category */
	public CCGcat result(){
		return result;
	};

	/** returns the function category of this category. The inverse of argument and result */
	public CCGcat function(){
		return function;
	} 
	/** returns the global (=outermost) function of this category. */
	public CCGcat globalFunction(){
		if (function != null){
			return function.globalFunction();
		}
		else return this;
	}
	/** returns the list of dependencies defined by this category */
	public DepList deps(){
		return dependencies;
	}
	/** returns the list of head words */
	public HeadWordList heads(){
		return heads;
	}
	/** returns the first head word of this category */
	public String headWord(){
		return heads.word();
	}
	/** returns the target category. If a category is atomic, it is its own target. The target of a complex category is the target of its result category.
	This is not encoded as a field, but is equally important!*/
	public CCGcat target(){
		if (result != null) return result.target();
		else return this;
	}

	public static void resetCounters(){ 
		idCounter = 0;
		headIdCounter = 1;//reset the counters
	}
	
	//##########################################################
	//
	//    CHANGE THE FIELDS     
	// 
	//##########################################################
	/** sets the dependencies to  <tt>dep</tt>*/
	public void setDeps(DepList dep){
		if (dep != null){
			dependencies = dep;
		}
	}
	public void setToExtractedBounded(){
		extracted = true;
		bounded = true;
	}
	public void setToExtractedUnbounded(){
		extracted = true;
		bounded = false;
	}

	/** Apply all dependencies defined by <tt>deps</tt> to all heads in <tt>depHeads</tt>. Filled dependencies are appended to <tt>filledDeps</tt>
	 */
	public  DepList applyDependencies(DepList deps, HeadWordList depHeads, DepList filledDeps){
		DepList allDeps = null;
		HeadWordList h = depHeads;
		if (deps != null){ 	       
			while (h != null ){	   
				DepList d = deps.copy();
				DepList tmp = d;
				while (tmp != null){
					tmp.argWord = h.headWord;
					tmp.argCat = h.lexCat;
					tmp.argIndex = h.index();
					tmp = tmp.next();
				}
				if (allDeps == null) allDeps = d;
				else allDeps.append(d);	    
				h = h.next();
			} 
			if (allDeps != null){
				filledDeps = appendDeps(filledDeps, allDeps);
				allDeps = null;
			}
			else allDeps = deps.copy();
		}

		dependencies = allDeps;
		return filledDeps;	
	}

	/** standard append operation -- appends a copy of <tt>dep</tt> to dependencies; if dependencies are null, copies <tt>dep</tt>. */ 
	public void appendDeps(DepList dep){
		if (dep != null){
			if (dependencies != null)
				dependencies.append(dep.copy());
			else dependencies = dep.copy();
		}	
	}

	/** standard append operation -- appends a copy of <tt>dep2</tt> to <tt>dep1</tt>; if <tt>dep1</tt> is null, copies <tt>dep2</tt>. */
	public static DepList appendDeps(DepList dep1, DepList dep2){
		if (dep2 != null){
			if (dep1 != null){
				dep1.append(dep2.copy());
			}
			else { 
				dep1 = dep2.copy();	      
			}	  
		}
		return dep1;	
	}

	/** Append the HeadWordList given as argument to the current head word list */
	public void appendHeads(HeadWordList hw){
		if (hw !=  null){
			if (heads != null) heads.append(hw);
			else heads = hw.copy();
		}
	}


	//##########################################################
	//
	//   CREATE LEXICAL CATEGORIES
	// 
	//##########################################################
	public static CCGcat lexCat(String word, String cat, int index){
		CCGcat lexCat = parseCat(cat);
		lexCat.adjustAdjs();
		if (word == null){	    	   
			System.err.println("WORD == null;  Cat: " + cat);
			word = new String("xxx");	    	     
		}
		lexCat.heads = new HeadWordList(word, cat, index);
		lexCat.headId = newHeadId();//new lexical category 
		lexCat.target().assignHeadsDeps(1, lexCat.heads, lexCat.headId);//lexCat
		lexCat.treatSpecialCases();
		return lexCat;
	}
	/** creates a lexical category for a word */
	public static CCGcat lexCat(String word, String cat){    
		CCGcat lexCat = parseCat(cat);
		lexCat.adjustAdjs();
		if (word == null){	    	  
			//System.out.println("WORD == null;  Cat: " + cat);
			word = "xxx";
		}
		lexCat.heads = new HeadWordList(word, cat);
		lexCat.headId = newHeadId();//lexCat	
		lexCat.target().assignHeadsDeps(1, lexCat.heads, lexCat.headId);//lexCat
		lexCat.treatSpecialCases();
		return lexCat;
	}
	/** If the argument is the same as the result category, make them equal */
	public void adjustAdjs(){
		if (argument != null && result != null){
			argument.adjustAdjs();
			if (isAdjunctCat()){//argument.catString.equals(result.catString)){	
				argument.setHeadId(++headIdCounter);//adjustAdjs -- recursively set all headIds. 
				result = argument.copy();// result is equal to argurment
				result.dependencies = null;		
			}
		}
	}

	/** go from the target category outwards, 
	and assign the head and appropriate dependencies  */   
	public void assignHeadsDeps(int i, HeadWordList head, int headIdNumber){ 
		headId = headIdNumber;// set the headId to this number	
		if (heads == null) heads = head; 

		// this constructor does not deal with lists of headwords,
		// since it only assigns the first element of heads.
		// but that is okay, since this is used for lexical categories only.
		if (argument != null){
			argument.setHeadId(++headIdCounter);//also set the argument headID to a new number 
			if (heads()!= null){
				argument.setDeps(new DepList("arg", heads(), null, i, argDir));//record the direction of the argument as well
				i++;
			}
			// Adjuncts: an adjunct to a function passes the function on;
			// whereas an adjunct to an atomic category doesn't need to do that
			if (isAdjunctCat()){
				//System.out.println("ADJUNCT CAT: " + catString());
				argument.setHeadId(++headIdCounter);//adjunct: also set argument headID to new. 
				result = argument.copy();		
			}	   		
		}
		if (function != null) function.assignHeadsDeps(i, head, headIdNumber);
	}

	/** after adjuncts have been adjusted.. */
	public boolean isAdjunct(){
		boolean retval = false;
		if (argument != null && argument.id == this.id){// isAdjunct()
			retval = true;
		}
		return retval;
	}
	/** A test for adjunct categories, which are defined here as categories whose argument category string is identical to its result category string, and which do not have any features other than [adj]. 
	This doesn't work in all generality, but does the job for the categories in CCGbank */
	public boolean isAdjunctCat(){
		boolean retval = false;
		if(argument != null && argument.catString.equals(result.catString)){
			int index = argument.catString.indexOf('[');
			if (index > -1){
				if (argument.catString.startsWith("[adj]", index)){
					retval = true;
				}
			}
			else retval = true;
		}
		return retval;
	}

	public boolean isAtomic(){
		if (result == null || argument == null)
			return true; 
		else return false; 
	}
	/** This does the coindexation for all special cases of categories,
	such as auxiliaries/modals, control verbs, subject extraction, vp modifiers, relative pronouns, piep piping, etc. */
	public void treatSpecialCases(){
		// atomic categories aren't special
		if (isAtomic()) return;
		if (isAuxModal()) treatAuxModal();
		// control verbs => co-index subject of complement vp with sbj or obj np
		else {
			if (isControlVerb()){
				if (isSubjectControl()) treatSubjectControl();
				else treatObjectControl();
			}
			else if (isVerb()){
				if (isSbjExtractionVerb()) treatSbjExtractionVerb();
				else if (!isOtherObjectControlVerb()) treatComplementVPs();
				if (isToughVerb()) treatToughVerb();
			}
			else if (isVPModifier()){
				treatVPModifier();
				if (isSmallClausePP()) treatSmallClausePP();
			}
			else if (isRelPronoun()) treatRelPronoun();
			else if (isFreeRelPronoun()) treatFreeRelPronoun();
			else if (isGenitiveRelPronoun()) treatGenitiveRelPronoun();
			else if (isYNQCategory()) treatYNQCategory();
			else if (isNonStandardPiedPipingCategory()) treatNonStandardPiedPipingCategory();
			else if (isPiedPipingAdjunctExtractionCategory()) treatPiedPipingAdjunctExtractionCategory();
			else if (isStandardPiedPipingCategory()) treatStandardPiedPipingCategory();
			else if (isPiedPipingRelPronCategory()) treatPiedPipingRelPronCategory();
			else if (isPiedPipingSBJRelPronCategory()) treatPiedPipingSBJRelPronCategory();
			else if (isPiedPipingEmbeddedQuestionCategory()) treatPiedPipingEmbeddedQuestionCategory();
			// Determiners
			else if (isDet()) treatDet();
			else if (isPOS()) treatPOS();
			else if (isSmallClausePP()) treatSmallClausePP();
			else if (isToughAdj()) treatToughAdj();
		}
	}

	/** Possessive 's and ' */
	public boolean isPOS(){
		if (catString.equals("(NP[nb]/N)\\NP")) return true;
		else return false;
	}
	/** VP pre- and postmodifiers*/
	public boolean isVPModifier(){
		if (argument != null && result != null
				&& argument.catString.equals(VP)
				&& result.catString.equals(argument.catString)){
			return true;
		}
		else if (result != null) return result.isVPModifier();
		return false;
	}
	public void treatVPModifier(){
		// VP\VP/VP ==> set the subject NP of the VP complement to the main VP 
		if (argument.matches(VP)
				&& !argument.catString.equals("S[asup]\\NP")
				&& !argument.catString.equals(VP)){
			if (result.catString.equals("(S\\NP)\\(S\\NP)")
					||result.catString.equals("(S\\NP)/(S\\NP)")){				
				argument.argument =  result.argument.argument.copy();
				argument.argument.setToExtractedBounded();	
			}	
			else{ 
				if (result.argument != null
						&& result.argument.catString.equals(result.result.catString)
						&& (result.argument.catString.equals("(S\\NP)\\(S\\NP)")
								||  result.argument.catString.equals("(S\\NP)/(S\\NP)"))){
					argument.argument = result.argument.argument.argument.copy();
					argument.argument.setToExtractedBounded();		
				} 	   	    
			}
		}
	}
	public boolean isGenitiveRelPronoun(){
		if (argument != null && result.argument != null && result.argument.argument != null
				&& argument.catString.equals(NOUN)
				&& (result.argument.matches("S/NP") || result.argument.matches(VP))
				&& !result.argument.catString.equals("S[adj]\\NP")
				&& !result.argument.catString.equals("S[asup]\\NP")
				&& !(result.argument.id == result.result.id)){
			return true;
		}	
		return false;
	}
	public void treatGenitiveRelPronoun(){
		result.argument.argument = argument.copy();
		if (result.argument.argDir == BW)
			result.argument.argument.setToExtractedBounded();
		if (result.argument.argDir == FW)
			result.argument.argument.setToExtractedUnbounded();	
	}
	public boolean isRelPronoun(){
		String plainCat = catStringNoFeatures();	
		if ((plainCat.equals("(NP\\NP)/(S\\NP)") || plainCat.equals("(NP\\NP)/(S/NP)"))
				&& !argument.catString.equals("S[adj]\\NP") 
				&& !argument.catString.equals("S[asup]\\NP") 
				&& !argument.catString.equals("S[ng]\\NP")){
			return true;
		}
		return false;
	}
	public void treatRelPronoun(){	
		argument.argument = result.argument.copy();
		if (argument.argDir == BW)
			argument.argument.setToExtractedBounded();
		if (argument.argDir == FW)
			argument.argument.setToExtractedUnbounded();	
	}
	public boolean isFreeRelPronoun(){
		String plainCat = catStringNoFeatures();	
		if ((plainCat.equals("NP/(S\\NP)") || plainCat.equals("NP/(S/NP)"))
				&& !argument.catString.equals("S[adj]\\NP") 
				&& !argument.catString.equals("S[asup]\\NP") 
				&& !argument.catString.equals("S[ng]\\NP")){
			return true;	    
		}
		return false;
	}
	public void treatFreeRelPronoun(){	
		argument.argument = result.copy();
		if (argument.argDir == BW)
			argument.argument.setToExtractedBounded();
		if (argument.argDir == FW)
			argument.argument.setToExtractedUnbounded();
	}
	public boolean isSmallClausePP(){
		if (argument != null  && argDir == FW
				&& argument.matches(NP)
				&& !argument.catString().equals("N")
				&& result.argument != null
				&& result.argDir == FW
				&& result.argument.matches(VP)
				&& result.result != null
				&& ((result.result.argument != null
						&& result.result.argument.id == result.result.result.id)//isSmallClausePP 
						|| ( (result.result.matches("PP") ||
								result.result.catString.equals("S[for]")||
								result.result.matches("(S\\NP)\\(S\\NP)") ||
								result.result.matches("NP\\NP") || 
								result.result.matches("S\\S") || 
								result.result.matches("S/S") 
						)))
						&& catString().indexOf("[thr]") == -1 
						&& catString().indexOf("[expl") == -1 ){
			return true;
		}      	
		return false;
	}
	public void treatSmallClausePP(){
		argument.headId = result.argument.argument.headId;// smallClausePP	
		result.argument.argument.setToExtractedBounded();	
	}
	public boolean isToughAdj(){
		if (catString.equals("(S[adj]\\NP)/((S[to]\\NP)/NP)"))
			return true;
		else return false;
	} 
	public void treatToughAdj(){
		argument.argument = result.argument.copy();
		argument.argument.setToExtractedUnbounded();	
	}
	public boolean isToughVerb(){
		//(X takes Y weeks to complete)
		if (argument != null
				&& argument.catString.equals("NP")
				&& result.argument != null 
				&& result.argument.catString.equals("(S[to]\\NP)/NP")
				&& result.result.argument != null
				&& result.result.argument.catString.equals("NP")
				// not expletive subject! 
		){
			return true;
		}
		else return false;
	}
	public void treatToughVerb(){
		result.argument.argument = result.result.argument.copy();
		result.argument.argument.setToExtractedUnbounded();	
	}
	public boolean isDet(){
		if (catString.equals("NP[nb]/N")) return true;
		return false;
	}
	public void treatDet(){
		result.headId = argument.headId;// determiner
		result.heads = null;	
	}
	public void treatPOS(){
		result.result.headId = result.argument.headId;//POS
		result.result.heads =null;
	}
	/** true for non-topicalized verbs -- with non-expletive subject (new!)*/
	public boolean isVerb(){
		CCGcat target = target();
		if (target.matches(S)
				&& !target.catString.equals(S)
				&& !target.catString.equals("S[adj]")
				&& target.function != null
				&& target.function.argDir == BW
				&& target.function.argument.matches(NP)
				&& !target.function.argument.catString.equals("NP[expl]") 
				&& !target.function.argument.catString.equals("NP[thr]")){
			return true;
		}
		return false;
	}
	public boolean isSbjExtractionVerb(){
		if (matches("((S\\NP)/NP)/(S\\NP)") && argument.catString.equals("S[dcl]\\NP")){
			return true;
		}
		return false;	
	}
	public void treatSbjExtractionVerb(){	
		argument.argument = result.argument.copy();	
		argument.argument.setToExtractedUnbounded();
	}
	public boolean isOtherObjectControlVerb(){
		CCGcat tmp = target().function.function;
		CCGcat complVP = null;
		CCGcat complNP = null;
		while (tmp != null && tmp.argument != null){

			if (tmp.argument.matches(VP)  
					// "adjectives" can also be particles
					//&&tmp.argument.catString.equals("S[adj]\\NP")
			){
				complVP = tmp.argument;
			}
			if (tmp.argument.matches(NP)
					&& !tmp.argument.catString().equals("NP[expl]")
					&& !tmp.argument.catString().equals("NP[thr]")){
				complNP = tmp.argument;		
			}
			tmp = tmp.function;
		}
		if (complVP != null && complNP != null){	    	   
			complVP.argument = complNP.copy();
			complVP.argument.setToExtractedBounded();
			return true;
		}
		return false;
	}
	public void treatComplementVPs(){
		//verbs that subcategorize for expletives are different.. 
		if (this.catString().indexOf("[expl]") != -1){
			return;
		}
		CCGcat sbjNP = target().function.argument;

		CCGcat tmp = target().function.function;
		while (tmp != null){
			if (tmp.argument.matches(VP)){
				tmp.argument.argument = sbjNP.copy();
				tmp.argument.argument.setToExtractedBounded();
			}
			tmp = tmp.function;
		}
	}
	public boolean isAuxModal(){
		if (!catString.equals("(S\\NP)/(S\\NP)")
				&& !catString.equals("(S[adj]\\NP)/(S[adj]\\NP)") 	   
				&& matches("(S\\NP)/(S\\NP)")
				&& !result.catString.equals("S[adj]\\NP") 	
				&& !result.argument.catString.equals("NP[expl]")
				&& !result.argument.catString.equals("NP[thr]")){
			return true;
		}
		return false;
	}
	public void treatAuxModal(){	 
		// "to" doesn't introduce a dependency on the subject
		if (!result.catString.equals("S[dcl]\\NP")){
			if (result.catString.equals("S[to]\\NP")){	  
				result.argument.dependencies = null; 	 
			}
			if (headWord().equals("be") 
					|| headWord().equals("been") 
					|| headWord().equals("have")
					|| headWord().equals("going")){
				result.argument.dependencies = null; 	 
			}
		}
		argument.argument = result.argument.copy();
		argument.argument.setToExtractedBounded();
	}
        
        // Ambati
        public boolean isAux(){
            boolean isaux = false;
            if(isAuxModal()){
                if(result.catString.equals("S[dcl]\\NP") && (!argument.catString.equals("S[to]\\NP")
                        && !argument.catString.equals("S[adj]\\NP") //&& !argument.catString.equals("S[pss]\\NP")
                        ))
                    isaux = true;
            }
            return isaux;
        }
        
        // Ambati
        public void handleAux(){
                    
            ///*
            //if (!result.catString.equals("S[to]\\NP") && !argument.catString.equals("S[to]\\NP")){
                result.result.headId = argument.result.headId;
                result.headId = argument.headId;
                result.heads = argument.heads;
                result.result.heads = argument.result.heads;
            //}
            //*/
        }
        
	public boolean isControlVerb(){
		if ( !catString.equals("((S\\NP)/(S\\NP))/NP")
				&& !catString.equals("((S[adj]\\NP)/(S[adj]\\NP))/NP")
				&& !catString.startsWith("((S[pss]\\NP)")
				&& matches("((S\\NP)/(S\\NP))/NP")
				&& catString.indexOf("[expl]") == -1
				&& catString.indexOf("[thr]") == -1){
			return true;
		}
		return false;
	}
	public boolean isSubjectControl(){
		if (isControlVerb() && isSubjectControlVerb()) return true;
		return false;
	}
	private boolean isSubjectControlVerb(){
		if (headWord().startsWith("promis")) return true; 
		else return false;
	}
	public void treatSubjectControl(){
		// the only case is promised|((S[pss]\NP)/(S[to]\NP))/NP, which is an odd category! 	
		result.argument.argument.headId = result.result.argument.headId;//sbj control
		result.argument.argument.setToExtractedBounded();
	}
	public boolean isObjectControl(){
		if (isControlVerb() && !isSubjectControlVerb()) return true;
		return false;
	}
	public void treatObjectControl(){	
		result.argument.argument = argument.copy();
		result.argument.argument.setToExtractedBounded();	
	}
	public boolean isYNQCategory(){
		// true of (S[q]/(S[xxx]\NP))/NP
		if (argument != null 
				&& result.argument != null
				&& result.argument.argument != null
				&& argument.matches(NP)
				&& !argument.catString().equals("NP[expl]")
				&& result.argument.matches(VP)
				&& result.result.catString.equals("S[q]")){
			return true;
		}	
		return false;
	}
	public void treatYNQCategory(){ 		
		result.argument.argument = argument.copy();
		result.argument.argument.setToExtractedBounded();	
	}
	public boolean isNonStandardPiedPipingCategory(){
		if (argument != null
				&& argument.matches("(NP\\NP)/NP")
				&& result != null
				&& (result.argument.matches(VP) || result.argument.matches("S//NP"))
				&& (target().matches(NP) || target().matches(NOUN)) ){
			return true;
		}	
		return false;
	}
	public void treatNonStandardPiedPipingCategory(){
		//((NP\NP)/(S[to/dcl]\NP))\((NP\NP)/NP)
		//"some of whom are suing"
		// the NP of the preposition is the same as the subject NP;
		if (target().matches(NP) || target().matches(NOUN)){
			// get the NP as argument of the preposition
			argument.argument.headId = target().function().argument.headId;
			argument.argument.setToExtractedBounded();
			// get the NP as argument of the verb 
			if (!result.argument.catString().equals("S[to]\\NP")){
				result.argument.argument.headId = target().function().argument.headId;
				if (result.argument.argDir == BW)
					result.argument.argument.setToExtractedBounded();
				else result.argument.argument.setToExtractedUnbounded();
			}
		}     
	}
	public boolean isPiedPipingAdjunctExtractionCategory(){
		if (argument != null
				&& argument.matches("(NP\\NP)/NP")
				&& result != null
				&& result.argument.matches(S)
				&& (target().matches(NP) || target().matches(NOUN)) ){
			return true;
		}	    
		return false;
	}
	//((NP\NP)/S[dcl])\((NP\NP)/NP)
	public void treatPiedPipingAdjunctExtractionCategory(){
		// the NP of the preposition is the same as the subject NP;
		if (target().matches(NP) || target().matches(NOUN)){
			//argument.argument = target().function().argument.copy();
			argument.argument.headId = target().function().argument.headId;
			argument.argument.setToExtractedBounded();
		}
	}
	// under whose auspices:(((NP\NP)/S[dcl])\((NP\NP)/NP))/N
	public boolean  isPiedPipingRelPronCategory(){
		if (argument != null 
				&& argument.matches(NOUN) 
				&& result != null 
				&& result.argument != null && result.argument.matches("(NP\\NP)/NP")
				&& result.result != null 
				&& result.result.argument != null 
				&& result.result.argument.matches("S")
				&& result.result.result != null 
				&& result.result.result.matches("NP\\NP")){
			return true;
		}
		return false;
	}
	public void treatPiedPipingRelPronCategory(){
		result.argument.argument.headId = argument.headId;
		result.argument.argument.setToExtractedBounded();
	}  
	//((NP\NP)/(S[dcl]\NP))\(NP/NP))/N  whose
	// Ruth Messinger, some of whose programs have made..."
	public boolean  isPiedPipingSBJRelPronCategory(){
		if (argument != null 
				&& argument.matches(NOUN) 
				&& result != null 
				&& result.argument != null 
				&& result.argument.matches("NP/NP")
				&& result.result != null 
				&& result.result.argument != null && result.result.argument.matches("S\\NP")
				&& result.result.result != null && result.result.result.matches("NP\\NP")){
			return true;
		}
		return false;
	}
	//((NP\NP)/(S[dcl]\NP_j))\(NP_j/NP_i))/N_i  whose
	// Ruth Messinger, some of whose programs have made..."
	public void treatPiedPipingSBJRelPronCategory(){
		argument.headId =     result.argument.argument.headId;
		result.argument.argument.setToExtractedBounded();
		if (result.result.argument.matches("S\\NP") && !result.result.argument.equals("S[to]\\NP")){
			result.result.argument.argument.headId = result.argument.result.headId;
			result.result.argument.argument.setToExtractedBounded();
		}
	}
	public boolean isStandardPiedPipingCategory(){
		if(argument != null && result != null && result.argument == null && argument.matches("NP/NP"))
			System.err.println("debug");
		if (argument != null
				&& argument.matches("NP/NP")
				&& result != null
				&& (result.argument.matches(VP) || result.argument.matches("S//NP"))
				&& (target().matches(NP) || target().matches(NOUN)) ){
			return true;
		}	    
		return false;
	}
	public void treatStandardPiedPipingCategory(){ 
		//"some of whom..." (((N\N_j)/(S[dcl]\NP_i))\(NP_i/NP_j))
		// the NP of the preposition is the same as the subject NP;
		if (target().matches(NP) || target().matches(NOUN)){
			argument.headId = result.argument.argument.headId;
			result.argument.argument.setToExtractedBounded();
			// get the dependency for the PP right
			argument.argument.headId = target().function().argument.headId;
			argument.argument.setToExtractedBounded();
		}     
	}
	public boolean isPiedPipingEmbeddedQuestionCategory(){
		String catString = catString();
		if (catString != null && catString.equals("((S[qem]/S[dcl])\\((NP\\NP)/NP))/N"))
			return true;
		else return false;
	}
	public void treatPiedPipingEmbeddedQuestionCategory(){
		result.argument.argument.headId = argument.headId;
		result.argument.argument.setToExtractedBounded();
	}

	//##################################################
	// READ IN A CATEGORY FROM A STRING
	//##################################################

	/**parseCat(String cat)
       This works only if cat really spans an entire category     */
	public static CCGcat parseCat(String cat){    
		// Create a new category  
		if (cat.endsWith(conjFeature)){// otherwise it might crash
			int index = cat.lastIndexOf(conjFeature);
			cat = cat.substring(0, index);  
		};

		CCGcat newCat = new CCGcat(cat);
		// CASE 1: No brackets
		if (cat.indexOf(OB) == -1 && cat.indexOf(CB) == -1){
			// CASE 1(a): And no slashes
			if (cat.indexOf(fslash) == -1 && cat.indexOf(bslash) == -1){
				// ==> newCat is atomic category
			}
			// CASE 1(b): a slash
			else {
				int slashIndex = 0;
				if (cat.indexOf(fslash) == -1 && cat.indexOf(bslash) != -1){		  
					slashIndex = cat.indexOf(bslash);
					newCat.argDir = BW;
				}
				if (cat.indexOf(bslash) == -1 && cat.indexOf(fslash) != -1){		  
					slashIndex = cat.indexOf(fslash);
					newCat.argDir = FW;
				}
				// Recurse on rescat 
				CCGcat resCat = parseCat(cat.substring(0, slashIndex)); 
				resCat.function = newCat;
				newCat.result = resCat;			      
				// Recurse on argcat
				CCGcat argCat = parseCat(cat.substring(slashIndex+1)); 
				argCat.function = newCat;     
				newCat.argument = argCat;	     		      
			}
		}         
		//CASE 2: Brackets
		else { 
			int obNumber = 0;    // the number of unclosed open brackets
			int start = 0;       // the start of a new category
			int end = 0;         // the end of a new category

			// Iterate through the characters in the string
			for (int i = 0; i < cat.length(); i++){
				// If: this character is an open bracket
				// Then: if there are no other unclosed open brackets, 
				//       then: the next character starts a new category
				// - also: increment the number of unclosed open brackets
				if (cat.charAt(i) == OB){
					if (obNumber == 0) start = i+1; 	 
					obNumber++;
				}
				// If: this character is a forward slash 
				//     and there are no unclosed open brackets
				// Then: this is the end of the result category
				if (cat.charAt(i) == fslash && obNumber == 0){
					newCat.argDir = FW;
					if (newCat.result == null){
						end = i;
						CCGcat resCat =  parseCat(cat.substring(start, end));
						resCat.function = newCat;
						newCat.result = resCat;	
					}
					start = i+1;
					end = i+1;	  
				}
				// If: this character is a backward slash 
				//     and there are no unclosed open brackets
				// Then: this is the end of the result category
				if (cat.charAt(i) == bslash && obNumber == 0){
					newCat.argDir = BW; 
					if (newCat.result == null){
						end = i;
						CCGcat resCat =  parseCat(cat.substring(start, end));
						resCat.function = newCat;
						newCat.result = resCat;	
					}
					start = i+1;
					end = i+1;
				}	  
				// If this is a closing bracket:
				// Then: decrement the number of open unclosed brackets
				if (cat.charAt(i) == CB ){ 
					obNumber--;
					if (obNumber == 0 ){
						end = i; 		      		      
						if (newCat.result == null){		       
							CCGcat resCat =  parseCat(cat.substring(start, end));
							resCat.function = newCat;
							newCat.result = resCat;	    
						}
						else {
							CCGcat argCat = parseCat(cat.substring(start, end)); 
							argCat.function = newCat;
							newCat.argument = argCat;			  		  
						}
					}
				}
				// If this is  the end of the string
				if (i == cat.length()-1 && cat.charAt(i) != CB){ 		  		
					end = i+1;
					if (newCat.result == null){		       
						CCGcat resCat =  parseCat(cat.substring(start, end));
						resCat.function = newCat;
						newCat.result = resCat;	    
					}
					else {
						CCGcat argCat = parseCat(cat.substring(start, end));
						argCat.function = newCat;
						newCat.argument = argCat;			  		  
					}
				}
			}
		}
		return newCat;
	}

	//##########################################################
	//    COPY    
	//##########################################################    
	public CCGcat copy(){
		CCGcat copy = null;
		try{copy = (CCGcat)this.clone();}
		catch (Exception E) {E.printStackTrace();}
		CCGcat tmp, tmpcopy;
		tmp = this;
		tmpcopy = copy;
		tmpcopy.copyHeadsDeps(tmp);
		if (tmp.argument != null){
			tmpcopy.argument = tmp.argument.copy();	  	  
			tmpcopy.result = tmp.result.copy();	  
			tmpcopy.result.function = tmpcopy;	    
		}
		return copy;
	}

	public void copyHeadsDeps(CCGcat original){
		this.headId = original.headId;
		if (original.heads() != null)
			this.heads = original.heads().copy();
		else this.heads = null;
		if (original.deps() != null)
			this.dependencies = original.deps().copy();
		if (original.filledDependencies != null)
			this.filledDependencies = original.filledDependencies.copy();
		else this.filledDependencies = null;
	}

	//##########################################################
	//   MATCHING, UNIFICATION 
	//##########################################################    
	public boolean matches(CCGcat cat){
		boolean retval = false;
		// strict equality matches
		if (this.catString().equals(cat.catString())
				|| (this.catString().equals(NOUN) && cat.catString().equals(NP))
				|| (this.catString().equals(NP) && cat.catString().equals(NOUN))){ 
			retval = true;
		}
		else{
			// no features match any features
			if (this.catStringNoFeatures().equals(cat.catString())){
				retval = true;
			}
			else {
				// and any features match no features
				if (cat.catStringNoFeatures().equals(this.catString())){
					retval = true;
				}
				else{
					// but if both have features, then we need a more thorough check!
					if (cat.catStringNoFeatures().equals(this.catStringNoFeatures())){		  
						retval = matchRecursively(cat);			  		    
					}	      
				}	    
			}
		}	
		return retval;
	}

	public boolean matchRecursively(CCGcat cat){
		boolean retval = false;  
		// strict equality matches 
		// no features match any features
		if (cat != null){
			if ( this.catString().equals(cat.catString())
					|| this.catStringNoFeatures().equals(cat.catString())
					|| cat.catStringNoFeatures().equals(this.catString())){
				retval = true;
			}
			else { 	     
				if (result != null && argument != null) {
					retval = argument.matchRecursively(cat.argument);	    
					if (retval == true){		  
						retval = result.matchRecursively(cat.result);		  
					}      
				}
			}
		}
		return retval;    
	}

	public boolean matches(String catString1){
		if (this.catString != null){	  
			if (catString.equals(catString1)) return true;
			else {
				if ((catString.equals(NOUN) && catString1.equals(NP))
						|| (catString.equals(NP) && catString1.equals(NOUN))){
					return true;		    
				}
				else if (catStringNoFeatures().equals(noFeatures(catString1)) ){
					return true;
				}
			}
		}
		return false;
	}


	//##################################################
	// METHODS FOR CATSTRING
	// - FORWARD, BACKWARD
	// - BRACKETING
	// - REPARSE CATSTRING 
	//##################################################
	public String forward(String cat1, String cat2){
		String tmp1, tmp2;
		if (needsBrackets(cat1)){
			tmp1 = bracket(cat1);
		}
		else tmp1 = cat1;
		if (needsBrackets(cat2)){
			tmp2 = bracket(cat2);
		}
		else tmp2  = cat2;
		return (new StringBuffer(tmp1).append(fslash).append(tmp2)).toString();
	}

	public String backward(String cat1, String cat2){
		String tmp1, tmp2;
		if (needsBrackets(cat1)){
			tmp1 = bracket(cat1);
		}
		else tmp1 = cat1;
		if (needsBrackets(cat2)){
			tmp2 = bracket(cat2);
		}
		else tmp2 = cat2;
		return (new StringBuffer(tmp1).append(bslash).append(tmp2)).toString();
	}

	/** bracket */
	public String bracket(String cat){
		return (new StringBuffer("(").append(cat).append(CB)).toString();
	}

	/** boolean: needs brackets? **/
	public boolean needsBrackets(String cat){
		if (cat.indexOf(bslash) != -1 || cat.indexOf(fslash) != -1){
			return true;
		}
		else return false;
	}

	/** reparse the cat string in complex category */
	public void catStringReparse(){
		if (result != null){
			result.catStringReparse();
			if (argDir == FW){
				this.catString = forward(result.catString, argument.catString);
			}
			else {
				if (argDir == BW){
					this.catString = backward(result.catString, argument.catString);
				}
				else { 
					System.err.println("catStringReparse ERROR: no direction!");
				}
			}
			if (result.function != this){
				System.err.println("ERROR: result.function != this");
			}
		}
	}

	public static int newId(){
		return ++idCounter;
	}

	public static int newHeadId(){
		return ++headIdCounter;
	}

	//##########################################################
	//##########################################################
	//##
	//##  THE COMBINATORY RULES
	//##
	//##########################################################
	//##########################################################
	public static CCGcat typeRaiseTo(CCGcat X, String typeRaisedX){
		CCGcat typeRaised = null;
		if (typeRaisedX.equals("S/(S/NP)")){
			typeRaised = X.topicalize(S);	    	    
		}
		else{	    
			if (typeRaisedX.indexOf('/') > -1 && typeRaisedX.indexOf('\\') > -1){
				CCGcat tmp = parseCat(typeRaisedX);
				if (tmp.argument.result != null
						&& tmp.argument.result.catString.equals(tmp.result.catString)){
					typeRaised = X.typeRaise(tmp.result.catString, tmp.argDir);		
				}	    
			}
		}
		return typeRaised;
	}

	// in LEFT node raising (Y X\Y conj X\Y),
	// all backward args in the second conjunct are long-range deps
	public void adjustLongRangeDepsLNR(){
		CCGcat tmp = this;
		if (argument != null && argDir == FW){
			tmp = tmp.result;
			while (tmp.argument != null
					&& tmp.argDir == FW){
				tmp = tmp.result;
			}
		}
		if (tmp.argument != null
				&& tmp.argDir == BW
				&& tmp.argument.dependencies != null){
			tmp.adjustLongRangeDepsLNRRec();	   
		}
	}

	public void adjustLongRangeDepsLNRRec(){
		if (argument != null && argDir == BW
				&& argument.dependencies != null){
			argument.dependencies.setToExtractedUnbounded();
			result.adjustLongRangeDepsLNRRec();
		}
	}
	// in RIGHT node raising (X/Y conj X/Y Y),
	// all forward args are long-range deps 
	public void adjustLongRangeDepsRNR(){
		if (argument != null
				&& argDir == FW
				&& argument.dependencies != null){
			argument.dependencies.setToExtractedUnbounded();
			result.adjustLongRangeDepsRNR();
		}
	}

	public static CCGcat conjoin(CCGcat leftCat, CCGcat rightCat, String resultCatString){
		CCGcat resultCat = null;
		return resultCat;
	}

	/**  
	 * COMBINING TWO CONSTITUENTS TO OBTAIN A CONSTITUENT WITH CATEGORY resultCatString
	 */
	public static CCGcat combine(CCGcat leftCat, CCGcat rightCat, String resultCatString){
		CCGcat resultCat = null;
		if (leftCat != null && rightCat != null){
			// INTERMEDIATE STAGE IN CONJUNCTION?
			if (resultCatString.endsWith(conjFeature) || 
					(leftCat.catString().equals("conj") && rightCat.matches(resultCatString)) ||
					(rightCat.catString().equals("conj") && leftCat.matches(resultCatString))){
				// X[conj] --> conj X
				// intermediate stage in coordination
				resultCat = conjunction(rightCat, leftCat);

				if (resultCat != null){		    
					resultCat.adjustLongRangeDepsLNR();		    
					if (!resultCat.matches(resultCatString))
						resultCat = typeChangingRule(rightCat, resultCatString);
					resultCat.catString = resultCatString;
				}
				if (resultCat == null){// X[conj] --> X conj
					// intermediate stage in coordination: X -> X conj. 
					// This is there due to noise in CCGbank.
					// ATM, 53 such instances in wsj02-21.
					resultCat = conjunction(leftCat, rightCat); 
					if (resultCat != null){  
						if (!resultCat.matches(resultCatString)){
							resultCat = typeChangingRule(leftCat, resultCatString);			
						}
						resultCat.catString = resultCatString;
					}
				}
			}
			else { 
				if (rightCat.catString().endsWith(conjFeature)){// X ==> X X[conj]
					// the actual coordination step
					resultCat = coordinate(leftCat, rightCat);
					if (resultCat == null){// X --> , X[conj]
						resultCat = punctuation(rightCat, leftCat);
						if (resultCat != null){
							if (!resultCat.matches(resultCatString)){
								resultCat = typeChangingRule(leftCat, resultCatString);			
							}
							resultCat.catString = resultCatString;
						}
					}
				}
				else {
					if (leftCat.argDir == FW){
						resultCat = apply(leftCat, rightCat);

						if (resultCat != null && resultCat.matches(resultCatString) && !resultCat.equals(resultCatString)){
							resultCat.catString = resultCatString;// NP[nb] etc.
						}
					}
					if (resultCat == null && rightCat.argDir == BW){
						resultCat = apply(rightCat, leftCat);
						if (resultCat != null && resultCat.matches(resultCatString) && !resultCat.equals(resultCatString)){
							resultCat.catString = resultCatString;// NP[nb] etc.
						} 		
					}
					if (resultCat == null){// X ,
						resultCat = punctuation(leftCat, rightCat);		  
						if (resultCat != null){
							if ( !resultCat.catString().equals(resultCatString)){       	    
								resultCat = typeChangingRule(leftCat, resultCatString);
							}      
						}
					}
					if (resultCat == null){// , X
						resultCat = punctuation(rightCat, leftCat);
						if (resultCat != null){
							if ( !resultCat.catString().equals(resultCatString)){		 
								resultCat = typeChangingRule(rightCat, resultCatString);		   
							}
						}
					}	   
					if (resultCat == null && leftCat.argDir == FW){
						resultCat = compose(leftCat, rightCat);
						if (resultCat != null && !resultCat.matches(resultCatString)){
							resultCat = null;
						}
						else {
							if (resultCat != null && !resultCat.equals(resultCatString)){
								resultCat.catString = resultCatString;
							}
						}
					}
					if (resultCat == null&& rightCat.argDir == BW ){
						resultCat = compose(rightCat, leftCat);	
						if (resultCat != null && !resultCat.matches(resultCatString)){
							resultCat = null;
						}
					}
					if (resultCat == null){
						resultCat = substitute(rightCat, leftCat);
					}
					if (resultCat == null){
						resultCat = substitute(leftCat, rightCat);
					}
					if (resultCat == null
							&& leftCat.catString().equals(resultCatString)
							&& rightCat.catString().equals(resultCatString)){// X ==> X X
						// an error in the treebank, but this is also like coordination
						// note: if the cats are adjuncts, they will have composed already
						resultCat = coordinate(leftCat, rightCat);
					}
				}	    
			}
			if (resultCat == null && leftCat != null && rightCat != null){		
			}
			else{
				if (resultCat != null && !resultCat.matches(resultCatString)){
					resultCat = null;	    
				}
			}
		}
		return resultCat;	
	}

	/** sets the <tt>headId</tt> of this category and recursively of all result categories of this category to the same number (<tt>headIdNumber</tt>) */
	public void setHeadId(int headIdNumber){
		headId = headIdNumber;//setHeadId
		if (result != null){
			result.setHeadId(headIdNumber);
		}
	}

	//##################################################
	// APPLICATION
	//##################################################    
	/** Function application.
	Unify the argument of the functor with arg,
	and return the result of the functor. */

	public static CCGcat apply(CCGcat functor, CCGcat arg){      
		if (functor.argument != null && functor.argument.matches(arg)){// was arg
			CCGcat result = functor.result.copy();	    
			result.function = null;
			// System.out.println("Application: intermediate category result");
			//result.printCat();
			CCGcat Y = unify(functor.argument, arg);
			result.replace(functor.argument, Y);
			result.filledDependencies = Y.filledDependencies;	    
			Y.filledDependencies = null;

			if (functor.argDir == FW){
				if (functor.catString.equals("NP[nb]/N"))
					result.catString = NP;      	     
			}
			return result;
		}
		else return null;
	}

	//##################################################
	// TYPERAISE
	//##################################################
	/** Typeraising -- typeraise the current category. 
	The current category together with <tt>T</tt> 
	and the direction specify the type-raised category.
	 */	
	public  CCGcat typeRaise(String T, int direction){
		CCGcat t = parseCat(T);
		t.setHeadId(++headIdCounter);//typeraise
		CCGcat newArg = new CCGcat(t.copy(), this);
		newArg.headId = t.headId;//typeraise: argument
		// newArg = T|X
		CCGcat resCat = new CCGcat(t.copy(), newArg);	
		// resCat = T|(T|X);
		resCat.argument.result = resCat.result;
		if (direction == FW){
			newArg.argDir = BW;
			resCat.argDir = FW;	  
			newArg.catString = backward(t.catString, this.catString);	 
			resCat.catString = forward(t.catString, newArg.catString); 	    
		}
		else{  
			newArg.argDir = FW;
			resCat.argDir = BW;  
			newArg.catString = forward(t.catString, this.catString);
			resCat.catString = backward(t.catString, newArg.catString);
		}
		resCat.function = null;	
		resCat.heads = this.heads();
		resCat.headId = this.headId;//typeRaise
		resCat.filledDependencies = null;
		return resCat;      
	}
	//##################################################
	// TOPICALIZATION
	//##################################################
	/** topicalization -- similar to type-raising, but yields S/(S/X)*/
	public  CCGcat topicalize(String S){
		CCGcat t = parseCat(S);
		t.setHeadId(++headIdCounter);//topicalize	
		CCGcat newArg = new CCGcat(t.copy(), this);
		newArg.headId = t.headId;
		// newArg = S|X
		CCGcat resCat = new CCGcat(t.copy(), newArg);	
		// resCat = S|(S|X);
		resCat.argument.result = resCat.result;    
		newArg.argDir = FW;
		resCat.argDir = FW;

		newArg.catString = forward(t.catString, this.catString);	 
		resCat.catString = forward(t.catString, newArg.catString); 	    

		resCat.function = null;
		resCat.filledDependencies = null;// new
		resCat.heads = this.heads();
		resCat.headId = this.headId;//topicalize
		return resCat;      
	}

	//##################################################
	// COMPOSE
	//##################################################

	/** (Generalized) function composition.
	Forward crossing composition is excluded at the moment. */
	public static CCGcat compose(CCGcat functor, CCGcat arg){
		// functor == X|Y
		// arg == Y|Z$
		// ==> resCat = X|Z$....

		CCGcat resultCat = null;
		if (functor.argument != null){
			resultCat =  arg.copy();
			resultCat.function = null;
			if (functor.heads != null){
				if (functor.isAdjunctCat()){
					resultCat.heads = arg.heads.copy();
				}
				else resultCat.heads = functor.heads.copy(); // hack!
			}
			// make tmp point to a category Y/Z$, 
			// then replace its result with X.
			CCGcat tmp = resultCat;
			CCGcat  functorArg = functor.argument();
			while (tmp != null
					&& tmp.result()!= null
					&& !tmp.result().matches(functorArg)){
				tmp = tmp.result();	   
			};

			if (tmp.result() != null &&
					tmp.result().matches(functorArg)
			){
				CCGcat functorCopy = functor.copy();
				CCGcat Y = unify(functorCopy.argument(), tmp.result);		
				functorCopy.replace(functorCopy.argument(), Y);		
				resultCat.replace(tmp.result(), Y);	  				
				tmp.result = functorCopy.result().copy();
				tmp.result.function = tmp;	 
				resultCat.filledDependencies = Y.filledDependencies;
				Y.filledDependencies = null;	    
				resultCat.catStringReparse();
				// adjust the head
				while (tmp != null){
					if (functorCopy.result.heads != null)
						tmp.heads = functorCopy.result.heads.copy();// again, this is just another copy
					tmp.headId = functorCopy.result.headId;//topicalize	
					tmp = tmp.function;
				};			
			}	    
			else resultCat = null;	
		}
		return resultCat;
	}
	/** coordinate two categories.
	Calls coordinateRec*/
	public static CCGcat coordinate(CCGcat cat1, CCGcat cat2){
		CCGcat coordination = null;
		if (cat1.matches(cat2.catString)){   
			coordination  =  cat1.copy();
			coordination.filledDependencies = null;
			// before recursing: adjust the long range dependencies:
			coordination.adjustLongRangeDepsRNR();	    
			coordination.coordinateRec(cat2);	   
		}	
		return coordination;
	}
	/** coordinate recursively with another category. Is called by coordinate */
	public void coordinateRec(CCGcat cat){
		if (argument != null){
			argument.coordinateRec(cat.argument);
		};
		if (result != null){
			if (result.id == argument.id){//coordination 
				result = argument.copy();
				result.dependencies = null;
			}
			else result.coordinateRec(cat.result);
		}
		appendHeads(cat.heads());
		appendDeps(cat.deps());
	}
	/** Standard substitution:  cat1 = (X\Y)/Z cat2=Y/Z ==> X/Z
     The result Z is  Z1 and Z2 coordinated.*/    
	public static CCGcat substitute(CCGcat cat1, CCGcat cat2){
		CCGcat result = null;
		if (cat1.argument != null
				&& cat1.result.argument != null
				&& cat1.result.result != null
				&& cat2.argument != null
				&& cat2.result != null
				&& cat1.argument.matches(cat2.argument)	    
				&& cat1.result.argument.matches(cat1.result.result)
				&& cat1.result.argument.matches(cat2.result)){
			CCGcat functor = cat1.copy();
			CCGcat arg = cat2.copy();
			CCGcat Z1 = functor.argument;
			CCGcat Y1 = functor.result.argument;
			CCGcat Z2 = arg.argument;
			CCGcat Y2 = arg.result;

			CCGcat Z3 = coordinate(Z1, Z2);	
			functor.replace(Z1.id, Z1.headId, Z3);// substitution
			arg.replace(Z2.id, Z2.headId, Z3);//substitution		
			CCGcat Y3= unify(Y1, Y2);
			functor.replace(Y1, Y3);	
			arg.replace(Y2, Y3);

			result  = functor.result();
			result.argDir = functor.argDir;
			result.argument = functor.argument;
			result.argument.function = result;
			result.catStringReparse(); 
			result.filledDependencies = Y3.filledDependencies;
			Y3.filledDependencies = null;

			if (result.result.heads != null){
				result.heads = result.result.heads.copy();
				result.headId = result.result.headId;	//coordination
			};

		}	
		return result;
	}
	/** A special rule for punctuation. Returns a copy of the main category. At the moment, does not change the yield. */
	public static CCGcat punctuation(CCGcat cat, CCGcat pct){
		CCGcat result = null;	
		if (pct.catString.equals(",") 
				|| pct.catString.equals(".")
				|| pct.catString.equals(";")
				|| pct.catString.equals(":") 
				|| pct.catString.equals("RRB") 
				|| pct.catString.equals("LRB")
				|| pct.catString.equals("``")
				|| pct.catString.equals("\'\'")
		){
			result =  cat.copy();
			result.filledDependencies = null;	   
		}
		return result;
	}
	/** Special rule for the intermediate stage of coordination. At the moment, the conjunctiun needs to have category <tt>conj</tt> -- if it is a comma or semicolon, we use the punctuation rule */
	public static CCGcat conjunction(CCGcat cat, CCGcat conjCat){
		CCGcat result = null;	
		if (conjCat.catString.equals("conj") 
				|| conjCat.catString.equals(",") 
				|| conjCat.catString.equals(";") 
				|| conjCat.catString.equals(":")
				|| conjCat.catString.equals(".")	
				|| conjCat.catString.equals("LRB")	
				|| conjCat.catString.equals("RRB")	   
		){	
			result =  cat.copy();
			result.filledDependencies = null;	
		}
		return result;	
	}

	/** Type-changing rules: change <tt>dtrCat</tt> to a category with string representation <tt>cat</tt>. 
	Copies the head and yield of dtrCat.
	Calls "assignHeadsDeps". */
	public static CCGcat typeChangingRule(CCGcat dtrCat, String cat){
		CCGcat newCat = null;

		if (cat != null && dtrCat != null){
			newCat = parseCat(cat);
			newCat.target().assignHeadsDeps(1, newCat.heads, newCat.headId);// TCR

			if (dtrCat.heads != null){
				newCat.heads = dtrCat.heads.copy();
			}
			newCat.filledDependencies = null;

			// if the argument of newCat matches the argument of dtrCat, then they are the same!
			if (newCat.argument != null
					&& dtrCat.argument != null
					&& newCat.argument.catString != null
					&& dtrCat.argument.catString != null
					&& newCat.argument.matches(dtrCat.argument.catString)
					&& !(dtrCat.catString.equals("S[to]\\NP") && cat.equals("NP\\NP"))
					&& !dtrCat.catString.equals("S[dcl]\\NP")
					&& !dtrCat.catString.equals("S[b]\\NP")
			){

				if(dtrCat.argument.dependencies != null){
					newCat.argument.dependencies 
					= dtrCat.argument.dependencies.copy();
					if (dtrCat.argDir == FW)
						newCat.argument.dependencies.setToExtractedUnbounded();
					if (dtrCat.argDir == BW)
						newCat.argument.dependencies.setToExtractedBounded();
				}	    	    
			}                        
                        // Bharat: Added for (S\NP)\(S\NP) -> S\S to have same dependencies
                        else if(dtrCat.catString.equals("(S\\NP)\\(S\\NP)") && cat.equals("S\\S")){
                            
                            if(dtrCat.argument.dependencies != null){
                                newCat.argument.dependencies
                                        = dtrCat.argument.dependencies.copy();
                                if (dtrCat.argDir == FW)
                                    newCat.argument.dependencies.setToExtractedUnbounded();
                                if (dtrCat.argDir == BW)
                                    newCat.argument.dependencies.setToExtractedBounded();
                            }
                        }
			else {
				// (S\NP)\(S\NP) ==> S\NP
				if (newCat.argument != null
						&& newCat.result != null
						&& dtrCat != null
						&& dtrCat.catString != null 
						&& dtrCat.matches(VP)	
						&& newCat.argument.matches(dtrCat.catString)
						&& newCat.result.matches(dtrCat.catString)
				){

					if(dtrCat.argument.dependencies != null){
						newCat.argument.argument.dependencies 
						= dtrCat.argument.dependencies.copy();
						newCat.argument.argument.dependencies.setToExtractedBounded();
						newCat.result.argument.dependencies 
						= dtrCat.argument.dependencies.copy();
						newCat.result.argument.dependencies.setToExtractedBounded();
					}	    	    
				}
			}
		}
		return newCat;	
	}

	public static CCGcat unify(CCGcat cat1, CCGcat cat2){		
		//assumes that cat1, cat2 have the same categories
		CCGcat newCat = null;
		DepList copiedUnfilled  = null;
		if (cat2.hasFeatures()){
			newCat = cat2.copy();	
			newCat.id =  newId();//unification -- case1

			// copiedUnfilled is a list of the unfilledDependencis in newCat
			if (newCat.filledDependencies != null)
				copiedUnfilled = newCat.filledDependencies.copyUnfilled();

			newCat.filledDependencies  = newCat.mergeWith(cat1, copiedUnfilled);	
		}
		else {
			newCat = cat1.copy();	
			newCat.id =  newId();//unification -- case2 
			// new
			if (newCat.filledDependencies != null)
				copiedUnfilled = newCat.filledDependencies.copyUnfilled();
			newCat.filledDependencies  = newCat.mergeWith(cat2, copiedUnfilled);
		}
		return newCat;
	}

	public boolean hasFeatures(){
		if (catString != null && catString.indexOf('[') > -1)
			return true;
		else return false;    
	}

	/** merge this category with cat recursively */
	public DepList mergeWith(CCGcat cat, DepList currentDeps){
		if (!this.hasFeatures() && cat.hasFeatures()){
			catString = new String(cat.catString);	   
		}	
		// RECURSION
		// 1. if the result has the same id as the argument,
		// then treat the argument first, and then copy it over to the result.
		if (argument != null && (argument.id == result.id || cat.argument.id == cat.result.id)){//mergeWith
			if (argument != null){
				currentDeps =  argument.mergeWith(cat.argument, currentDeps);
			}
			result = argument.copy();	  
		}
		// 2. otherwise, do the argument and result separately
		else {  
			if (argument != null){	
				currentDeps =  argument.mergeWith(cat.argument, currentDeps);		
			}
			if (result != null){  
				currentDeps =  result.mergeWith(cat.result, currentDeps);
			}
		}

		// THE BASE STEP: FOR EACH CATEGORY:

		// prepending: 
		if (cat.extracted && dependencies != null ){	    
			// if one of them is unbounded, the result is unbounded
			if (cat.bounded && this.bounded){
				dependencies.setToExtractedBounded();
			}
			else { dependencies.setToExtractedUnbounded();	    
			}
		}
		// new -- March 06: prepending the other way! 
		if (this.extracted
				&& cat.dependencies != null ){	    
			// if one of them is unbounded, the result is unbounded
			if (cat.bounded && this.bounded){
				cat.dependencies.setToExtractedBounded();
			}
			else {cat.dependencies.setToExtractedUnbounded();	    
			}
		}

		// COPY THE HEADS (fill the dependencies if they are there)	
		if (heads == null && cat.heads != null){	    
			heads = cat.heads.copy();
			headId = cat.headId; //TCR	    
			if (dependencies != null){       		
				currentDeps = applyDependencies(deps(), heads(), currentDeps);	
			}
		}
		else {	 
			appendHeads(cat.heads);			
			if (dependencies != null && heads != null){ 	    
				currentDeps = applyDependencies(deps(), heads(), currentDeps);	
			}
		}	

		// COPY THE DEPENDENCIES (fill the dependencies if they are there)
		if (dependencies == null && cat.dependencies != null){
			dependencies = cat.dependencies.copy();
			if (heads != null){	
				currentDeps = applyDependencies(deps(), heads(), currentDeps);	
			}
		} // IF THERE ARE MULTIPLE DEPENDENCIES: APPEND, THEN APPLY	
		else { 
			if (dependencies != null && cat.dependencies != null ){
				if (extracted){// the multiple dependencies are extracted!!! 
					// cat.dependencies.setToExtracted();
					if (bounded && cat.bounded){
						cat.dependencies.setToExtractedBounded();
					}
					else {cat.dependencies.setToExtractedUnbounded(); 		
					}
				}   
				appendDeps(cat.dependencies);	     
				if (heads != null){		 
					currentDeps = applyDependencies(deps(), heads(), currentDeps);		
				}
			}
		}
		return currentDeps;	 
	}

	/** recursive replacement of all the parts of cat1 with the corresponding parts of cat2 */
	public void replace(CCGcat cat1, CCGcat unifiedCat1){
		if (cat1 != null 
				&&  unifiedCat1 != null){	
			if (cat1.argument != null){
				replace(cat1.argument, unifiedCat1.argument);
			}
			if (cat1.result != null){
				replace(cat1.result, unifiedCat1.result);
			}	    
			replace(cat1.id, cat1.headId, unifiedCat1);//recursion in replace
		}
	}

	/** REPLACING catId with newCat and headID with newCat.head or deps
	 */
	public void replace(int catId, int headID, CCGcat newCat){
		/* If the catId matches the old catID: 

	   - replace old catId with new catId
	   - replace old headId with the new headId
	   - replace cat string with newCat.catString if that one has features
	   - copy the heads across from newCat if newCat provides the head.
		 * COPY the dependencies if newCat provides the dependencies, 
	     else set them to  null (debug?)

	    If the headId matches the old headId: 
	   - replace old headId with the new headId
	   - copy the heads across from newCat if newCat provides the head.
		 * ADD the dependencies from newCat if the categories match (and otherwise???) 
		 */
		// Case 1: it could be the category itself!
		if (this.id == catId){// replace: the category id matches the one to be replaced  
			this.id = newCat.id;// replace old by new 
			this.headId = newCat.headId;// replace old by new

			if (!hasFeatures() && newCat.hasFeatures()){
				catString = newCat.catString;	      
			}
			if (newCat.heads != null){		
				this.heads = newCat.heads.copy();		
			}	    	   
			if (newCat.dependencies != null){		
				this.dependencies = newCat.dependencies.copy();		
			}
			else dependencies = null;
			if (newCat.extracted) {
				if (newCat.bounded) this.setToExtractedBounded();
				else this.setToExtractedUnbounded();		
			}
		}	
		else {
			// If the categories have the same headId, but aren't the same category:
			// then instantiate the head if newCat's head is instantiated:

			if (this.headId == headID //replace: (old) head ids are the same
					&& this.id != newCat.id){// replace: heads same, but cats not
				// copy the head across		
				if (newCat.heads != null && this.heads == null){
					this.heads = newCat.heads.copy(); 		  	    
				}		
				headId = newCat.headId;	// replace old headId with new   

				// add the dependencies, but don't change the 'extracted' feature on these cate  here. 		   
				if (this.matches(newCat.catString)){
					appendDeps(newCat.dependencies);		    
				}				
			}

			// recursion on argument:
			if (argument != null){	     
				argument.replace(catId, headID, newCat);// replace: recursion on argument
			}
			// recursion on result: 
			if (result != null){	    	
				result.replace(catId, headID, newCat);// replace: recursion on result	 
			}
		}	
	}
        
        public int getHeadId(){
            return headId;
        }
        
        /*Get Indexed category with CCG dependencies*/
        public String getIndCatDeps(){
            String result = "";
            result = catStringRecIndexedX();
            String deps = getDeps().toString();
            result += ";;"+deps.substring(1, deps.length()-1);
            String head = "X"+headId;
            result = result.replaceAll(head, "_");
            return result;
        }
        
        /*Get Indexed category with CCG dependencies*/
        public String getIndCat(){
            String result = "";
            result = catStringRecIndexedX();
            String head = "X"+headId;
            result = result.replaceAll(head, "_");
            return result;
        }
        
        private void printDeps(DepList deps){
            while(deps!=null){
                //System.out.println(dep.argIndex+"\t"+dep.headIndex+"\t"+dep.headCat+"\t"+dep.argPos+"\t"+dep.extracted+"\t"+dep.bounded);
                //dep = dep.next();
                //IntegerObject par = dep.getParent().getVariableValue();
                //IntegerObject child = dep.getChild().getVariableValue();
                String rel = deps.headCat+"--"+String.valueOf(deps.argPos);
                String key = deps.headIndex+"--"+deps.argIndex;
                System.out.println(key+" "+rel);
                deps = deps.next();
            }
        }
        
        public static CCGcat forwardApplication(CCGcat leftCat, CCGcat rightCat){
            CCGcat resultCat = null;
            if (leftCat.argDir == FW)
                resultCat = apply(leftCat, rightCat);
            return resultCat;
        }
        
        public static CCGcat backwardApplication(CCGcat leftCat, CCGcat rightCat){
            CCGcat resultCat = null;
            if (rightCat.argDir == BW)
                resultCat = apply(rightCat, leftCat);
            return resultCat;
        }
        
        public static CCGcat forwardComposition(CCGcat leftCat, CCGcat rightCat){
            CCGcat resultCat = null;
            if (leftCat.argDir == FW)
                resultCat = compose(leftCat, rightCat);
            return resultCat;
        }
        
        public static CCGcat backwardComposition(CCGcat leftCat, CCGcat rightCat){
            CCGcat resultCat = null;
            if (rightCat.argDir == BW)
                resultCat = compose(rightCat, leftCat);	
            return resultCat;
        }
        
        public static CCGcat backwardCrossedComposition(CCGcat leftCat, CCGcat rightCat){
            CCGcat resultCat = null;
            if (rightCat.argDir == BW && leftCat.argDir == FW){
                if(rightCat.argument.matches(leftCat.result))
                    resultCat = compose(rightCat, leftCat);
            }                
            return resultCat;
        }
        
        public static CCGcat typeRaiseForwardComposition(CCGcat leftCat, CCGcat rightCat, String typed, int dir){
            CCGcat resultCat;
            CCGcat leftCatTyped = leftCat.typeRaise(typed, dir);
            resultCat = forwardComposition(leftCatTyped, rightCat);
            return resultCat;
        }
        
        public static CCGcat conjItermediate(CCGcat leftCat, CCGcat rightCat){
            CCGcat resultCat = null;
            if(leftCat.catString().equals("conj")){
                // intermediate stage in coordination (X[conj] --> conj X)
		resultCat = conjunction(rightCat, leftCat);
                if (resultCat != null){
                    resultCat.adjustLongRangeDepsLNR();
                    String resultCatString = resultCat.catString;
                    if(!resultCat.catString.endsWith("[conj]"))
                        resultCatString = resultCatString+"[conj]";
                    if (!resultCat.matches(resultCatString))
                        resultCat = typeChangingRule(rightCat, resultCatString);
                    resultCat.catString = resultCatString;
                }
            }
            return resultCat;
        }
        
        public static CCGcat conjFinal(CCGcat leftCat, CCGcat rightCat){
            CCGcat resultCat = null;
            if (rightCat.catString().endsWith(conjFeature)){
                // the actual coordination step (X ==> X X[conj] )
                resultCat = coordinate(leftCat, rightCat);
                if (resultCat == null && leftCat.catString.equals(",") ){// X --> , X[conj]
                    resultCat = punctuation(rightCat, leftCat);
                    String resultCatString = rightCat.catString.replace("[conj]", "");
                    if (resultCat != null){
                        if (!resultCat.matches(resultCatString)){
                            resultCat = typeChangingRule(leftCat, resultCatString);
                        }
                    }
                    resultCat.catString = resultCatString;
                }
            }
            return resultCat;
        }
        
        public static CCGcat punctConj(CCGcat leftCat, CCGcat rightCat) {
            CCGcat resultCat;
            // X[conj] --> , X
            // intermediate stage in coordination
            resultCat = conjunction(rightCat, leftCat);
            return resultCat;
        }
        
        public String catStringRecIndexedX(){
            if (argument != null){
                String argString = argument.catStringRecIndexedX();
		String resultString = result.catStringRecIndexedX();
		String slash = "/";
		if (argDir == BW)
                    slash = "\\"; 
		return  new String("(" + resultString + slash + argString + ")"+ "{X" + headId + "}") ;
            }
            else return  new String(catString() + "{X"  + headId + "}");
        }
        
        public void setHeads(HeadWordList list, int id){            
            heads = list;
            headId = id;
            if(result != null)
                result.setHeads(list, id);
        }
        private Set<String> getDeps(){
            
            if(this == null)
                return null;
            
            Set<String> depsSet = new TreeSet<>();
            
            depsSet.addAll(getDepsSet(dependencies));
            if(argument != null) depsSet.addAll(argument.getDeps());
            if(result != null) depsSet.addAll(result.getDeps());
            
            return depsSet;
        }
        
        private Set<String> getDepsSet(DepList deps){
            Set<String> depsSet = new TreeSet<>();
            while(deps != null){
                depsSet.add("_ " + deps.argPos + " X" + headId);
                deps = deps.next();
            }
            return depsSet;
        }
        
        public String toString(){
            return catString;
        }
        
        public static CCGcat ccgCatFromString(String catstr){
            CCGcat cat;
            cat = lexCat("", catstr);
            if(catstr.endsWith("[conj]")){
                cat.adjustLongRangeDepsLNR();
                cat = CCGcat.typeChangingRule(cat, catstr);
                cat.catString = catstr;
            }
            return cat;
        }
        
        public LinkedList<Integer> headIdList(){
            LinkedList<Integer> list = new LinkedList<>();
            if(heads == null) return list;
            HeadWordList hwlist = heads.copy();
            while(hwlist!=null){
                list.add(hwlist.index());
                hwlist = hwlist.next();
            }
            return list;
        }
        
        public static CCGcat changeVPcat(CCGcat cat){
            CCGcat ncat;
            ncat = cat.copy2();
            ncat.catString = ncat.catStringRec();
            return ncat;
        }
        
        public String catStringRec(){
            if (argument != null){
                int head = this.headId;
                String argString = argument.catStringRec(head);
                String resultString = result.catStringRec(head);
                String slash = "/";
                if (argDir == BW)
                    slash = "\\";
                return  new String(resultString + slash + argString) ;
            }
            else return  catString();
	};
        
        public String catStringRec(int head){
		String thisCatString = null;
		// A complex category...
		if (argument != null){
			String argString = argument.catStringRec(head);
			String resultString = result.catStringRec(head);
			String slash = "/";
			if (argDir == BW)
				slash = "\\"; 
                        thisCatString =   new String("("+ resultString + slash + argString +")") ;

		}
		// An atomic category...
                else
                    thisCatString = catString();
		return thisCatString;
	};

        public CCGcat catForReveal(String wrd, String rcatstr, int index){
            //CCGcat ncat = typeChangingRule(this, rcatstr);
            CCGcat ncat = lexCat(wrd, rcatstr, index);
            DepList list = ncat.argument.dependencies;
            DepList nlist = list;
            while(nlist != null){
                nlist.headCat = this.heads.lexCat;
                nlist = nlist.next;
            }
            ncat.argument.dependencies = list;
            ncat.heads = this.heads;
            ncat.result.heads = this.heads;
            return ncat;
        }
        
        public CCGcat revealCat(CCGcat cat, String rcatstr){
            CCGcat ncat = typeChangingRule(cat, rcatstr);
            //ncat.heads = this.heads;
            ncat.result.heads = ncat.heads;
            return ncat;
        }
        
        public CCGcat copy2(){
		CCGcat copy = null;
		try{copy = (CCGcat)this.clone();}
		catch (Exception E) {E.printStackTrace();}
		CCGcat tmp, tmpcopy;
		tmp = this;
		tmpcopy = copy;
		tmpcopy.copyHeadsDeps(tmp);
		if (tmp.argument != null){
                        if(noFeatures(tmpcopy.catString).equals("(S\\NP)\\(S\\NP)")){
                            tmpcopy.catString = tmpcopy.result.result.catString+"\\"+tmpcopy.argument.result.catString;
                            tmpcopy.argument = tmp.argument.result.copy2();
                            tmpcopy.argument.dependencies = tmp.argument.dependencies;
                            tmpcopy.result = tmp.result.result.copy2();
                            tmpcopy.result.function = tmpcopy;
                        }
                        else{
                            tmpcopy.argument = tmp.argument.copy2();
                            tmpcopy.result = tmp.result.copy2();
                            tmpcopy.result.function = tmpcopy;
                        }
		}
		return copy;
	}
}