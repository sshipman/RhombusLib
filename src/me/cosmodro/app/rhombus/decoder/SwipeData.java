package me.cosmodro.app.rhombus.decoder;

import java.util.ArrayList;
import java.util.List;

public class SwipeData {
	public String content;
	public List<Integer> badCharIndices;
	public boolean badRead;
	public List<Integer> raw;
	
	public SwipeData(){
		content = "";
		badRead = false;
		badCharIndices = new ArrayList<Integer>();
		raw = new ArrayList<Integer>(); //placeholder.
	}
	
	public void setContent(String text){
		this.content = text;
	}
	
	public void addBadCharIndex(int i){
		badCharIndices.add(i);
	}
	
	public void setBadRead(){
		badRead = true;
	}
	
	public boolean isBadRead(){
		return badRead;
	}
	
	public List<Integer> getBadCharIndices(){
		return this.badCharIndices;
	}

}
