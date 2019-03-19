//===================================================
//  File: /home/julia/CCG/code/StatisticalParser/code/CurrentCVSed/StatCCG/DepList.java
//  Author: Julia Hockenmaier                                
//  Purpose:                                                  
//  Created: Thu Feb 21 11:35:32 2002                         
//  Modified: Wed Jul 14 17:26:37 2004 (juliahr)               
//===================================================
// History:
//
// Exports:
// Imports:
//===================================================
package edin.ccg.representation.hockendeps;

/** A class for the list of dependencies */
public class DepList implements Cloneable{
	public String rel;// the relationship between the two words (atm, only 'arg')
	public String headWord;
	public String headCat;
	public int headIndex;//the index of the argument word in the chart
	public String argWord;
	public String argCat;
	public int argPos;// the argument slot
	public int argIndex;//the index of the argument word in the chart
	public boolean extracted;
	public boolean bounded;
	public DepList next;
	public int argDir;
	public int start;
	public int end;
	public boolean compl = false;// if true, head is the linguistic head and arg is its complement; o/w head is an adjunct
	/** directionality of argument: forward */
	public static final int FW = 0;// cf. CCGcat
	/** directionality of argument: backward */
	public static final int BW = 1; //cf.CCGcat

	public DepList(String relation, HeadWordList head, HeadWordList dependent, int argPosition, int dir){
		rel = relation;
		argDir = dir;
		if (head != null){
			headWord = head.headWord;
			headCat = head.lexCat;
			headIndex = head.index();
		}
		else {
			headWord = null;
			headCat = null;
			headIndex = -1;
		}
		if (dependent != null){
			argWord = dependent.headWord;
			argCat = dependent.lexCat;
			argIndex = dependent.index();
		}
		else {
			argWord = null;
			argCat = null;
			argIndex = -1;
		}
		argPos = argPosition;
		extracted = false;
		bounded = true;
		next = null;
	}

	public void append(DepList deplist){
		if (next != null){
			next.append(deplist);
		}
		else next = deplist;
	}
        
        public void appendnew(DepList deplist){
            DepList last = this;
            while(last.next != null)
                last = last.next;
            last.next = deplist;
	}
        
        
        public int count(){
            int count = 0;
            DepList last = next;
            while(last.next != null) {
                last = last.next;
                count++;                
            }
            return count;
        }

	public boolean isLocal(){
		return !extracted;
	}

	public boolean isBWextracted(){
		boolean retval = extracted;
		if (retval && argDir == FW){
			retval = false;
		}
		return retval;
	}

	public void setToExtractedBounded(){
		extracted = true;
		if (bounded != false) bounded = true;
		if (next != null){
			next.setToExtractedBounded();
		}
	}
	
	public void setToExtractedUnbounded(){
		extracted = true;
		bounded = false;
		if (next != null){
			next.setToExtractedUnbounded();
		}
	}

	public boolean containsDependency(int index){
		boolean retval = false;
		DepList tmp = this;
		while (tmp != null && retval == false){
			if (index == tmp.argIndex || index == tmp.headIndex){
				retval = true;
				break;
			}
			tmp = tmp.next;
		}
		return retval;
	}

	public DepList next(){
		return  next;
	}
	
	public DepList copy(){
		DepList copy = null;
		DepList tmp, tmpcopy;
		try {copy = (DepList)this.clone();}
		catch (Exception E) {E.printStackTrace();}
		tmp = this;
		tmpcopy = copy;
		while (tmp.next != null){
			try{ tmpcopy.next = (DepList)tmp.next.clone();}
			catch (Exception E) {E.printStackTrace();}
			tmp = tmp.next;
			tmpcopy = tmpcopy.next;
		}
		return copy;
	}
	
	// return a list of the unfilled dependencies	
	public DepList copyUnfilled(){
		DepList copy = null;
		DepList tmp, tmpcopy;
		if (headWord == null || argWord == null){
			try {copy = (DepList)this.clone();}
			catch (Exception E) {E.printStackTrace();}
		}
		tmp = this;
		tmpcopy = copy;
		while (tmp.next != null){
			if (tmp.next.headWord == null || tmp.next.argWord == null){
				if (copy == null){
					try {copy = (DepList)tmp.next.clone();}
					catch (Exception E) {E.printStackTrace();} 
					tmpcopy = copy;
				}
				else {
					try{ 
						tmpcopy.next = (DepList)tmp.next.clone();		
						tmpcopy = tmpcopy.next;    
					}
					catch (Exception E) {E.printStackTrace();}
				}
			}
			tmp = tmp.next;	     	 	     	   
		}
		return copy;
	}
}