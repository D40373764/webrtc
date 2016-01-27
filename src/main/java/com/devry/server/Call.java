package com.devry.server;

import java.util.HashMap;
import java.util.Map;

import org.java_websocket.WebSocket;

public class Call {
	private String hostname;
	private WebSocket savedSocket; // Socket is saved when screen sharing replaced the user socket
	private Map<String, WebSocket> users;	
	
	public Call(String hostName, int roomSize) {
		users = new HashMap<String, WebSocket>(roomSize);	
	}
	public void setHost(String userName, WebSocket socket) {
		this.hostname = userName;
		addUser(userName, socket);
	}
	public String getHostName() {
		return hostname;
	}
	public WebSocket getSavedSocket() {
		return savedSocket;
	}
	public Map<String, WebSocket> getUsers() {
		return users;
	}
	public void addUser(String userName, WebSocket socket) {
		if (users.containsKey(userName)) {
			savedSocket = users.get(userName);
		}
		users.put(userName, socket);
	}
	public void removeUser(String username) {
		users.remove(username);
	}	
	public void close() {
		users.clear();
	}
}
