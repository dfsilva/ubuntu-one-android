package com.ubuntuone.android.files.util;

public class TimeUtil
{
	public static long getTimeInMillis() {
		return System.currentTimeMillis();
	}
	
	public static long getTimeInSeconds() {
		return System.currentTimeMillis()/1000;
	}
}
