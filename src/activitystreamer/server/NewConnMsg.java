package activitystreamer.server;

import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import activitystreamer.commandhandlers.ConnectedClient;
import activitystreamer.commandhandlers.ConnectedServer;
import activitystreamer.server.storednodes.StoredClient;
import activitystreamer.server.storednodes.StoredServer;
import activitystreamer.util.Settings;

public class NewConnMsg {

	private static Logger log = LogManager.getLogger();
    // Constant Initialization
    private static final String COMMAND = "command";
    private static final String USER = "username";
    private static final String PASS = "secret";

    private static NewConnMsg newConnMsg = null;

    private NewConnMsg() {
        super();
    }

    public static NewConnMsg getInstance() {
        if (newConnMsg == null) {
            newConnMsg = new NewConnMsg();
        }
        return newConnMsg;
    }

    /**
     * The process method receives incoming messages from new connections.
     * It performs validation checks and processes the messages
     * @param msg
     * @param conn
     * @return	false if the message was correctly processed
     * 			else returns true
     * @throws Exception
     */
    public static boolean process(String msg, Connection conn) throws Exception {
        JSONObject json = new JSONObject(msg);
        String msgCommand = (String) json.get("command");
        if (msgCommand == null)
            throw new InvalidArgsException("No command present");
        log.info("Received "+msgCommand+" from " + conn.getSocket().getInetAddress());
        switch (msgCommand) {
        case "REGISTER":
            return manageRegisterRequest(json, conn);
        case "LOGIN":
            return manageLoginRequest(json, conn);
        case "AUTHENTICATE":
            return manageAuthenticateRequest(json, conn);
        default:
            throw new Exception("Invalid message type from " + "unauthenticated connection");
        }
    }

    // Method to create {"command": command, "info": info} JSONString
    private static String createReplyMsg(String command, String info) {
        JSONObject replyMsg = new JSONObject();
        try {
            replyMsg.put(COMMAND, command);
            replyMsg.put("info", info);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return replyMsg.toString();
    }

    /**
     * 
     * @param json 
     * @param conn Connection where this was received
     * @return	true if REGISTER_FAILED, false otherwise
     * @throws Exception
     */
    private static boolean manageRegisterRequest(JSONObject json, Connection conn) throws Exception {
        String username = json.getString(USER);
        String secret = json.getString(PASS);
        boolean registerFailed = false;
        
        // Fail if either is missing
        if (username == null || secret == null || username.isEmpty() || secret.isEmpty())
        	throw new InvalidArgsException("username/secret missing");
        else {
        	// Check if username is already known about / attempted to register
            String storedSecret = Control.getInstance().getUserSecret(username);
            boolean inRegisterAttempt = Control.getInstance().inWaitingRegister(username);
            if (storedSecret != null || inRegisterAttempt)
            	registerFailed = true;
            else {
                ServerState serverState = Control.getInstance().getServerState();

                if (serverState == ServerState.MASTERSUPERPEER) {
                	Control.getInstance().addRegisteredUser(username, secret);
                	String registerSuccess = createRegisterSuccess(username, secret);
                	Control.getInstance().broadcastSuperpeer(registerSuccess);
                	if (conn.getConType() != ConType.SUPERPEER)
                		conn.writeMsg(registerSuccess);
                	return false;
                } else if (serverState == ServerState.SUPERPEER) {
                	String registerRequest = createRegisterRequest(username, secret);
                	Control.getInstance().sendToMasterSuperPeer(registerRequest);
                	// Add this username to waiting register, linked to this conn
                	Control.getInstance().addWaitingRegister(username, conn);
                	return false;
                } else {
                    String registerRequest = createRegisterRequest(username, secret);
                    Control.getInstance().sendToSupervisor(registerRequest);
                    // Add this username to waiting register, linked to this conn
                	Control.getInstance().addWaitingRegister(username, conn);
                    return false;
                }
            }
        }  
        
        if (registerFailed)
        	conn.writeMsg(
                createReplyMsg("REGISTER_FAILED", username + "is already registered with the " + "system"));
        return registerFailed;
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
     * 
     * @param json
     * @param conn
     * @return true if login failed, false otherwise
     * @throws Exception
     */
    private static boolean manageLoginRequest(JSONObject json, Connection conn) throws Exception {
        String username = json.getString(USER);
        String secret = null;
        boolean loginFailed = false;
        ServerState serverState = Control.getInstance().getServerState();
        Control control = Control.getInstance();
        
        if (username.equals("anonymous"))
        	return redirectOrLogin(username, secret, conn);
        
        secret = json.getString(PASS);
        StoredClient sc = Control.getInstance().getStoredClient(username);
        if (sc == null) {
        	if (serverState == ServerState.MASTERSUPERPEER || serverState == ServerState.SUPERPEER)
        		loginFailed = true;
        	else {
        		control.sendToSupervisor(createLoginAttempt(username, secret));
        		control.addLoginWaitingClient(conn, username);
        		return false;
        	}
        }
        else {
	        if (secret == null || !secret.equals(sc.getSecret()))
	        	loginFailed = true;
	        else {
	        	// secret matches stored one
                if (serverState == ServerState.MASTERSUPERPEER || serverState == ServerState.SUPERPEER) {    	
                	// Send LOGIN_SUCCESS and add to loggedInClients 
                	// Broadcast a NEW_LOGIN message	
            		boolean redirected = redirectOrLogin(username, secret, conn);
            		if (!redirected) {
            			control.broadcastSuperpeer(newLoginMessage(username));
            			control.loginClient(conn, username, secret);
            		}
                	return redirected;	                            	
	            }
	        }
        }
        // else, login successful
        if (loginFailed)
        	conn.writeMsg(createReplyMsg("LOGIN_FAILED", "attempt to login with wrong secret"));
        return loginFailed;
        
    }
    
    private static String createLoginAttempt(String username, String secret) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
     * Sent to superpeers / supervisor after a new user logs in.
     * @param username
     * @return
     */
    private static String newLoginMessage(String username) {
    	JSONObject json = new JSONObject();
    	try {
    		json.put("command", "NEW_LOGIN");
    		json.put("username", username);
    		json.put("id", Settings.getId());
    		json.put("timestamp", new Date());
    	} catch (JSONException e) {
    		e.printStackTrace();
    	}
    	return json.toString();
    }

    /**
     * 
     * @param username
     * @param secret
     * @param con
     * @return	true if redirect, false if login
     */
    private static boolean redirectOrLogin(String username, String secret, Connection con) throws Exception {
    	String redirect = Control.getInstance().redirectClient();
    	if (redirect == null) {
            con.writeMsg(createReplyMsg("LOGIN_SUCCESS", "logged in as user " + username));     
        	Control.getInstance().loginClient(con, username, secret);
        	return false;
        }
    	con.writeMsg(redirect);
    	return true;
    }
    
    
    /**
     * If secret matches, and I am a superpeer/mastersuperpeer, instantly check
     * for redirection. If I am a peer, then redirect to my superpeer.
     * If secret does not match, send back an AUTHENTICATION_FAIL.
     * 
     * @param json
     * @param conn
     * @return True if it should be disconnected.
     * @throws Exception
     */
    private static boolean manageAuthenticateRequest(JSONObject json, Connection conn) throws Exception {
        String secret = json.getString(PASS);
        if (secret.equals(Settings.getSecret())) {
        	ServerState serverState = Control.getInstance().getServerState();
     
        	if (serverState == ServerState.PEER || serverState == ServerState.PEERTOSUPERPEER) {
        		// Redirect to superpeer
        		JSONObject redirect = Control.getInstance().supervisorRedirect();
        		redirect.put("command", "SERVER_REDIRECT");
        		return true;
        	} else {
        		String redirect = Control.getInstance().redirectServer();
        		if (redirect == null) {
        			// don't redirect
        			Control.getInstance().addPeer(conn);
        			return false;
        		}
        		conn.writeMsg(redirect);
        		return true;
        	}
        } else {
            conn.writeMsg(createReplyMsg("AUTHENTICATION_FAIL", "the supplied secret is incorrect : " + secret));
            return true;
        }
    }

//    private static boolean manageRedirection(ConnectedClient cc) throws Exception {
//
//        Random randomGenerator;
//
//        ArrayList<ConnectedServer> eligibleServers = Control.getInstance().getRedirectEligibleServers();
//
//        if (eligibleServers.isEmpty()) {
//            Control.addConnectedClient(cc);
//            return true;
//        }else {
//            randomGenerator = new Random();
//            int index = randomGenerator.nextInt(eligibleServers.size());
//            
//            ConnectedServer redirectServer = eligibleServers.get(index);
//            cc.getConnection().writeMsg(createRedirectMsg(redirectServer));
//            return false;
//        }
//    }
}
