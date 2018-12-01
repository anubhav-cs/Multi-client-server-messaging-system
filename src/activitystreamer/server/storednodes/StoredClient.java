package activitystreamer.server.storednodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;

import activitystreamer.server.Control;

public class StoredClient {
	private String username;
	private String secret;
	private int sequence;
	private long addedToSystem;
	
	// last confirmed time when logged in
	private long lastConnected;
	private StoredServer lastServer;
	private boolean loggedIn;
	// keeps the message_ids of all messages
	private Set<String> pendingMessages = new HashSet<String>();
	
	public StoredClient(String username, String secret) {
		this.username = username;
		this.secret = secret;
		this.addedToSystem = Control.getInstance().currentTime();
	}
	
	public StoredClient(String username, String secret, int sequence) {
		this(username, secret);
		this.sequence = sequence;
	}
	
	public String getUsername() {
		return username;
	}
	public String getSecret() {
		return secret;
	}
	
	public void login(StoredServer ss) {
		this.lastServer = ss;
		this.lastConnected = Control.getInstance().currentTime();
		this.loggedIn = true;
	}
	public void disconnected(long timestamp) {
		this.lastConnected = timestamp;
		this.loggedIn = false;
	}
	
	public boolean loggedIn() {
		return this.loggedIn;
	}
	public int getNextSequence() {
		sequence++;
		return sequence;
	}
	public int getLastSequence() {
		return sequence;
	}
	
	public void addPendingMessage(String messageId) {
		this.pendingMessages.add(messageId);
	}
	public JSONArray getPendingMessages() {
		ArrayList<StoredMessage> messages = new ArrayList<StoredMessage>();
		for (String messageId : pendingMessages) {
			StoredMessage sm = Control.getInstance().getStoredMessage(messageId);
			if (sm != null)
				messages.add(sm);
		}
		Collections.sort(messages, new Sortbyusernamesequence());
		JSONArray arr = new JSONArray();
		for (StoredMessage sm : messages)
			arr.put(sm.getActivityJson());
		return arr;
	}
	public void clearPendingMessages() {
		pendingMessages.clear();
	}
	
}
