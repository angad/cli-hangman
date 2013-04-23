package org.guessme;

public class Util {

	public enum L {
		ERROR,
		TRACE,
		INFO
	}
	
	public static void Log(L l, String message) {
		System.out.format("%s: %s%n", l, message);
	}
	
}
