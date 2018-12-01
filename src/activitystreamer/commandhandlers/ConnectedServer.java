package activitystreamer.commandhandlers;

import org.apache.logging.log4j.Logger;

import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import activitystreamer.server.ConType;
import activitystreamer.server.Connection;
import activitystreamer.server.Control;
import activitystreamer.server.InvalidArgsException;
import activitystreamer.server.ServerState;
import activitystreamer.server.storednodes.StoredServer;

public class ConnectedServer {
	private Logger log = LogManager.getLogger();
	private StoredServer ss = null;
//	private String id;
//	private int load;
//	private String hostname;
//	private int port;
	private Connection con;
	private Date firstConnectedTimestamp;

	public ConnectedServer() {
		this.ss = null;
		this.con = null;
		this.firstConnectedTimestamp = new Date();
	}

	public ConnectedServer(Connection con) {
		this();
		this.con = con;
	}

	public ConnectedServer(String id, int load, String hostname, int port) throws Exception {
		this();
		this.ss = new StoredServer(id, hostname, port, load);
		Control.getInstance().addStoredServer(ss);
	}

	public String getId() {
		if (ss == null) return null;
		return ss.getId();
	}

	public int getLoad() {
		if (ss == null) return -1;
		return ss.getLoad();
	}

	public String getHostname() {
		if (ss == null) return null;
		return ss.getHostname();
	}
	
	public int getPort() {
		if (ss == null) return -1;
		return ss.getPort();
	}
	
	public Connection getCon() {
		return con;
	}

	public void setCon(Connection con) {
		this.con = con;
	}

	public Date getFirstConnectedTimestamp() {
		return firstConnectedTimestamp;
	}
	
	public JSONObject getRedirectJson() throws JSONException {
		return ss.getRedirectJson();
	}
	
	public JSONObject getJson() throws JSONException {
		return ss.getJson();
	}

	public void sendMessageToThisServer(String msg) {
		this.getCon().writeMsg(msg);
	}

	public void update(String id, int load, String hostname, int port) throws Exception {
		if (id == null || !id.equals(this.getId()))
			throw new Exception ("id mismatch");
		this.ss.update(hostname, port, load);
	}

	/**
	 * Process serverAnnounce from peers. Since they can only send their own
	 * Update their properties here.
	 * @param json
	 * @return false if server announce is ok.
	 * @throws Exception
	 */
	private boolean processServerAnnounce(JSONObject json) throws Exception {
		try {
			String id = json.getString("id");
			if (id.isEmpty())
				throw new Exception("Empty ID in server announce");
			int load = json.getInt("load");
			String hostname = json.getString("hostname");
			int port = json.getInt("port");

			//	StoredServer ss = Control.getInstance().getStoredServer(id);
			//	ConnectedServer cs = Control.getInstance().getConnectedServer(id);
			if (ss == null) {
				ss = new StoredServer(id, hostname, port, load);
				Control.getInstance().addStoredServer(ss);
			} else {
				ss.update(hostname, port, load);
			}
			log.debug("received an announcement from " + id + ": load " + load + " at " + hostname + ":"
					+ port);
			return false;
		} catch (JSONException e) {
			throw new Exception("Incorrect Server Announce");
		}
	}

	/**
	 * This is received from peers
	 * @param json
	 * @return
	 * @throws Exception
	 */
	private boolean processPeerActivityBroadcast(JSONObject json) throws Exception {
		try {
			JSONObject activity = json.getJSONObject("activity");
			String messageUsername = json.getString("authenticated_user");
			long messageOrigin = json.getLong("origin_timestamp");
			int sequence = json.getInt("sequence");
			Control c = Control.getInstance();
			c.addWatchedMessage(messageUsername, sequence, messageOrigin, activity);
			
			c.broadcastSuperpeer(json.toString());
			c.broadcastPeer(json.toString(), this.getCon());
			c.broadcastClient(json.toString(), this.con);
		} catch (JSONException e) {
			throw new Exception("activity not found");
		}
		return false;
	}
	
	/**
	 * Called during both peer (indirectly) and superpeer processing.
	 * @param json
	 * @return
	 * @throws Exception
	 */
	private boolean processSuperpeerActivityBroadcast(JSONObject json) throws Exception {
		try {
			JSONObject activity = json.getJSONObject("activity");
			String messageUsername = json.getString("authenticated_user");
			long messageOrigin = json.getLong("origin_timestamp");
			int sequence = json.getInt("sequence");
			Control c = Control.getInstance();
			c.addWatchedMessage(messageUsername, sequence, messageOrigin, activity);
			
			c.broadcastPeer(json.toString(), this.getCon());
			c.broadcastClient(json.toString(), this.con);
			c.updateWatchedMessagePeerReceived(messageUsername, sequence);
		} catch (JSONException e) {
			throw new Exception("activity not found");
		}
		Control.getInstance().broadcastPeer(json.toString(), this.getCon());
		Control.getInstance().broadcastClient(json.toString(), this.con);
		return false;
	}

	/**
	 * Treat as peer. Messages:
	 * -	SERVER_ANNOUNCE
	 * -	ACTIVITY_BROADCAST
	 * -	LOGIN_ATTEMPT
	 * -	REGISTER_REQUEST
	 * 
	 * @param msg
	 * @return true if the connection should be disconnected, false otherwise 
	 * @throws Exception
	 */
	public boolean process(String msg) throws Exception {
		
		JSONObject jo = new JSONObject(msg);
		String command = jo.getString("command");
		log.info("received a "+command+" from "+con.getSocket().getInetAddress());
		switch (command) {
		case "SERVER_ANNOUNCE":
			return this.processServerAnnounce(jo);
		case "ACTIVITY_BROADCAST":
			return this.processPeerActivityBroadcast(jo);
		case "LOGIN_ATTEMPT":
			return this.processLoginAttempt(jo);
		case "REGISTER_REQUEST":
			return this.processRegisterRequest(jo);
		default:
			throw new Exception("Invalid command, disconnecting");
		}
	}
	
	/**
	 * Processing a register attempt from a peer (we're the supervisor) or a superpeer (we're the mastersuperpeer).
	 * @param jo
	 * @return
	 * @throws JSONException 
	 */
	private boolean processRegisterRequest(JSONObject json) throws JSONException, InvalidArgsException {
		String username = json.getString("username");
		String secret = json.getString("secret");
		if (username == null || secret == null || username.isEmpty() || secret.isEmpty())
			throw new InvalidArgsException();
		
		// if username and secret are both present, try to get storedSecret from Control
		String storedSecret = Control.getInstance().getUserSecret(username);
		if (storedSecret != null)
			con.writeMsg(createRegisterAttemptFailed(username, secret));
		else {
			ServerState serverState = Control.getInstance().getServerState();

            if (serverState == ServerState.MASTERSUPERPEER) {
            	Control.getInstance().addRegisteredUser(username, secret);
            	String registerSuccess = createRegisterSuccess(username, secret);
            	Control.getInstance().broadcastSuperpeer(registerSuccess);
            	if (con.getConType() != ConType.SUPERPEER)
            		con.writeMsg(registerSuccess);
            } else if (serverState == ServerState.SUPERPEER) {
            	String registerRequest = createRegisterRequest(username, secret);
            	Control.getInstance().sendToMasterSuperPeer(registerRequest);
            	// Add this username to waiting register, linked to this conn
            	Control.getInstance().addWaitingRegister(username, con);
            }
				
		}
			
			
		return false;
	}
	
	private static String createRegisterAttemptFailed(String username, String secret) {
		JSONObject json = new JSONObject();
		try {
			json.put("command", "REGISTER_ATTEMPT_FAILED");
			json.put("username", username);
			json.put("secret", secret);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return json.toString();
	}
    
	private static String createRegisterSuccess(String username, String secret) {
	 	JSONObject json = new JSONObject();
	 	try {
	 		json.put("command", "REGISTER_SUCCESS");
	 		json.put("username", username);
	 		json.put("secret", secret);
	 	} catch (Exception e) {
	 		e.printStackTrace();
	 	}
 		return json.toString();
	}
	 
	private static String createRegisterRequest(String username, String secret) {
	 	JSONObject json = new JSONObject();
	 	try {
	 		json.put("command", "REGISTER_REQUEST");
	 		json.put("username", username);
	 		json.put("secret", secret);
	 	} catch (Exception e) {
	 		e.printStackTrace();
	 	}
	 	return json.toString();
	 }

	/**
	 * sent from a peer to its supervisor
	 * @param json
	 * @return false if no error in the request, true otherwise
	 */
	private boolean processLoginAttempt(JSONObject json) {
		try {
			String username = json.getString("username");
			String secret = json.getString("secret");	
			if (username == null || username.isEmpty() || secret == null || secret.isEmpty())
				throw new InvalidArgsException();		
			
			String storedSecret = Control.getInstance().getUserSecret(username);
			JSONObject loginAttemptFailed = new JSONObject();
			loginAttemptFailed.put("command", "LOGIN_ATTEMPT_FAILED");
			loginAttemptFailed.put("username", username);
			if (storedSecret == null || !storedSecret.equals(secret)) {
				this.getCon().writeMsg(loginAttemptFailed.toString());
			} else {
				if (Control.getInstance().userAlreadyLoggedIn(username))
					this.getCon().writeMsg(loginAttemptFailed.toString());
				else {
					JSONObject loginAttemptSuccess = new JSONObject();
					loginAttemptSuccess.put("command", "LOGIN_ATTEMPT_SUCCESS");
					loginAttemptSuccess.put("username", username);
					loginAttemptSuccess.put("pending_messages", Control.getInstance().getUserPendingMessages(username));
					this.getCon().writeMsg(loginAttemptSuccess.toString());
				}
					
			}
		} catch (Exception e) {
			e.printStackTrace();
			return true;
		}
		
		return false;
	}
	
	/**
	 * TODO
	 * NEW_LOGIN will have username, and id of peer, as well as the timestamp.
	 * The reason for this is that this NEW_LOGIN will be broadcast among the superpeers. If 
	 * any of them detects that they have just received a NEW_LOGIN as well, then whichever has the
	 * earlier timestamp wins. Both have added the user in their loggedInClients in Control. So, 
	 * they compare the loggedInTimestamp in the StoredClient and in the NEW_LOGIN message. If the
	 * NEW_LOGIN timestamp is earlier, then they tell their peer (where the client was connected) to 
	 * disconnect.
	 * @param json
	 * @return
	 */
	private boolean processNewLogin(JSONObject json) {
		return false;
	}

	
	
	
	/**
	 * Supervisor 
	 * Can receive the following messages:
	 * 
	 * LOGIN_ALLOWED {"username": "aaron"}
	 * possibly including {"redirect_hostname": "hostname", "redirect_port": 1234}
	 * Find this user in the waitingLogins, and send them a LOGIN_SUCCESS.
	 * 
	 * LOGIN_NOT_ALLOWED {"username": "aaron"} Find this user in the waitingLogins,
	 * and send them a LOGIN_FAILED
	 * 
	 * REGISTER_SUCCESS {"username": "aaron", "secret": "asdf"} Find this user in
	 * the waitingRegisters, and send them a REGISTER_SUCCESS
	 * 
	 * REGISTER_FAILED {"username": "aaron", "secret": "asdf"} Find this user in the
	 * waitingRegisters, and send them a REGISTER_FAILED
	 * 
	 * After a new server connects to this server, and authenticates, check with the
	 * other servers to know if/where it should be redirected. SERVER_REDIRECT
	 * {"hostname": "something", "port": 1234}
	 * 
	 * 
	 * If the supervisor sends you a RAISE_TO_SUPERPEER, try to make outgoing
	 * connections to all hostname, ports in the array. Then send back a
	 * SUPERPEER_CONNECTED with a list of all the connected superpeers' IDs.
	 * {"command": "RAISE_TO_SUPERPEER", "master": {"hostname": "hostname", "port":
	 * 1234}, "superpeers": [ {"hostname": "hostname", "port": 1234}, {"hostname":
	 * "hostnam2", "port": 1234} ]} Then the Supervisor will broadcast among the
	 * superpeers to treat you like a superpeer for sending information. (You'd be
	 * in the PEER_TO_SUPERPEER serverState). There you'd be sent all the messages,
	 * but you can't act like a superpeer for sending stuff. Then you'd be sent
	 * 
	 * SUPERPEER_INFO { "connected_clients": [ "asdfjklasdfjkl": "asdfjkl" ],
	 * "stored_messages": [ { "msg_id": "aaron_1234", "msg_content":
	 * "asdflkjasdlfkj", "pendingUsers": [ "aaron", "beri", "asdflkjs" ] } ] } Then
	 * you send an acknowledgement. Then you change your own state from
	 * PEER_TO_SUPERPEER to SUPERPEER and set supervisor to null.
	 * 
	 * 
	 * @param 	msg
	 * @return 	false always, because we don't want the supervisor connection to quit, unless the 
	 * 			supervisor disconnects or crashes.
	 * @throws 	Exception
	 */
	public boolean processSupervisor(String msg) throws Exception {
		
		JSONObject jo = new JSONObject(msg);
		String command = jo.getString("command");
		log.info("received a "+command+" from "+con.getSocket().getInetAddress());
		switch (command) {
		case "LOGIN_ALLOWED":
			return this.processLoginAllowed(jo);
		case "LOGIN_NOT_ALLOWED":
			return this.processLoginNotAllowed(jo);
		case "DISCONNECT_USER":
			return this.processDisconnectUser(jo);
		case "REGISTER_SUCCESS":
			return this.processRegisterSuccess(jo);
		case "REGISTER_FAILED":
			return this.processRegisterFailed(jo);
		case "RAISE_TO_SUPERPEER":
			return this.startBecomingSuperpeer(jo);
		case "SUPERVISOR_ANNOUNCE":
			return this.processSupervisorAnnounce(jo);
		default:
			throw new Exception("Invalid command, disconnecting");
		}
		
	}
	
	private boolean processSuperpeerInfo(JSONObject jo) {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean startBecomingSuperpeer(JSONObject jo) {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean processRegisterFailed(JSONObject jo) throws Exception {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean processRegisterSuccess(JSONObject jo) throws Exception {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean processDisconnectUser(JSONObject jo) throws Exception {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean processLoginNotAllowed(JSONObject jo) throws Exception {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean processLoginAllowed(JSONObject jo) throws Exception {
		// TODO
		String username = jo.getString("username");
//		Control.getInstance().
		return false;
	}

	/**
	 * 
	 * @param msg
	 * @return
	 * @throws Exception
	 */
	public boolean processSupervisorWhileRaising(String msg) throws Exception {
		JSONObject jo = new JSONObject(msg);
		String command = jo.getString("command");
		log.info("received a "+command+" from "+con.getSocket().getInetAddress());
		switch (command) {
		case "LOGIN_ALLOWED":
			return this.processLoginAllowed(jo);
		case "LOGIN_NOT_ALLOWED":
			return this.processLoginNotAllowed(jo);
		case "USER_DISCONNECTED":
			return this.processUserDisconnected(jo);
		case "REGISTER_SUCCESS":
			return this.processRegisterSuccess(jo);
		case "REGISTER_FAILED":
			return this.processRegisterFailed(jo);
		case "NEW_LOGIN":
			return this.processNewLogin(jo);
		case "SUPERPEER_ANNOUNCE":
			return this.processSuperpeerAnnounce(jo);
		case "SUPERVISOR_ANNOUNCE":
			return this.processSupervisorAnnounce(jo);
		case "SUPERPEER_INFO":
			return this.processSuperpeerInfo(jo);
		default:
			throw new Exception("Invalid command, disconnecting");
		}
	}

	private boolean processSupervisorAnnounce(JSONObject jo) {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean processSuperpeerAnnounce(JSONObject jo) {
		// TODO Auto-generated method stub
		return false;
	}

	private boolean processUserDisconnected(JSONObject jo) {
		// TODO Auto-generated method stub
		return false;
	}

	/**
	 * 
	 * @param msg
	 */
	public boolean processSuperpeer(String msg) throws Exception {
		// TODO Auto-generated method stub
		JSONObject jo = new JSONObject(msg);
		String command = jo.getString("command");
		log.info("received a "+command+" from "+con.getSocket().getInetAddress());
		switch (command) {
		case "SUPERPEER_ANNOUNCE":
			return this.processSuperpeerAnnounce(jo);
		case "NEW_LOGIN":
			return this.processNewLogin(jo);
		case "USER_DISCONNECTED":
			return this.processUserDisconnected(jo);
		case "REGISTER_SUCCESS":
			return this.processRegisterSuccess(jo);
		case "REGISTER_FAILED":
			return this.processRegisterFailed(jo);
		default:
			throw new Exception("Invalid command, disconnecting");
		}
	}

	/**
	 * 
	 * @param msg
	 */
	public boolean processMasterSuperpeer(String msg) throws Exception {
		// TODO Auto-generated method stub
		JSONObject jo = new JSONObject(msg);
		String command = jo.getString("command");
		log.info("received a "+command+" from "+con.getSocket().getInetAddress());
		switch (command) {
		case "RAISE_NEW_SUPERPEER":
			return this.startSuperpeerProcess(jo);
		case "REGISTER_SUCCESS":
			return this.processRegisterSuccess(jo);
		case "REGISTER_FAILED":
			return this.processRegisterFailed(jo);
		default:
			throw new Exception("Invalid command, disconnecting");
		}
	}

	private boolean startSuperpeerProcess(JSONObject jo) {
		// TODO Auto-generated method stub
		return false;
	}
}