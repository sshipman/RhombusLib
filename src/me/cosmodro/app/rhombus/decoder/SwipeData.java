package me.cosmodro.app.rhombus.decoder;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;

public class SwipeData {
	public String content;
	public List<Integer> badCharIndices;
	public boolean badRead;
	public byte[] raw;
	private static CharacterStyle defaultBadCharFormat = new ForegroundColorSpan(Color.RED);
	
	public SwipeData(){
		content = "";
		badRead = false;
		badCharIndices = new ArrayList<Integer>();
		raw = new byte[0]; //placeholder.
	}
	
	/**
	 * create a CharSequence including formatting which makes characters at badCharIndices highlighted
	 * This version uses the defaultFormat
	 * @return
	 */
	public CharSequence toFormattedCharSequence(){
		return toFormattedCharSequence(defaultBadCharFormat);
	}
	
	public CharSequence toFormattedCharSequence(CharacterStyle cs){
		SpannableStringBuilder ssb = new SpannableStringBuilder(content);
		for (Integer i : badCharIndices){
			ssb.setSpan(cs, i, i+1, 0);
		}
		return ssb;
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

}
