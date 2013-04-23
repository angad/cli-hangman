package org.guessme.server;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

import org.guessme.C;
import org.guessme.C.ClientMode;
import org.guessme.Config;
import org.guessme.Util;
import org.guessme.Util.L;

/**
 * 
 * @author angadsingh
 *
 */
public class Server {

	private ServerSocket serverSocket;
	private Socket oracleSocket;
	private Socket analystSocket;
	private Socket oracleTimerSocket;
	private Socket analystTimerSocket;
	
	private String word;
	private Timer tryTimer;
	private Timer gameTimer;
	
	private int points;
	
	public enum ServerState {
		DEAD,
		READY,
		CONNECTED,
		WORD_RECEIVED,
		TIME_UP
	}

	protected ServerState state;

	protected boolean isAnalystConnected;
	protected boolean isOracleConnected;

	public Server() {
		setState(ServerState.DEAD);
		points = 0;
		tryTimer = new Timer();
		gameTimer = new Timer();
		init();
	}

	private void setState(ServerState state) {
		this.state = state;
	}

	private boolean init() {
		try {
			serverSocket = new ServerSocket(Config.SERVER_PORT);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		setState(ServerState.READY);
		while (true) 
		{
			//		Util.Log(L.INFO, "Server init: Waiting for connection at 9000");
			try {
				Socket s = serverSocket.accept();
				new Thread(new ServerThread(s)).start();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void send(final Socket s, final String message) {
		new Thread() {
			public void run() {
				try {
					if(s == null) return;
					if(!s.isConnected()) return;
					DataOutputStream output = new DataOutputStream(s.getOutputStream());
					output.writeBytes(message + "\n");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		}.start();
	}

	private String createGuessWord(String word) {
		this.word = word;
		int len = word.length();
		//generate (80% of len) number of unique random numbers between 0,len
		int n = (int) (C.REMOVE * len / 100.0);
		Random rand = new Random();
		
		ArrayList<Integer> a = new ArrayList<Integer>();
		while(n!=0) {
			a.add(rand.nextInt(len + 1));
			n--;
		}
		
		String guessWord = "";
		for(int i=0; i<word.length(); i++) {
			if(a.contains(i)) guessWord += "_";
			else guessWord += word.charAt(i);
		}
		return guessWord;
	}
	
	private void tryTimeUp() {
		send(analystTimerSocket, C.SERVER + C.SEPARATOR + C.TIME_UP);
		send(oracleTimerSocket, C.SERVER + C.SEPARATOR + C.TIME_UP);
	}
	
	private void gameTimeUp() {
		send(analystTimerSocket, C.SERVER + C.SEPARATOR + C.POINTS + C.SEPARATOR + points);
		send(oracleTimerSocket, C.SERVER + C.SEPARATOR + C.POINTS + C.SEPARATOR + points);
	}
	
	private class TryTimer extends TimerTask {
		@Override
		public void run() {
			setState(ServerState.CONNECTED);
			tryTimeUp();
		}
	}

	private class GameTimer extends TimerTask {
		@Override
		public void run() {
			setState(ServerState.CONNECTED);
			gameTimeUp();
		}
	}

	
	private class ServerThread implements Runnable {

		Socket s;
		public ServerThread(Socket s) {
			this.s = s;
		}

		@Override
		public void run() {
			try {
				while(true) {
					BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
					String input = br.readLine();
					if(input == null) continue;
					DataOutputStream output = new DataOutputStream(s.getOutputStream());
					String response = processInput(input);
					if(response != null) output.writeBytes(response + "\n");
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private String processInput(String message) throws IOException {
			Util.Log(L.INFO, "Server: received message " + message);

			StringTokenizer st = new StringTokenizer(message, C.SEPARATOR);
			String sender = null;
			String controlMessage = null;
			if(st.hasMoreTokens()) sender = st.nextToken();
			if(st.hasMoreTokens()) controlMessage = st.nextToken();

			if(sender.equals(C.ORACLE)) {
				if(controlMessage.equals(C.TIMER)) {
					oracleTimerSocket = s;
				}
			} else if(sender.equals(C.ANALYST)) {
				if(controlMessage.equals(C.TIMER)) {
					analystTimerSocket = s;
				}
			}
			
			switch(state) {
			case DEAD:
				break;
			case READY:
				if(sender.equals(C.CLIENT)) {
					int clientMode = Integer.parseInt(controlMessage);
					if(clientMode == ClientMode.ANALYST.ordinal()) {
						if(isAnalystConnected) return C.SERVER + C.SEPARATOR + C.CONFLICT;
						isAnalystConnected = true;
						analystSocket = s;
						Util.Log(L.INFO, "Server: Analyst Connected");
					} else if(clientMode == ClientMode.ORACLE.ordinal() && !isOracleConnected) {
						if(isOracleConnected) return C.SERVER + C.SEPARATOR + C.CONFLICT;
						isOracleConnected = true;
						oracleSocket = s;
						Util.Log(L.INFO, "Server: Oracle Connected");
					}
					if(isAnalystConnected && isOracleConnected) {
						setState(ServerState.CONNECTED);
						Util.Log(L.INFO, "Server: Both Analyst and Oracle Connected");
						gameTimer = new Timer();
						gameTimer.schedule(new GameTimer(), C.GAME_TIME);
					}
					return C.SERVER + C.SEPARATOR + C.ACK;
				}
				
				return null;
			case CONNECTED:
				if(sender.equals(C.ORACLE)) {
					if(controlMessage.equals(C.WORD)) {
						tryTimer = new Timer();
						tryTimer.schedule(new TryTimer(), C.GUESS_TIME);
						String word = null;
						if(st.hasMoreElements()) word = st.nextToken();
						Util.Log(L.INFO, "Server: Received word from Oracle " + word);
						String guessWord = createGuessWord(word);
						send(analystSocket, C.SERVER + C.SEPARATOR + C.WORD + C.SEPARATOR + guessWord);
						Util.Log(L.INFO, "Server: Sent guessWord to Analyst " + guessWord);
						setState(ServerState.WORD_RECEIVED);
//						send(oracleSocket, C.SERVER + C.SEPARATOR + C.ACK);
//						return C.SERVER + C.SEPARATOR + C.ACK;
					} else {
						// error
					}
				} else {
					//error
				}
				break;
			case WORD_RECEIVED:
				if(sender.equals(C.ANALYST)) {
					if(controlMessage.equals(C.GUESS)) {
						//analyst is sending a guess request
						String guess = null;
						if(st.hasMoreElements()) guess = st.nextToken();
						if(guess.equalsIgnoreCase(word)) {
							//analyst has found the correct word
							Util.Log(L.INFO, "Server: Analyst found the word!");
							points++;
							try {
								tryTimer.cancel();
							} catch(IllegalStateException e) {
								
							}
							send(oracleSocket, C.SERVER + C.SEPARATOR + C.CORRECT);
							return C.SERVER + C.SEPARATOR + C.CORRECT;
						} else {
							//analyst has not found the correct word
							//forward the word to the oracle
							send(oracleSocket, C.SERVER + C.SEPARATOR + C.GUESS + C.SEPARATOR + guess);
							return C.SERVER + C.SEPARATOR + C.INCORRECT;
						}
					} else if(controlMessage.equals(C.QUESTION)) {
						//analyst is sending a question
						String question = null;
						if(st.hasMoreElements()) question = st.nextToken();
						//forward the question to oracle
						send(oracleSocket, C.SERVER + C.SEPARATOR + C.QUESTION + C.SEPARATOR + question);
					}
				} else if(sender.equals(C.ORACLE)) {
					if(controlMessage.equals(C.GUESS)) {
						//oracle is responding to a guess
						String response = null;
						if(st.hasMoreElements()) response = st.nextToken();
						//forward the request to the analyst
						send(analystSocket, C.SERVER + C.SEPARATOR + C.GUESS + C.SEPARATOR + response);
 					}
					if(controlMessage.equals(C.QUESTION)) {
						//oracle is responding to a question
						String response = null;
						if(st.hasMoreElements()) response = st.nextToken();
						//forward the request to the analyst
						send(analystSocket, C.SERVER + C.SEPARATOR + C.QUESTION + C.SEPARATOR + response);
 					}
				}
				break;
			
			case TIME_UP:
				break;
			default:
				break;

			}
			return null;
		}
	}
}
