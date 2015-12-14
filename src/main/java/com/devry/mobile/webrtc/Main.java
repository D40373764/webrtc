package com.devry.mobile.webrtc;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;

public class Main extends WebSocketServer {

	private static Map<Integer, Set<WebSocket>> rooms = new HashMap<Integer, Set<WebSocket>>();
	private int myroom;

	public Main() {
		super(new InetSocketAddress(30001));
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		System.out.println("Client disconnected: " + reason);		
	}

	@Override
	public void onError(WebSocket conn, Exception exc) {
		System.out.println("Error happened: " + exc);				
	}

	@Override
	public void onMessage(WebSocket conn, String message) {
		Set<WebSocket> s;
		try {
			JSONObject obj = new JSONObject(message);
			String msgtype = obj.getString("type");
			switch (msgtype) {
			case "GETROOM":
				myroom = generateRoomNumber();
				s = new HashSet<>();
				s.add(conn);
				rooms.put(myroom, s);
				System.out.println("Generated new room: " + myroom);
				conn.send("{\"type\":\"GETROOM\", \"value\":" + myroom + "}");
				break;
			case "ENTERROOM":
				myroom = obj.getInt("value");
				s = rooms.get(myroom);
				s.add(conn);
				rooms.put(myroom, s);
				break;
			default:
				sendToAll(conn, message);
				break;
			}
		}
		catch (JSONException e) {
			sendToAll(conn, message);			
		}
		System.out.println();
	}

	private void sendToAll(WebSocket conn, String message) {
		Iterator it = rooms.get(myroom).iterator();
		while (it.hasNext()) {
			WebSocket c = (WebSocket)it.next();
			if (c != conn) c.send(message);
		}		
	}

	private int generateRoomNumber() {
		return new Random(System.currentTimeMillis()).nextInt();
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		System.out.println("New client connected: " + conn.getRemoteSocketAddress() + " hash: " + conn.getRemoteSocketAddress().hashCode());
	}

	public static void main(String[] args) {
		Main main = new Main();
		main.start();
	}
}
