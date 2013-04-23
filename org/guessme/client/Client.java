package org.guessme.client;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.guessme.C;
import org.guessme.C.ClientMode;
import org.guessme.Config;
import org.guessme.Util;
import org.guessme.Util.L;

public class Client {

	private Socket s;
	private Socket timerSocket;
	private DataOutputStream serverWriter;
	private BufferedReader serverReader;
	private BufferedReader timerReader;
	private DataOutputStream timerWriter;

	private ClientMode mode;

	private String word;
	private boolean found;
	private Future<String> consoleInput;
	private Future<String> socketInput;
	private int game;

	public enum ClientState {
		DEAD,
		READY,
		REGISTERED,
		GAME_STARTED;
	}

	private ClientState state;

	public Client() {}

	public Client(ClientMode mode) {
		this.mode = mode;
		found = false;
		game = 1;
		if(init()){
			setState(ClientState.READY);
		}
	}

	private void setState(ClientState state) {
		this.state = state;
	}

	private boolean init() {
		OutputStream os;
		OutputStream timerOs;
		try {
			s = new Socket(Config.SERVER_HOST, Config.SERVER_PORT);
			timerSocket = new Socket(Config.SERVER_HOST, Config.SERVER_PORT);
			os= s.getOutputStream();
			timerOs = timerSocket.getOutputStream();
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		serverWriter = new DataOutputStream(os);
		timerWriter = new DataOutputStream(timerOs);
		InputStreamReader isrServer = null;
		InputStreamReader isrTimer = null;
		try {
			isrServer = new InputStreamReader(s.getInputStream());
			isrTimer = new InputStreamReader(timerSocket.getInputStream());
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		serverReader = new BufferedReader(isrServer);
		timerReader = new BufferedReader(isrTimer);		
		return true;
	}

	private String timerRead() {
		try {
			return timerReader.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private void timerWrite(String message) {
		try {
			timerWriter.writeBytes(message + "\n");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void write(String message) {
		if(state.equals(ClientState.DEAD)) Util.Log(L.ERROR, "Client write: Client is not ready yet");
		else {		
			try {
				serverWriter.writeBytes(message + "\n");
			} catch (IOException e) {
				Util.Log(L.ERROR, "Client write: error " + e.getMessage());
			}
		}
	}

	private String read() {
		if(state.equals(ClientState.DEAD)) {
			Util.Log(L.ERROR, "Client read: Client is not ready yet");
			return null;
		} else {
			ExecutorService ex = Executors.newSingleThreadExecutor();
			String input = null;
			socketInput = ex.submit(new SocketReadTask(serverReader));
			try {
				input = socketInput.get();
			} catch (ExecutionException e) {
				e.getCause().printStackTrace();
			} catch(CancellationException e) {
				return "##";
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			return input;
		}
	}

	private boolean isGuess(String guess) {
		if(guess == null || word == null) return false;
		
		if(guess.length() != word.length()) return false;

		for(int i=0; i < word.length(); i++) {
			if(word.charAt(i) != '_' && (word.charAt(i) != guess.charAt(i))) 
				return false;
		}
		return true;
	}

	private void reset() {
		found = false;
		word = null;
		setState(ClientState.REGISTERED);
		game ++;
		System.out.println("Starting New Try");
		startGame();
	}

	public void startGame() {
		String command;
		String response;
		String sender = mode == ClientMode.ANALYST ? C.ANALYST : C.ORACLE;

		if(state != ClientState.REGISTERED) { 
			//Register with server
			command = C.CLIENT + C.SEPARATOR + mode.ordinal();
			write(command);
			response = read();
			processResponse(response, mode);
			timerWrite(sender + C.SEPARATOR + C.TIMER);

			new Thread() {
				public void run() {
					String message  = timerRead();
					processResponse(message, mode);
				}
			}.start();
		}

		switch(mode) {
		case ANALYST:

			//Receive the word from the server
			response = read();
			Util.Log(L.INFO, "Client: received " + response);
			processResponse(response, mode);

			//prompt the user for a guess or a related question
			while(!found) {

				String guess = null;
				try {
					System.out.println("Enter a guess: ");
					//					guess = bufferedreader.readLine();
					guess = readLine();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				if(guess.equals("##")) reset();
				if(isGuess(guess))
					command = sender + C.SEPARATOR + C.GUESS + C.SEPARATOR + guess;
				else
					command = sender + C.SEPARATOR + C.QUESTION + C.SEPARATOR + guess;
				write(command);

				response = read();
				if(response.equals("##")) reset();
				processResponse(response, mode);
			}

			break;
		case ORACLE:
			String word = null;

			System.out.println("Enter a word: ");
			try {
				word = readLine();
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}

			//send word to server
			command = sender + C.SEPARATOR + C.WORD + C.SEPARATOR + word;
			write(command);
			setState(ClientState.GAME_STARTED);

			//receive ACK from server
//			Util.Log(L.INFO, "WAITING FOR ACK FROM SERVER!");
//			response = read();
//			processResponse(response, mode);

			while(!found) {
				response = read();
				if(response.equals("##")) reset();
				String type = null;
				type = processResponse(response, mode);
				if(found) break;
				System.out.println("Enter response for Analyst: ");
				String r = null;
				try {
					r = readLine();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if(r.equals("##")) reset();
				command = sender + C.SEPARATOR + type + C.SEPARATOR + r;
				write(command);
			}
			break;
		}
	}

	private String processResponse(String message, ClientMode mode) {
		Util.Log(L.TRACE, "Client: received " + message + " state " + state);
		
		if(message == null) return null;
		
		StringTokenizer st = new StringTokenizer(message, C.SEPARATOR);
		String sender = null;
		String controlMessage = null;
		if(st.hasMoreElements()) sender = st.nextToken();
		if(st.hasMoreElements()) controlMessage = st.nextToken();

		if(!sender.equals(C.SERVER)) {
			//ERROR!
			return null;
		}

		switch(state) {
		case DEAD:
			break;
		case READY:
			if(controlMessage.equals(C.ACK)) {
				setState(ClientState.REGISTERED);
				Util.Log(L.INFO, "Client: Registered");
			}
			break;
		case REGISTERED:
			switch(mode) {
			case ANALYST:
				if(controlMessage.equals(C.WORD)) {
					setState(ClientState.GAME_STARTED);
					Util.Log(L.INFO, "Client: Analyst: Game started");
					if(st.hasMoreElements()) word = st.nextToken();
				}
				break;
			case ORACLE:
				break;
			}
			break;
		case GAME_STARTED:
			switch(mode) {
			case ANALYST:
				if(controlMessage.equals(C.CORRECT)) {
					found = true;
					Util.Log(L.INFO, "Client: Analyst found the correct word!");
					reset();
				} else if(controlMessage.equals(C.GUESS) || (controlMessage.equals(C.QUESTION))) {
					Util.Log(L.INFO, "Client: Oracle's response " + st.nextToken());
				} else if(controlMessage.equals(C.TIME_UP)) {
					//if there is an input waitin somewhere, cancel it
					if(consoleInput!=null) consoleInput.cancel(true);
					if(socketInput!=null) socketInput.cancel(true);
					Util.Log(L.INFO,  "Client: Time up!");
				}
				break;
			case ORACLE:
				if(controlMessage.equals(C.CORRECT)) {
					found = true;
					Util.Log(L.INFO, "Client: Analyst found the correct word!");
					reset();
				} else if(controlMessage.equals(C.GUESS) || controlMessage.equals(C.QUESTION)) {
					Util.Log(L.INFO, "Client: Analyst's query " + st.nextToken());
					return controlMessage;
				} else if(controlMessage.equals(C.TIME_UP)) {
					//if there is an input waitin somewhere, cancel it
					if(consoleInput!=null) consoleInput.cancel(true);
					if(socketInput!=null) socketInput.cancel(true);
					Util.Log(L.INFO,  "Client: Time up!");
				}
				break;
			}
			break;
		}

		return null;
	}

	public String readLine() throws InterruptedException {
		ExecutorService ex = Executors.newSingleThreadExecutor();
		String input = null;
		consoleInput = ex.submit(new ConsoleInputReadTask());
		try {
			input = consoleInput.get();
		} catch (ExecutionException e) {
			e.getCause().printStackTrace();
		} catch(CancellationException e) {
			return "##";
		}
		return input;
	}

	private class ConsoleInputReadTask implements Callable<String> {
		public String call() throws IOException {
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			String input;
			input = br.readLine();
			return input;
		}
	}

	private class SocketReadTask implements Callable<String> {
		BufferedReader br;
		public SocketReadTask(BufferedReader br) {
			this.br = br;
		}
		public String call() throws IOException {
			String input;
			input = br.readLine();
			return input;
		}
	}
}
