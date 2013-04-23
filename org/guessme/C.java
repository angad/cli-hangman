package org.guessme;

/**
 * 
 * @author angadsingh
 *
 */
public class C {

	public static final int		SECONDS_IN_A_MINUTE =	5;
	public static final int		MILLISECONDS_IN_A_SECOND = 1000;
	public static final int 	GAME_TIME 		=		6 * SECONDS_IN_A_MINUTE * MILLISECONDS_IN_A_SECOND;
	public static final int 	GUESS_TIME 		= 		2 * SECONDS_IN_A_MINUTE * MILLISECONDS_IN_A_SECOND;
	
	
	
	public static final int		REMOVE 			=		80; //x % of characters are removed
//	public static final String	ORACLE			=		"ORACLE";
//	public static final String	ANALYST			= 		"ANALYST";	
	
	public static final String	YES				=		"Y";
	public static final String	NO				=		"N";
	public static final String 	ACK				= 		"ACK";
	
	public static final String	SEPARATOR		=		"/";
	public static final String	SERVER			=		"TUX";
	public static final String	CLIENT			=		"WIN";
	public static final String	ANALYST			=		"ANA";
	public static final String	ORACLE			=		"ORA";
	public static final String	TIMER			=		"TIMER";
	
	/**
	 * Protocol
	 * SENDER/CONTROL_MESSAGE/MESSAGE
	 * 
	 */
	
	//Protocol messages
	public static final String	CONFLICT		= 		"CONFLICT";
	public static final String	WORD			= 		"WORD";
	public static final String	GUESS			=		"GUESS";
	public static final String	QUESTION		=		"QUESTION";
	public static final String	CORRECT			=		"CORRECT";
	public static final String	INCORRECT		= 		"INCORRECT";
	public static final String 	TIME_UP 		=		"TIMEUP";
	public static final String	POINTS			=		"POINTS";
	
	
	
	
	public enum ClientMode {
		ORACLE,
		ANALYST
	}	
}
