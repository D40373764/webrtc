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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CallCenter extends WebSocketServer {

	static final Logger LOGGER = Logger.getLogger(CallCenter.class);

	private static Map<String, Call> calls = new HashMap<String, Call>();
	
	// SocketMap is used to find call entry from calls based on callerId.
	private static Map<Integer, String> socketMap = new HashMap<Integer, String>();

	public CallCenter(InetSocketAddress address) {
		super(address);
	}

	@Override
	public void onClose(WebSocket socket, int code, String reason, boolean remote) {
		LOGGER.debug("Socket closed: " + socket.getRemoteSocketAddress() + " reason: " + reason);
		String data = socketMap.get(socket.hashCode());
		if (data == null) {
			return;
		}
		socketMap.remove(socket.hashCode());

		String[] userInfo = data.split(":");
		String username = userInfo[0];
		String callerId = userInfo[1];
		
		Call call = calls.get(callerId);
		call.removeUser(username);

		if (username.equalsIgnoreCase(call.getHostName())) {
			calls.remove(callerId);
			call.close();
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
		Call call;
		
		try {
			JSONObject jsonObj = new JSONObject(message);
			String type = jsonObj.has("type") ? jsonObj.getString("type") : "";
			String username = jsonObj.has("username") ? jsonObj.getString("username") : "";
			String callerId = jsonObj.has("callerId") ? jsonObj.getString("callerId") : System.currentTimeMillis() + "-" + username;

			
			switch (type) {

			case "login":
			case "call":

				if (username.length() == 0) {
					socket.send("{\"type\":\"error\", \"message\":\"Missing username\"}");
					return;
				}
				call = new Call(callerId, 3);
				call.setHost(username, socket);
				calls.put(callerId, call);
				LOGGER.debug("Generated call for: " + username);
				jsonObj.put("success", "true");
				jsonObj.put("callerId", callerId);
				socket.send(jsonObj.toString());
				
				socketMap.put(socket.hashCode(), username + ":" + callerId);

				break;
			
			case "calls":
				// Return call list
				LOGGER.debug("Calls requested by: " + username);				
				Set<String> callerIds = getCalls();
				jsonObj.put("value", callerIds);
				socket.send(jsonObj.toString());
				break;

			case "callList":
				// Return call list
				LOGGER.debug("Calls requested by: " + username);
				jsonObj.put("value", getCallList());
				socket.send(jsonObj.toString());
				break;
				
			case "join":
				// Join a call
				call = calls.get(callerId);

				if (call == null) {
					jsonObj.put("success", "false").put("value", "Call does not exist");
					socket.send(jsonObj.toString());
					return;
				}
				
				call.addUser(username, socket);
				jsonObj.put("success", "true");
				socket.send(jsonObj.toString());
				
				socketMap.put(socket.hashCode(), username + ":" + callerId);

				break;

			case "leave":
				leaveCall(socket, jsonObj);

				break;
				
			case "offer":
			case "candidate":
				processMessage(socket, callerId, jsonObj);
				break;
			case "answer":
				LOGGER.debug("TYPE: " + type);				
				LOGGER.debug("USERNAME: " + username);				
				LOGGER.debug("ROOMNAME: " + callerId);				
				processMessage(socket, callerId, jsonObj);
				break;
				
			default:
				socket.send("{\"type\":\"error\", \"message\":\"Unrecognized command: " + type + "\"}");
			}
		}
		catch (JSONException e) {
			socket.send("{\"type\":\"error\", \"message\":" + e.getMessage() + "}");
		}

	}

	private void leaveCall(WebSocket currentSocket, JSONObject jsonObj) {
		
		socketMap.remove(currentSocket.hashCode());

		String username = jsonObj.getString("username");
		String callerId = jsonObj.getString("callerId");

		Call call = calls.get(callerId);
		if (call != null) {
			call.removeUser(username);
			processMessage(currentSocket, callerId, jsonObj);
			WebSocket savedSocket = call.getSavedSocket();
			if (savedSocket != null) {
				savedSocket.send(jsonObj.toString());				
			}
			
			//if (username.equalsIgnoreCase(call.getHostName())) {
				calls.remove(callerId);
				call.close();
			//}				
		}
	}
	
	private void processMessage(WebSocket currentSocket, String callerId, JSONObject jsonObj) {
		Call call = calls.get(callerId);
		Map<String, WebSocket> users = call.getUsers();
		for(Map.Entry<String, WebSocket> user : users.entrySet()) {
			WebSocket socket = user.getValue();
			if (socket != currentSocket) {
				socket.send(jsonObj.toString());
			}
		};
	}

	private Set<String> getCalls() {
		return calls.keySet();	
	}

	private JSONObject getCallList() {

		JSONObject jsonObj = new JSONObject();

		for (Map.Entry<String, Call> entry : calls.entrySet()) {
			Call call = (Call)entry.getValue();
			Set<String> users = call.getUsers().keySet();
			
			jsonObj.put(entry.getKey(), new JSONArray( users.toArray() ));			
		}
		
		return jsonObj;
	}

	@Override
	public void onOpen(WebSocket socket, ClientHandshake handshake) {
		LOGGER.debug("New client connected: " + socket.getRemoteSocketAddress() + " hash: " + socket.getRemoteSocketAddress().hashCode());
	}

	public static void main(String[] args) throws Exception {
		
		BasicConfigurator.configure();
		LOGGER.debug("Start app");
		
		InetSocketAddress socketAddress = new InetSocketAddress(8443);
		
		CallCenter main = new CallCenter(socketAddress);
		
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
