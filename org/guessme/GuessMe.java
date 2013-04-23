package org.guessme;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.guessme.C.ClientMode;
import org.guessme.client.Client;
import org.guessme.server.Server;

/**
 * This is the main class
 * 
 * @author angadsingh
 *
 */
public class GuessMe {

	public static void main(String[] args) throws IOException, InterruptedException {
		
		System.out.format("Select mode: %n" +
				"1 - Analysis%n" +
				"2 - Oracle%n");
		
		BufferedReader bufferedreader = new BufferedReader(new InputStreamReader(System.in));	
		int mode = Integer.parseInt(bufferedreader.readLine());
		
		Client c = mode == 1 ? new Client(ClientMode.ANALYST) : new Client(ClientMode.ORACLE);
		
		System.out.format("Ready? [Y/N]: %n");
		
		String ready = bufferedreader.readLine();
		if(ready.equals(C.YES)) {
			c.startGame();
		}
	}
}
