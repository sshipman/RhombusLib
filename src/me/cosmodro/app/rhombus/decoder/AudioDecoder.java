package me.cosmodro.app.rhombus.decoder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import android.util.Log;

public class AudioDecoder {
	public static String TAG = "Rhombus AudioDecoder";
	public static int TRACK_1_BITLENGTH = 7;
	public static int TRACK_1_BASECHAR = 32;
	
	public static int TRACK_2_BITLENGTH = 5;
	public static int TRACK_2_BASECHAR = 48;
	
	private boolean debugging = true;

	private int silenceLevel = 500; //arbitrary level below which we consider "silent"
	private int minLevel = silenceLevel; //adaptive minimum level, should vary with each swipe.
	private double smoothing = 0.1;
	private double minLevelCoeff = 0.5;
	
	public AudioDecoder(){
	}

	/**
	 * get the level below which we consider audio data to be silent
	 * @return
	 */
	public int getSilenceLevel() {
		return silenceLevel;
	}
	
	/**
	 * set arbitrary audio level below which we consider silent.  
	 * Defaults to 500
	 * @param silenceLevel
	 */
	public void setSilenceLevel(int silenceLevel) {
		this.silenceLevel = silenceLevel;
	}

	/**
	 * get the percentage of average peak to set decoding threshold to.
	 * when decoding, the signal must go above this level between zero-crossings in order to count
	 * as separate zeros.
	 * @return
	 */
	public double getMinLevelCoeff() {
		return minLevelCoeff;
	}

	/**
	 * 
	 * get the percentage of average peak to set decoding threshold to.
	 * when decoding, the signal must go above this level between zero-crossings in order to count
	 * as separate zeros.
	 * Defaults to 0.5
	 * 
	 * @param minLevelCoeff
	 * @throws IllegalArgumentException if passed a value outside of 0 to 1. fs
	 */
	public void setMinLevelCoeff(double minLevelCoeff) {
		this.minLevelCoeff = minLevelCoeff;
	}

	public double getSmoothing() {
		return smoothing;
	}

	public void setSmoothing(double smoothing) {
		this.smoothing = smoothing;
	}

	private List<Integer> preprocessData(List<Integer> data){
		data = recenter(data);
		data = smooth(data);
		return data;
	}

	public SwipeData processData(List<Integer> samples){
		debug(TAG, "processing data");
    	List<Integer> data = preprocessData(samples);
		
		//first pass, iterate through bytes, get avg peak level
		//set minLevel to min% of avg peak
        SwipeData result = new SwipeData();
        result.setContent("Unevaluated.  This shouldn't happen");
        result.setBadRead();
       
		minLevel = getMinLevel(data, minLevelCoeff);

		Log.d(TAG, "first, the zero crossing method");
		BitSet bits = decodeToBitSet(data);
		result = decodeToASCII(bits);
		if (result.isBadRead()){
			debug(TAG, "bad read, lets try it backwards");
			result = decodeToASCII(reverse(bits));
		}

		if (result.isBadRead()){
			//second pass, decode to bitset
			bits = decodePeaksToBitSet(getPeaks(data, minLevel));
			Log.d(TAG, "and now the peaks method");
			result = decodeToASCII(bits);
		}
        
		if (result.isBadRead()){
			debug(TAG, "bad read, lets try it backwards");
			result = decodeToASCII(reverse(bits));
		}

		result.raw = samples;
		return result;
		
	}
	
	/**
	 * given a list of samples, get the average value, then subtract that from each sample
	 * @param List<Integer> data
	 * @return
	 */
	private List<Integer> recenter(List<Integer> data){
		List<Integer> samples = new ArrayList<Integer>(data.size());
		int sum = 0;
    	for (Integer val : data){
    		sum += val;
		}
		int avg = Math.round(sum/data.size());
		for (Integer s : data){
			samples.add(s - avg);
		}
		return samples;
	}
	
	/**
	 * apply smoothing to the data by setting each datapoint to a weighted average of the previous point and its raw value
	 * @param data
	 * @return
	 */
	private List<Integer> smooth(List<Integer> data){
		Log.d(TAG, "smoothing data.  smoothing param is "+smoothing);
		List<Integer> samples = new ArrayList<Integer>(data.size());
		Integer lastVal = data.get(0);
		for (Integer val : data){
			samples.add((int) ((lastVal*smoothing) + (val * (1-smoothing))));
		}
		return samples;
	}
	
	private int getMinLevel(List<Integer> data, double coeff){
		int lastval = 0;
		int peakcount = 0;
		int peaksum = 0;
		int peaktemp = 0; //value to store highest peak value between zero crossings
    	boolean hitmin = false;
    	for (Integer val : data){
    		if (val > 0 && lastval <= 0){
    			//we're coming from negative to positive, reset peaktemp
    			peaktemp = 0;
    			hitmin = false;
    		}else if (val < 0 && lastval >= 0 && hitmin){
    			//we're going from positive to negative, so add peaktemp to peaksum
    			peaksum += peaktemp;
    			peakcount++;
    		}
    		if ((val > 0) &&(lastval > val) && (lastval > silenceLevel) && (val > peaktemp)){
    			//new peak, higher than last peak since zero
    			//debug(TAG, "Peak: "+lastval);
    			hitmin = true;
    			peaktemp = val;
    		}
    		lastval = val;
		}
		
		if (peakcount > 0){
			int level =(int)Math.floor((peaksum / peakcount) * coeff); 
			debug(TAG, "returning "+level+" for minLevel");
			debug(TAG, "there were "+peakcount+" peaks");
			return level;
		}else{
			return silenceLevel;
		}
	}
		
	/**
	 * get all peaks above threshold
	 * a peak is a positive maximum or a negative minimum
	 * @param bytes
	 * @return
	 */
	private List<Peak> getPeaks(List<Integer> data, int threshold){
		LinkedList<Peak> toreturn = new LinkedList<Peak>();
    	//current sample index
    	int i = -1;
    	int lastDp = 0;
    	int beforeThatDp = 0;
    	for (Integer dp : data){
    		i++;
    		if (Math.abs(dp) < threshold){
    			//if it's not a great enough level, we don't care if it's a min/max or not.  move on.
    			continue;
    		}
    		
    		//yes, I know these could be one condition.  I think it's more readable like this.
    		if ((dp > 0) && (dp < lastDp) && (lastDp >= beforeThatDp)){ //positive maximum
    			toreturn.add(new Peak(i, lastDp));
    		}else if ((dp < 0) && (dp > lastDp) && (lastDp <= beforeThatDp)){ //negative minimum
    			toreturn.add(new Peak(i, lastDp));
    		}
    		//if not a qualifying peak, move on.
			beforeThatDp = lastDp;
			lastDp = dp;
    		
    	}
		debug(TAG, "got "+toreturn.size()+" peaks");
		return toreturn;
	}
	
	/**
	 * convert list of Peaks to BitSet of bits representing logical bits of stripe
	 * This uses both peak sign and timing.
	 * @param peaks
	 * @return
	 */
	public BitSet decodePeaksToBitSet(List<Peak> peaks){
		BitSet result = new BitSet(); //Todo: determine if setting initial capacity is worth it.
		debug(TAG, "there are "+peaks.size()+" peaks to decode");
		Iterator<Peak> piterator = peaks.iterator();
		if (!piterator.hasNext()){
			debug(TAG, "no peaks to decode");
			return result;
		}
		Peak lastPeak = piterator.next();
		debug(TAG, "initial peak:"+lastPeak);
		Peak peak;
		int oneinterval = -1; //interval between transitions for a 1 bit.  There are two transitions per 1 bit, 1 per 0.
		//so if interval is around 15, then if the space between transitions is 17, 15, that's a 1.  but if that was 32, that'd be 0.
		//the pattern starts with a self-clocking set of 0s.  We'll discard the first few, just because.
		int introDiscard = 1;
		int discardCount = 0;
		boolean flip;
    	int resultBitCount = 0;
    	int peakCount = 1; //for first we already got
		boolean needHalfOne = false; //if the last interval was the first half of a 1, the next better be the second half
		//iterate through peaks
		while(piterator.hasNext()){
			peak = piterator.next();
			flip = !peak.sameSign(lastPeak);
			debug(TAG, "peak:"+peak+" flip:"+flip+" peakcount:"+peakCount++);
			if (flip){
				if (discardCount < introDiscard){
					debug(TAG, "discard");
					discardCount++;
				}else{
					int sinceLast = peak.index - lastPeak.index;
					if (oneinterval == -1) {
						debug(TAG, "set oneinterval");
						oneinterval = sinceLast/2;
					}else {
						boolean oz = isOne(sinceLast, oneinterval);
						debug(TAG, "diff (peaks): " + sinceLast+ " oneinterval: "+oneinterval+" idx:"+peak.index+" one?: " + oz);
						if (oz) {
							if (needHalfOne) {
								oneinterval = (oneinterval + sinceLast)/2;
								result.set(resultBitCount, true);
								resultBitCount++;
								needHalfOne = false; //don't need next to be
							}else {
								needHalfOne = true;
							}
						}else {
							if (needHalfOne) {
								debug(TAG, "got a 0 where expected a 1.  result so far: " + result);
								break;
								//throw new Error("parse exception, did not get second half of expected 1 value");
							}else {
								oneinterval = (oneinterval + (sinceLast / 2))/2;
								//group+="0";
								//debug(TAG, "0");
								result.set(resultBitCount, false);
								resultBitCount++;
							}
						}
					}
				}
				lastPeak = peak;
			}
		}
    	debug(TAG, "raw binary: "+dumpString(result));
		return result;
	}
	
	/* convert array of bytes representing sample levels to BitSet of bits representing 
	 * logical bits of stripe
	 * 
	 * @param data List of samples (ints)
	 * @return BitSet representing logical signal
	 */
	public BitSet decodeToBitSet(List<Integer> data){
		BitSet result = new BitSet(); //Todo: determine if setting initial capacity is worth it.
    	// Create a DataOuputStream to write the audio data 
    	//current sample index
    	int i = 0;
    	int resultBitCount = 0;
		int lastSign = -1;
		int lasti = 0;
		int first = 0;
		int oneinterval = -1; //interval between transitions for a 1 bit.  There are two transitions per 1 bit, 1 per 0.
		//so if interval is around 15, then if the space between transitions is 17, 15, that's a 1.  but if that was 32, that'd be 0.
		//the pattern starts with a self-clocking set of 0s.  We'll discard the first few, just because.
		int introDiscard = 1;
		int discardCount = 0;
		boolean needHalfOne = false; //if the last interval was the first half of a 1, the next better be the second half
		int expectedParityBit = 1; //invert every 1 bit.  parity bit should make number of 1s in group odd.
		for (Integer dp : data){
			if ((dp * lastSign < 0) && (Math.abs(dp) > minLevel)) {
				if (first == 0) {
					first = i;
					debug(TAG,"set first to: " + first);
				}else if (discardCount < introDiscard) {
					discardCount++;
				}else {
					int sinceLast = i - lasti;
					if (oneinterval == -1) {
						oneinterval = sinceLast/2;
					}else {
						boolean oz = isOne(sinceLast, oneinterval);
						debug(TAG, "diff: " + sinceLast+ " oneinterval: "+oneinterval+" idx:"+i+" one?: " + oz);
						if (oz) {
							oneinterval = sinceLast;
							if (needHalfOne) {
								expectedParityBit = 1-expectedParityBit;
								//debug(TAG, "1");
								result.set(resultBitCount, true);
								resultBitCount++;
								needHalfOne = false; //don't need next to be
							}else {
								needHalfOne = true;
							}
						}else {
							oneinterval = sinceLast / 2;
							if (needHalfOne) {
								//debug(TAG, "result so far: " + result);
								break;
								//throw new Error("parse exception, did not get second half of expected 1 value");
							}else {
								//group+="0";
								//debug(TAG, "0");
								result.set(resultBitCount, false);
								resultBitCount++;
							}
						}
					}
				}
				lasti = i;
				lastSign *= -1;
			}
			i++;
    		
    	}
    	debug(TAG, "raw binary: "+dumpString(result));
		return result;
	}
	
	private SwipeData decodeToASCII(BitSet bits){
		SwipeData toreturn = new SwipeData();
		//get index of first 1
		int first1 = bits.nextSetBit(0);
		if (first1 < 0){
			debug(TAG, "no 1 bits detected.");
			toreturn.setBadRead();
			return toreturn;
		}
		debug(TAG, "first 1 bit is at position "+first1);
		int sentinel = 0;
		int exp = 0;
		int i = first1;
		//check for 5 bit sentinel
		for (; i < first1 + 4; i++){
			if (bits.get(i)){
				sentinel += 1 << exp; //lsb first. so with each following bit, shift it left 1 place.
			}
			exp++;
		}
		debug(TAG, "sentinel value for 4 bit:" + sentinel);
		if (sentinel == 11){ //11 is magic sentinel number for track 2.  corresponds to ascii ';' with offset 48 (ascii '0');
			return decodeToASCII(bits, first1, 4, 48);
		}else{
			for (; i < first1 + 6; i++){
				if (bits.get(i)){
					sentinel += 1 << exp;
				}
				exp++;
			}
			debug(TAG, "sentinel value for 6 bit:" + sentinel);
			if (sentinel == 5){ //5 is magic sentinel for track 1.  corresponds to ascii '%' with offset 32 (ascii space)
				return decodeToASCII(bits, first1, 6, 32);
			}
		}
		debug(TAG, "could not match sentinel value to either 11 or 5 magic values");
		toreturn.setBadRead();
		return toreturn;
	}
	
	/**
	 * Turn BitSet into String by decoding binary into ASCII characters
	 * @param bits BitSet of logical bits in swipe
	 * @param beginIndex int index of first 1 (start of sentinel)
	 * @param bitsPerChar int number of bits (not including parity bit) in character
	 * @return SwipeData
	 */
	public SwipeData decodeToASCII(BitSet bits, int beginIndex, int bitsPerChar, int baseChar){
		StringBuilder sb = new StringBuilder();
		SwipeData toreturn = new SwipeData();
		int i = beginIndex;
		char endSentinel = '?'; //for both!
		int charCount = 0;
		boolean sentinelFound = false;
		int size = bits.size(); //actual number of bits in bits, but we may not need all of them because we don't care after the end sentinel
		int letterVal = 0;
		char letter;
		boolean expectedParity;
		boolean bit;
		int exp;
		while((i < size) && !sentinelFound){
			letterVal = 0;
			expectedParity = true;
			exp = 0;
			int nextCharIndex = i + bitsPerChar;
			for (; i < nextCharIndex; i++){
				bit = bits.get(i);
				if (bit){
					letterVal += 1 << exp;
					expectedParity = !expectedParity;
				}
				exp++;
			}
			letter = decode(letterVal, baseChar);
			sb.append(letter);
			bit = bits.get(i);
			if (bit != expectedParity){
				debug(TAG, "addBadCharIndex "+charCount);
				toreturn.addBadCharIndex(charCount);
			}
			i++;
			charCount++;
			if (letter == endSentinel){
				sentinelFound = true;
			}
		}
		toreturn.setContent(sb.toString());
		return toreturn;
	}
   
    private char decode(int input, int baseChar){
    	//debug(TAG, "decode input: "+input);
    	char decoded = (char)(input + baseChar);
    	return decoded;
    }
    
	private boolean isOne(int actualInterval, int oneInterval) {
		int diffToOI = Math.abs(actualInterval - oneInterval);
		int diffToZI = Math.abs(actualInterval - (2 * oneInterval));
		if (diffToOI < diffToZI) {
			return true;
		}else {
			return false;
		}
	}
	
	private BitSet reverse(BitSet bits){
		int size = bits.size();
		BitSet toreturn = new BitSet(size);
		for (int i = 0; i < size; i++){
			toreturn.set(i, bits.get((size-1)-i));
		}
		//debug(TAG, "reversed BitSet: "+dump(toreturn));
		return toreturn;
	}
	
	private String dumpString(BitSet bits){
		StringBuilder sb = new StringBuilder();
		for (int i =0; i < bits.size(); i++){
			if (bits.get(i)){
				sb.append("1");
			}else{
				sb.append("0");
			}
		}
		return sb.toString();
	}
	
	private void debug(String tag, String message){
		if (debugging){
			Log.d(tag, message);
		}
	}

}
