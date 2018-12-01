package activitystreamer.server;

import java.io.IOException;
import java.net.Socket;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import activitystreamer.commandhandlers.ConnectedClient;
import activitystreamer.commandhandlers.ConnectedServer;
import activitystreamer.server.storednodes.StoredClient;
import activitystreamer.server.storednodes.StoredMessage;
import activitystreamer.server.storednodes.StoredServer;
import activitystreamer.util.Settings;

public class Control extends Thread {
	private static final Logger log = LogManager.getLogger();

	private ServerState serverState = ServerState.PEER;
	private Map<Connection, Object> connections = new HashMap<Connection, Object>();

	// If supervisor is null, that means this one's a superpeer.
	private ConnectedServer supervisor = null;
	private StoredServer[] backupSupervisors = new StoredServer[2];
	// If masterSuperpeer is null and supervisor is also null, that means
	// this one's the masterSuperpeer.
	private ConnectedServer masterSuperpeer = null;
	private ConnectedServer[] backupMasterSuperpeer = new ConnectedServer[2];

	private Map<String, StoredServer> storedServers = new HashMap<String, StoredServer>();
	private Map<String, StoredClient> registeredClients = new HashMap<String, StoredClient>();
	// clients logged in anywhere in the system.
	private Map<String, StoredServer> loggedInClients = new HashMap<String, StoredServer>();
	// clients/peers connections waiting for register_success or register_failed 
	private Map<String, Connection> waitingRegister = new HashMap<String, Connection>();
	private Map<String, ConnectedClient> waitingLogin = new HashMap<String, ConnectedClient>();
	
	private long timeDelta = 0;
	private boolean term = false;

	// Initialization might throw an error, so done in constructor
	private Listener listener;
	private StoredServer selfStoredServer;

	private static Control control = null;

	public static Control getInstance() {
		if (control == null) {
			control = new Control();
		}
		return control;
	}

	private Control() {
		// start a listener
		try {
			listener = new Listener();
			 selfStoredServer = new StoredServer(Settings.getId(), Settings.getLocalHostname(), Settings.getLocalPort(), getLoad());
		} catch (IOException e1) {
			log.fatal("failed to startup a listening thread: " + e1);
			System.exit(-1);
		} catch (Exception e) {
			log.fatal("failed to set up selfStoredServer");
			System.exit(-1);
		}
		// try to initiate connection to given remote hostname/port if given,
		// if unsuccessful (or not given), raise own state to MASTERSUPERPEER
		if (!this.initiateConnection()) {
			log.info("no remote connection, becoming MasterSuperPeer");
			serverState = ServerState.MASTERSUPERPEER;
		} else {
			serverState = ServerState.PEER;
		}
		start();
	}

	/**
	 * Checks Settings to see if remote hostname/port are given. If not, returns
	 * false. Otherwise, tries to authenticate with that hostname/port. If
	 * unsuccessful, shows error and exits, otherwise returns true.
	 * 
	 * @return false if remote hostname/port is not present, true if successfully
	 *         authenticated
	 */
	private boolean initiateConnection() {
		String remoteHostname = Settings.getRemoteHostname();
		if (remoteHostname == null)
			return false;
		int remotePort = Settings.getRemotePort();

		try {
			Connection oc = outgoingConnection(new Socket(remoteHostname, remotePort));
			JSONObject authenticate = new JSONObject();
			authenticate.put("command", "AUTHENTICATE");
			// Possible TODO: update timeDelta here
			// authenticate.put("timestamp", currentTime());
			authenticate.put("secret", Settings.getSecret());
			
			oc.writeMsg(authenticate.toString());
			// Create connectedServer and set as supervisor
			if (supervisor == null)
				supervisor = new ConnectedServer(oc);
			return true;
		} catch (IOException e) {
			log.error("Failed to make connection to " + remoteHostname + ":" + remotePort + " :" + e);
			System.exit(-1);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public void run() {
		log.info("Using activity interval of " + Settings.getActivityInterval() + " milliseconds");

		while (!term) {
			try {
				Thread.sleep(Settings.getActivityInterval());
			} catch (InterruptedException e) {
				log.info("Received an interrupt, system shutting down");
				break;
			}
			if (this.supervisor != null) {
				log.debug("Sending Server Announce");
				term = doPeerActivity();
			} else {
				log.debug("Sending Superpeer Announce");
				term = doSuperpeerActivity();
			}
		}

		log.info("Closing " + connections.size() + " connections");
		// cleanup
		for (Connection con : connections.keySet()) {
			con.closeCon();
		}
		listener.setTerm(true);
	}

	/**
	 * Create serverAnnounce and send to connected superpeer
	 * 
	 * @return false if message sent successfully, true otherwise.
	 */
	private boolean doPeerActivity() {
		JSONObject serverAnnounce = new JSONObject();
		try {
			serverAnnounce.put("command", "SERVER_ANNOUNCE");
			serverAnnounce.put("id", Settings.getId());
			serverAnnounce.put("load", connections.size());
			serverAnnounce.put("hostname", Settings.getLocalHostname());
			serverAnnounce.put("port", Settings.getLocalPort());
		} catch (Exception e) {
			log.error("Failed to create SERVER_ANNOUNCE message");
			return true;
		}

		supervisor.getCon().writeMsg(serverAnnounce.toString());
		return false;
	}

	/**
	 * Create superpeerAnnounce and send to connected superpeers
	 * 
	 * @return false if all sent successfully, true otherwise
	 */
	private boolean doSuperpeerActivity() {
		JSONObject announce = new JSONObject();
		JSONObject supervisorAnnounce = new JSONObject();
		try {
			announce.put("command", "SUPERPEER_ANNOUNCE");
			announce.put("id", Settings.getId());
			announce.put("hostname", Settings.getLocalHostname());
			announce.put("port", Settings.getLocalPort());
			supervisorAnnounce.put("hostname", Settings.getLocalHostname());
			supervisorAnnounce.put("port", Settings.getLocalPort());
			supervisorAnnounce.put("command", "SUPERVISOR_ANNOUNCE");
			supervisorAnnounce.put("id", Settings.getId());
			
			announce.put("load", connections.size());
			announce.put("peers", createPeersArray());
			String announceMessage = announce.toString();
			log.debug("superpeerAnnounceMessage: " + announceMessage);
			broadcastSuperpeer(announceMessage);
			
			JSONArray backupSuperpeers = new JSONArray();
			// TODO: Get backup superpeers
			supervisorAnnounce.put("backup_superpeers", backupSuperpeers.toString());
			log.debug("supervisorAnnounce: " + supervisorAnnounce.toString());
			broadcastPeer(supervisorAnnounce.toString(), null);
		} catch (Exception e) {
			log.error("Failed to create SUPERPEER_ANNOUNCE message");
			return true;
		}
		
		return false;
	}
	
	/**
	 * @return a JSONArray containing the collated serverAnnounces of all direct peers of this server
	 */
	private JSONArray createPeersArray() {
		JSONArray jsonArray = new JSONArray();
		try {
			for (Connection con : connections.keySet())
				if (con.getConType() == ConType.PEER) {
					ConnectedServer cs = (ConnectedServer)connections.get(con);
					// Only consider this connected peer if a server announce has
					// been received from them.
					if (cs.getHostname() == null)
						continue;
					JSONObject json = cs.getJson();
					jsonArray.put(json);
				}					
		} catch (Exception e) {
			e.printStackTrace();
		}
		return jsonArray;
	}

	/**
	 * Called by listener when there's a new incoming connection. Starts a new
	 * Connection thread, and adds it to static connections.
	 * 
	 * @return The new connection
	 */
	public synchronized Connection incomingConnection(Socket s) throws IOException {
		log.debug("Incoming connection: " + Settings.socketAddress(s));
		Connection c = new Connection(s);
		connections.put(c, null);
		return c;
	}

	/**
	 * Called by initiateConnection, and also might be called when a peer is trying
	 * to become a superpeer. Assume that the other server is a superpeer. This might not 
	 * actually be true, but in that case, they would directly send you a SERVER_REDIRECT.
	 */
	public synchronized Connection outgoingConnection(Socket s) throws IOException {
		log.debug("Outgoing connection: " + Settings.socketAddress(s));
		Connection c = new Connection(s);
		c.setConType(ConType.SUPERPEER);
		connections.put(c, null);
		return c;
	}

	/**
	 * Called by all connections that are not with superpeers.
	 * If first message, goes to NewConnMsg, which might create a ConnectedClient or ConnectedServer
	 * and add them to Control's connections.
	 *
	 * @return true to quit connection, false otherwise
	 */
	public synchronized boolean process(Connection con, String msg) {
		try {
			if (con.getConType() == ConType.NEW) {
				return NewConnMsg.process(msg, con);
			} else if (con.getConType() == ConType.CLIENT) {
				ConnectedClient cc = (ConnectedClient) connections.get(con);
				return cc.process(msg);
			} else {
				ConnectedServer cs = (ConnectedServer) connections.get(con);
				return cs.process(msg);
			}
			// if any exception is caught, send INVALID_MESSAGE, and send error
			// message, then return true, which would terminate the connection
		} catch (Exception e) {
			e.printStackTrace();
			JSONObject invalidMessage = new JSONObject();
			try {
				invalidMessage.put("command", "INVALID_MESSAGE");
				invalidMessage.put("info", e.getMessage());
			} catch (JSONException e1) {
				e1.printStackTrace();
			}
			con.writeMsg(invalidMessage.toString());
			return true;
		}
	}

	/**
	 * Check if I have a supervisor or not, if yes, then do Supervisor process,
	 * otherwise do Superpeer process
	 * @param con
	 * @param msg
	 * @return false if connection should quit, true otherwise
	 */
	public synchronized boolean processSuperpeer(Connection con, String msg) {
		try {
			if (serverState == ServerState.PEER)
				return supervisor.processSupervisor(msg);
			else {
				ConnectedServer cs = (ConnectedServer) connections.get(con);
				if (serverState == ServerState.PEERTOSUPERPEER) {
					if (cs == supervisor) 
						return cs.processSupervisorWhileRaising(msg);
					else 
						return cs.processSuperpeer(msg);
				} else if (serverState == ServerState.SUPERPEER) {
					if (cs == masterSuperpeer)
						return cs.processMasterSuperpeer(msg);
					else
						return cs.processSuperpeer(msg);
				} else {
					return cs.processSuperpeer(msg);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			JSONObject invalidMessage = new JSONObject();
			try {
				invalidMessage.put("command", "INVALID_MESSAGE");
				invalidMessage.put("info", e.getMessage());
			} catch (JSONException e1) {
				e1.printStackTrace();
			}
			con.writeMsg(invalidMessage.toString());
			return true;
		}
	}

	public synchronized void connectionClosed(Connection con) {
		if (!term) {
			if (con == supervisor.getCon())
				handleSupervisorDisconnection();
			else if (con.getConType() == ConType.PEER) 
				handlePeerDisconnection((ConnectedServer) connections.get(con));
			else if (con.getConType() == ConType.SUPERPEER)
				handleSuperpeerDisconnection((ConnectedServer) connections.get(con));
			
			connections.remove(con);
		}
	}

	/**
	 * Check if this superpeer was the mastersuperpeer. If yes, then 
	 * update the mastersuperpeer with the backupmastersuperpeer.
	 * 
	 * For all (both master and otherwise), update all users which were
	 * associated with them, as well as all peers and the users connected
	 * to them.
	 * 
	 * @param connectedServer
	 */
	private void handleSuperpeerDisconnection(ConnectedServer connectedServer) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * Broadcast to superpeers that this peer has been disconnected. Get
	 * list of usernames connected to this peer, and pass them along as well.
	 * 
	 * Update own list of storedClients for the lastConnectedTimestamp.
	 * 
	 * @param connectedServer
	 */
	private void handlePeerDisconnection(ConnectedServer connectedServer) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * Check if we have any backupSupervisors. If we do, try to create
	 * an outgoingConnection to them, sending them LoginAttempts for 
	 * all the connectedClients.
	 * 
	 * Otherwise, raise own state to MasterSuperpeer.
	 */
	private void handleSupervisorDisconnection() {
		// TODO Auto-generated method stub
	}

	public void broadcast(String msg, Connection source) {
		log.info("Broadcasting " + msg);
		for (Connection con : connections.keySet())
			con.writeMsg(msg);
	}

	public void broadcastClient(String msg, Connection source) {
		for (Connection con : connections.keySet())
			if (con.getConType() == ConType.CLIENT)
				con.writeMsg(msg);
	}

	public void broadcastServer(String msg, Connection source) {
		for (Connection con : connections.keySet())
			if (con.getConType() != ConType.CLIENT)
				con.writeMsg(msg);
	}

	public void broadcastSuperpeer(String msg) {
		for (Connection con : connections.keySet())
			if (con.getConType() == ConType.SUPERPEER || con.getConType() == ConType.PEERTOSUPERPEER)
				con.writeMsg(msg);
	}

	public void broadcastPeer(String msg, Connection source) {
		for (Connection con : connections.keySet())
			if (con != source)
				if (con.getConType() == ConType.PEER || con.getConType() == ConType.PEERTOSUPERPEER)
					con.writeMsg(msg);
	}

	public void broadcastActivityMessage(String msg, Connection source) {
		// First, broadcast it to everyone.
		if (serverState == ServerState.PEER)
			supervisor.getCon().writeMsg(msg);
		else if (serverState == ServerState.PEERTOSUPERPEER) {
			supervisor.getCon().writeMsg(msg);
//			addWatchedMessage(messageUsername, sequence, messageOrigin, activity);
		}
		else 
		broadcastClient(msg, source);
		
		// TODO: Add sentTimestamp to an ACB_ACK and send it to
		// supervisor (if there) or all supervisors
		// Also TODO: Add ACB_ACK to ConnectedServer/Peer's process
	}

	/**
	 * Returns username's secret if present, otherwise returns null
	 */
	public String getUserSecret(String username) {
		return getStoredClient(username).getSecret();
	}
	
	public StoredClient getStoredClient(String username) {
		return this.registeredClients.get(username);
	}

	public ServerState getServerState() {
		return this.serverState;
	}

	public boolean inWaitingRegister(String username) {
		return this.waitingRegister.containsKey(username);
	}
	
	public void addWaitingRegister(String username, Connection con) {
		this.waitingRegister.put(username, con);
	}

	public void sendToSupervisor(String msg) {
		this.supervisor.getCon().writeMsg(msg);
	}

	public final void setTerm(boolean t){
		term=t;
	}
	
	public void sendToMasterSuperPeer(String msg) {
		this.masterSuperpeer.getCon().writeMsg(msg);
	}
	
	/**
	 * Gets the stored server from the storedServers map with
	 * the given id and return it.  
	 * @param id
	 * @return	the StoredServer with the given id, null if not found
	 */
	public StoredServer getStoredServer(String id) {
		return storedServers.get(id);
	}
	
	public void addStoredServer(StoredServer ss) {
		storedServers.put(ss.getId(), ss);
	}
	
	public void addRegisteredUser(String username, String secret) {
		StoredClient sc = new StoredClient(username, secret);
		registeredClients.put(username, sc);
	}
	
	public void addLoginWaitingClient(Connection con, String username) {
		ConnectedClient cc = new ConnectedClient(con);
		con.setConType(ConType.CLIENT);
		waitingLogin.put(username, cc);
		connections.put(con, cc);
	}
	
	/**
	 * Add peer locally.
	 * 
	 * Other superpeers will be informed when the serverAnnounce is processed.
	 * @param con
	 */
	public void addPeer(Connection con) {
		ConnectedServer cs = new ConnectedServer(con);
		con.setConType(ConType.PEER);
		connections.put(con, cs);
	}
	
	public int getLoad() {
		return connections.size();
	}
	
	public boolean userAlreadyLoggedIn(String username) {
		return loggedInClients.containsKey(username);
	}
	
	/**
	 * TODO: The redirection algorithm for whether and where to redirect a client 
	 * @return null if no redirect, otherwise the string to be sent
	 */
	public String redirectClient() {
		return null;
	}

	/**
	 * TODO: The redirection algorithm for whether and where to redirect a server (from superpeer)
	 * @return null if no redirect, otherwise the string to be sent
	 */
	public String redirectServer() {
		return null;
	}
	
	public JSONObject supervisorRedirect() throws JSONException {
		if (supervisor != null) {
	    	JSONObject json = new JSONObject();
			json.put("hostname", supervisor.getHostname());
			json.put("port", supervisor.getPort());
			return json;			
		}
		return null;
	}
	
	/**
	 * TODO
	 * Get user object from storedClients. Get the pendingMessages from there.
	 * For each message in pendingMessages, delete username
	 * from the KeySet in their StoredMessage. Make a JSONArray out of the message contents, and return it. 
	 */
	public JSONArray getUserPendingMessages(String username) {
		return null;
	}
	
	/**
	 * TODO: Add time offset 
	 * @return
	 */
	public long currentTime() {
		 Date d = new Date();
		 return d.getTime() - timeDelta;
	}

	/**
	 * When an activity broadcast is received at any superpeer, add it here.
	 * @param messageUsername
	 * @param sequence
	 * @param messageOrigin
	 * @param activity
	 */
	public void addWatchedMessage(String messageUsername, int sequence, long messageOrigin, JSONObject activity) {
		// TODO Auto-generated method stub
		
	}

	public void updateWatchedMessagePeerReceived(String messageUsername, int sequence) {
		// TODO Auto-generated method stub
		
	}

	public StoredMessage getStoredMessage(String messageId) {
		// TODO Auto-generated method stub
		return null;
	}

	public void loginClient(Connection conn, String username, String secret) throws Exception {
		StoredClient sc = getStoredClient(username);
		if (sc == null)
			throw new Exception("Did not find stored client");
		ConnectedClient cc = (ConnectedClient) connections.get(conn);
		cc.setStoredClient(sc);
		sc.login(this.selfStoredServer);
	}

}