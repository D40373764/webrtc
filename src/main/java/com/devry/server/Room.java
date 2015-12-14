package com.devry.server;

import java.util.HashMap;
import java.util.Map;

import org.java_websocket.WebSocket;

public class Room {
	private String hostname;
	private Map<String, WebSocket> users;	
	
	public Room(String hostName, int roomSize) {
		users = new HashMap<String, WebSocket>(roomSize);	
	}
	public void setHost(String userName, WebSocket socket) {
		this.hostname = userName;
		addUser(userName, socket);
	}
	public String getHostName() {
		return hostname;
	}
	public Map<String, WebSocket> getUsers() {
		return users;
	}
	public void addUser(String userName, WebSocket socket) {
		users.put(userName, socket);
	}
	public void removeUser(String username) {
		users.remove(username);
	}	
	public void close() {
		users.clear();
	}
}
