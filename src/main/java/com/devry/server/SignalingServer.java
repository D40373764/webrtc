package com.devry.server;

import java.io.File;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;

public class SignalingServer extends WebSocketServer {

	static final Logger LOGGER = Logger.getLogger(SignalingServer.class);

	private static Map<String, Room> rooms = new HashMap<String, Room>();
	private static Map<Integer, String> socketMap = new HashMap<Integer, String>();

	public SignalingServer(InetSocketAddress address) {
		super(address);
	}

	@Override
	public void onClose(WebSocket socket, int code, String reason, boolean remote) {
		LOGGER.debug("Socket closed: " + socket.getRemoteSocketAddress() + " reason: " + reason);
		String data = socketMap.get(socket.hashCode());
		if (data == null) {
			return;
		}
		String[] userInfo = data.split(":");
		String username = userInfo[0];
		String roomname = userInfo[1];
		
		Room room = rooms.get(roomname);
		room.removeUser(username);

		if (username.equalsIgnoreCase(room.getHostName())) {
			rooms.remove(roomname);
			room.close();
		}			
	}

	@Override
	public void onError(WebSocket socket, Exception exc) {
		LOGGER.debug("Socket error: " + socket.getRemoteSocketAddress() + " exception: " + exc.getMessage());		
	}

	@Override
	public void onMessage(WebSocket socket, String message) {
		LOGGER.debug("Got message" + message);
		
		processMessage(socket, message);
	}

	private void processMessage(WebSocket socket, final String message) {
		Room room;
		
		try {
			JSONObject jsonObj = new JSONObject(message);
			String type = jsonObj.has("type") ? jsonObj.getString("type") : "";
			String username = jsonObj.has("username") ? jsonObj.getString("username") : "";
			String roomname = jsonObj.has("roomname") ? jsonObj.getString("roomname") : System.currentTimeMillis() + "-" + username;

			
			switch (type) {

			case "login":
				// Create a room
//				if (roomname.length() < 1) {
//					jsonObj.put("success", "false").put("value", "Room name is empty");
//					socket.send(jsonObj.toString());
//					return;
//				}
//
//				if (rooms.get(roomname) != null) {
//					jsonObj.put("success", "false").put("value", "Room already taken");
//					socket.send(jsonObj.toString());
//					return;					
//				}

				room = new Room(roomname, 2);
				room.setHost(username, socket);
				rooms.put(roomname, room);
				LOGGER.debug("Generated room for: " + username);
				jsonObj.put("success", "true");
				jsonObj.put("roomname", roomname);
				socket.send(jsonObj.toString());
				
				socketMap.put(socket.hashCode(), username + ":" + roomname);

				break;
			
			case "rooms":
				// Return room list
				LOGGER.debug("Rooms requested by: " + username);				
				Set<String> roomnames = getRooms();
				jsonObj.put("value", roomnames);
				socket.send(jsonObj.toString());
				break;

			case "join":
				// Join a room
				room = rooms.get(roomname);

				if (room == null) {
					jsonObj.put("success", "false").put("value", "Room not exist");
					socket.send(jsonObj.toString());
					return;
				}
				
				room.addUser(username, socket);
				jsonObj.put("success", "true");
				socket.send(jsonObj.toString());
				
				socketMap.put(socket.hashCode(), username + ":" + roomname);

				break;

			case "leave":
				leaveRoom(socket, jsonObj);

				break;
				
			case "offer":
			case "answer":
			case "candidate":
				LOGGER.debug("TYPE: " + type);				
				LOGGER.debug("USERNAME: " + username);				
				LOGGER.debug("ROOMNAME: " + roomname);				
				processMessage(socket, roomname, jsonObj);
				break;
				
			default:
				socket.send("{\"type\":\"error\", \"message\":\"Unrecognized command: " + type + "\"}");
			}
		}
		catch (JSONException e) {
			socket.send("{\"type\":\"error\", \"message\":" + e.getMessage() + "}");
		}

	}

	private void leaveRoom(WebSocket currentSocket, JSONObject jsonObj) {
		
		socketMap.remove(currentSocket.hashCode());

		String username = jsonObj.getString("username");
		String roomname = jsonObj.getString("roomname");

		Room room = rooms.get(roomname);
		if (room != null) {
			room.removeUser(username);
			processMessage(currentSocket, roomname, jsonObj);

			if (username.equalsIgnoreCase(room.getHostName())) {
				rooms.remove(roomname);
				room.close();
			}				
		}
	}
	
	private void processMessage(WebSocket currentSocket, String roomname, JSONObject jsonObj) {
		Room room = rooms.get(roomname);
		Map<String, WebSocket> users = room.getUsers();
		for(Map.Entry<String, WebSocket> user : users.entrySet()) {
			WebSocket socket = user.getValue();
			if (socket != currentSocket) {
				socket.send(jsonObj.toString());
			}
		};
	}

	private Set<String> getRooms() {
		return rooms.keySet();	
	}

	@Override
	public void onOpen(WebSocket socket, ClientHandshake handshake) {
		LOGGER.debug("New client connected: " + socket.getRemoteSocketAddress() + " hash: " + socket.getRemoteSocketAddress().hashCode());
	}

	public static void main(String[] args) throws Exception {
		
		BasicConfigurator.configure();
		LOGGER.debug("Start app");
		
		InetSocketAddress socketAddress = new InetSocketAddress(8443);
		
		SignalingServer main = new SignalingServer(socketAddress);
		
		// load up the key store
		String STORETYPE = "JKS";
		String KEYSTORE = "keystore.jks";
		String STOREPASSWORD = "password";
		String KEYPASSWORD = "password";

		KeyStore ks = KeyStore.getInstance( STORETYPE );
		File kf = new File( KEYSTORE );
		ks.load( new FileInputStream( kf ), STOREPASSWORD.toCharArray() );

		KeyManagerFactory kmf = KeyManagerFactory.getInstance( "SunX509" );
		kmf.init( ks, KEYPASSWORD.toCharArray() );
		TrustManagerFactory tmf = TrustManagerFactory.getInstance( "SunX509" );
		tmf.init( ks );

		SSLContext sslContext = null;
		sslContext = SSLContext.getInstance( "TLS" );
		sslContext.init( kmf.getKeyManagers(), tmf.getTrustManagers(), null );

		main.setWebSocketFactory( new DefaultSSLWebSocketServerFactory( sslContext ) );

		
		main.start();
	}

}
