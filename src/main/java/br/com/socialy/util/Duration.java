package br.com.socialy.util;

import java.util.concurrent.TimeUnit;

public class Duration {

	private int time;
	private TimeUnit unit;

	private Duration(){}
	
	public static Duration create(int time, TimeUnit unit){
		Duration d = new Duration();
		d.time = time;
		d.unit = unit;
		return d;
	}

	public int getTime() {
		return time;
	}

	public TimeUnit getUnit() {
		return unit;
	} 
	
	
	
}
