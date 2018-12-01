package activitystreamer.server.storednodes;

import java.util.Comparator;
import java.util.Date;

import org.json.JSONObject;

public class StoredMessage {
	private String username;
	private int sequence;
	private String activity;
	private JSONObject activityJson;
	private long sentTimestamp;
	private long lastReceivedTimestamp;
	
	private int superpeersConnected;
	private int superpeersAcknowledged;
	// peers connected when this message was received here.
	private int peersConnected;
	private int peersAcknowledged;	
	
	public StoredMessage(JSONObject json) {
		
	}
	
	public String getUsername() {
		return this.username;
	}
	
	public int getSequence() {
		return this.sequence;
	}
	
	public String getActivity() {
		return this.activity;
	}
	public JSONObject getActivityJson() {
		return this.activityJson;
	}
	
	private boolean superpeerAcknowledged() {
		superpeersAcknowledged++;
		if (superpeersAcknowledged >= superpeersConnected)
			return true;
		else
			return false;
	}
	
	private boolean peerAcknowledged() {
		peersAcknowledged++;
		if (peersAcknowledged >= peersConnected)
			return true;
		else
			return false;
	}
}

class Sortbyusernamesequence implements Comparator<StoredMessage> {
	public int compare(StoredMessage a, StoredMessage b) {
		int usernameComparison = a.getUsername().compareTo(b.getUsername()); 
		if (usernameComparison == 0) 
			return a.getSequence() - b.getSequence();
		return usernameComparison;
	}
}
