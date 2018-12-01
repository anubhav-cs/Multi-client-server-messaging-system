package activitystreamer.commandhandlers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import activitystreamer.server.Connection;
import activitystreamer.server.Control;
import activitystreamer.server.storednodes.StoredClient;

public class ConnectedClient {

	// holds username, secret, lastLogin, etc.
	private StoredClient storedClient;
    private Connection con;
    private boolean loggedIn;
	private Logger log = LogManager.getLogger();

    public ConnectedClient() {}
//    public ConnectedClient(String username, String secret, Connection con) {
//        this.username = username;
//        this.secret = secret;
//        this.con = con;
//    }
    public ConnectedClient(Connection con) {
    	this.con = con;
    }
    public ConnectedClient(Connection con, StoredClient sc) {
    	this.con = con;
    	this.storedClient = sc;
    }

    public Connection getConnection() {
        return this.con;
    }
    
    public void setStoredClient (StoredClient sc) {
    	this.storedClient = sc;
    }

    public String getUsername() {
        return storedClient.getUsername();
    }
    public String getSecret() {
        return storedClient.getSecret();
    }
    
    public int nextSequence() {
    	return storedClient.getNextSequence();
    }
    public int getSequence() {
    	return this.storedClient.getLastSequence();
    }

    public boolean matches(String username, String secret) {
        return this.getUsername().equals(username) && this.getSecret().equals(secret);
    }

    /**
     * @return  True if the connection should exit, false otherwise
     * @throws  Exception   If any error is encountered.
     */
    public boolean process(String msg) throws Exception {
		try {
            JSONObject jo = new JSONObject(msg);
            String command;
            command = jo.getString("command");
            log .info("received a "+command+" from "+con.getSocket().getInetAddress());
            switch(command) {
                case "LOGOUT":
                    return this.processLogout(jo);
                case "ACTIVITY_MESSAGE":
                    return this.processActivityMessage(jo);
                default:
                    throw new Exception();
            }
		} catch (Exception e) {
			throw e;
		}        
    }
    
    // Doesn't need validation because doesn't contain anything other than command
	private boolean processLogout(JSONObject json) {
		storedClient.disconnected(Control.getInstance().currentTime());

		// TODO Send supervisor or broadcast the USER_DISCONNECTED message.
        return true;
	}
	
	/**
	 * Important TODO Check
	 * @param json
	 * @return
	 * @throws Exception
	 */
	private boolean processActivityMessage(JSONObject json) throws Exception {
        String username = null;
        String secret = null;
        try {
            username = json.getString("username");
        } catch (JSONException e) {
            throw new Exception ("no username present");
		}
		
		if (username.equals("anonymous"))
			throw new Exception("anonymous cannot send activity message");

		try {
			secret = json.getString("secret");
		} catch (Exception e) {
			throw new Exception("secret not present");
		}

		if (!username.equals(getUsername()) || !secret.equals(getSecret())) 
			throw new Exception("authentication mismatch");

		try {            
			JSONObject activity = json.getJSONObject("activity");
			activity.put("authenticated_user", getUsername());
			JSONObject msg = new JSONObject();
			msg.put("command", "ACTIVITY_BROADCAST");
			msg.put("activity", activity);
			msg.put("origin_timestamp", Control.getInstance().currentTime());
			msg.put("sequence", nextSequence());
//			Control.getInstance().broadcastActivityMessage(msg.toString(), );
			return false;
		} catch (Exception e) {
			throw new Exception("could not read the activity object");
		}
	}
}