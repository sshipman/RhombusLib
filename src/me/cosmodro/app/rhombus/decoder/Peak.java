package me.cosmodro.app.rhombus.decoder;
/**
 * simple pojo to represent peaks in an audio stream
 * a peak is a positive maximum or negative minimum whose absolute value exceeds some threshold.
 * @author Steve
 *
 */

public class Peak {
	//index into samples where this peak was detected
	public int index;
	
	//sample value
	public short value;
	
	public Peak(){
		this(0,(short)0);
	}
	public Peak(int index, short value){
		this.index = index;
		this.value = value;
	}
	
	public boolean isPositive(){
		return value > 0;
	}
	
	public boolean sameSign(Peak other){
		return (this.isPositive() && other.isPositive()) ||
				(!this.isPositive() && !other.isPositive());
	}
	
	public String toString(){
		return "[idx: "+index+", "+"value: "+value+"]";
	}

}
