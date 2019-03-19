//===================================================
//  File: /home/julia/CCG/code/MyParsers/DependencyModel/code/HeadWordList.java
//  Author: Julia Hockenmaier                                
//  Purpose:                                                  
//  Created: Mon Dec 11 18:09:21 2000                         
//  Modified: Thu Apr  7 15:43:44 2005 (juliahr)               
//===================================================
// History:
//
// Exports:
// Imports:
//===================================================
package edin.ccg.representation.hockendeps;


/** 
 * This is just an implementation of a list of headwords.
 */
public class HeadWordList implements Cloneable{	
	/**  the head word */
	public String headWord;

	/** the lexical category */
	public String lexCat;

	public int index;

	private HeadWordList next;


	/** The constructor for parsing.
	 * @see  BinaryLeaf#BinaryLeaf(int, String, String, String)
	 */
	public HeadWordList(String word, String cat, double prob, int i){
		headWord = word;
		lexCat = cat;
		index = i;
	}

	/** The constructor for training -- am not sure this is required..!! */
	public HeadWordList(String word, String cat){
		headWord = word;
		lexCat = cat;
		index = 1;
	}

	/** The constructor for parsing..!! */
	public HeadWordList(String word, String cat, int i){
		headWord = word;
		lexCat = cat;
		index = i;
	}

	/** returns the head word or the string "WORD" */
	public String word(){	
		if (headWord != null)
			return headWord.toString();
		else return new String("WORD");
	}

	public HeadWordList next(){
		return next;
	}

	public int index(){
		return index;
	}

	/** Appends an entire <tt>HeadWordList</tt> to this list. */
	public void append(HeadWordList list2){
		HeadWordList copy = null;
		HeadWordList tmp, tmpcopy;
		try{copy = (HeadWordList)list2.clone();}
		catch (Exception E) {E.printStackTrace();}
		this.last().next = copy;
		tmp = list2;
		tmpcopy = copy;

		while (tmp.next != null){
			try{ tmpcopy.next = (HeadWordList)tmp.next.clone();}
			catch (Exception E) {E.printStackTrace();}
			tmp = tmp.next;
			tmpcopy = tmpcopy.next;
		}
	}

	private HeadWordList last(){
		HeadWordList tmp = this;
		while (tmp.next != null){
			tmp = tmp.next;
		}
		return tmp;
	}

	/** Returns a copy of this list. 
	 * Used by the constructors of <tt>BinaryNode</tt> during parsing.
	 */
	public HeadWordList copy(){
		HeadWordList copy = null;
		try{copy = (HeadWordList)this.clone();}
		catch (Exception E) {E.printStackTrace();}
		return copy;
	}
}