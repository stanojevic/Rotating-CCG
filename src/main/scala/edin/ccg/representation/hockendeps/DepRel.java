//===================================================
//  File: /home/julia/CCG/StatCCGChecked/StatCCG/DepRel.java
//  Author: Julia Hockenmaier                                
//  Purpose:                                                  
//  Created: Mon Oct  7 14:49:06 2002                         
//  Modified: Sat Mar 29 00:19:50 2003 (julia)                 
//===================================================
// History:
//
// Exports:
// Imports:
//===================================================
package edin.ccg.representation.hockendeps;

public class DepRel{
	public String cat;
	public int slot;
	public boolean extracted;
	public boolean bounded;

	public DepRel(String myCat, int mySlot, boolean myExtracted, boolean myBounded){
		cat = myCat;
		slot = mySlot;
		extracted = myExtracted;
		bounded = myBounded;
	}
	
	public DepRel copy(){
		return new DepRel(cat, slot, extracted, bounded);
	}
}