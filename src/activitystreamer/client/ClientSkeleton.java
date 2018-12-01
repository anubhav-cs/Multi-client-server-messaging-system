package activitystreamer.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import activitystreamer.util.MessageHandler;
import activitystreamer.util.Settings;

public class ClientSkeleton extends Thread {
	private static final Logger log = LogManager.getLogger();
	private static ClientSkeleton clientSolution;
	private TextFrame textFrame;
	private Socket socket = null;
	private DataInputStream in;
	private DataOutputStream out;
	private BufferedReader inreader;
	private PrintWriter outwriter;
	private boolean anonymous;
	private boolean registered;
	private boolean loggedIn;
	
	/*
	 * returns true if the message was written, otherwise false
	 */
	public boolean writeMsg(String msg) {
		outwriter.println(msg);
		outwriter.flush();
		return true;
	}

	public static ClientSkeleton getInstance() {
		if (clientSolution == null) {
			clientSolution = new ClientSkeleton();
		}
		return clientSolution;
	}

	public ClientSkeleton() {
		textFrame = new TextFrame();
		start();
	}

	private void initializeConnection() throws UnknownHostException, IOException, ParseException {
		this.socket = new Socket(Settings.getRemoteHostname(), Settings.getRemotePort());
		this.in = new DataInputStream(socket.getInputStream());
		this.out = new DataOutputStream(socket.getOutputStream());
		this.inreader = new BufferedReader( new InputStreamReader(in));
		this.outwriter = new PrintWriter(out, true);
		log.info("Connection established");
		textFrame.setConnectionStateText("Connection established.");

		String username = Settings.getUsername();
		String secret = Settings.getSecret();

		this.loggedIn = false;
		this.registered = false;
		this.anonymous = false;
		
		// if username not given, empty or "anonymous" send anonymous login message
		if (username == null || username.isEmpty() || username.equals("anonymous")) {
			log.info("sending anonymous login");
			textFrame.setConnectionStateText("Sending anonymous login.");
			sendAnonymousLoginMessage();
			this.anonymous = true;
			this.loggedIn = readUntilReceiveCommand("LOGIN_SUCCESS", "LOGIN_FAILED");
			if (this.loggedIn) {
				textFrame.setConnectionStateText("Logged in as anonymous");
			} else {
				textFrame.setConnectionStateText("Failed to log in as anonymous");
			}
		}
		// if username is given but secret isn't, first send register
		// and wait for REGISTER_SUCCESS
		else {
			if (secret == null || secret.isEmpty()) {
				log.info("sending register message");
				textFrame.setConnectionStateText("Attempting to register with username");
				sendRegisterMessage();
				this.registered = readUntilReceiveCommand("REGISTER_SUCCESS", "REGISTER_FAILED");
				if (this.registered) {
					textFrame.setConnectionStateText("Successfully registered with secret "+Settings.getSecret());
				} else {
					textFrame.setConnectionStateText("Failed to register.");
				}
			} 
			// if both username and secret are given, assume that you're 
			// already registered, wait for login
			if (this.registered || (secret != null && !secret.isEmpty())) {
				log.info("sending login message");
				textFrame.setConnectionStateText("Attempting to login as user " + username);
				sendLoginMessage();
				this.loggedIn = readUntilReceiveCommand("LOGIN_SUCCESS", "LOGIN_FAILED");
				if (this.loggedIn) {
					textFrame.setConnectionStateText("Logged in as " + username);
				} else {
					textFrame.setConnectionStateText("Failed to log in as " + username);
				}
			}
		}
		
	}

	private boolean readUntilReceiveCommand(String command, String failCommand) throws IOException, ParseException {
		boolean stopReading = false;
		boolean success = false;
		String data;
		while (!stopReading && (data = this.inreader.readLine())!=null) {
			JSONObject json = MessageHandler.stringToJSONObject(data);
			String receivedCommand = (String)json.get("command");
			if (receivedCommand.equals(command)) {
				success = true;
				stopReading = true;
				log.info(data);
				break;
			} else if (receivedCommand.equals(failCommand)) {
				log.info("Received message with command: "+failCommand);
				success = false;
				stopReading = true;
				break;
			} else {
				log.info("Received message with command: "+receivedCommand);
			}
		}
		return success;
	}

	public void sendJsonObject(JSONObject json) {
		writeMsg(json.toJSONString());
	}

	@SuppressWarnings("unchecked")
	public void sendActivityObject(JSONObject activityObj) {
		try {
			JSONObject activityObject = new JSONObject();
			activityObject.put("command", "ACTIVITY_MESSAGE");
			activityObject.put("activity", activityObj);
			activityObject.put("username", Settings.getUsername());
			activityObject.put("secret", Settings.getSecret());
			sendJsonObject(activityObject);
		} catch (Exception e) {	
			e.printStackTrace();
		}
	}

	public void disconnect() {
		if (!socket.isClosed()) {
			textFrame.setConnectionStateText("Logging out.");
			log.info(("Sending logout"));
			JSONObject logout = new JSONObject();
			logout.put("command", "LOGOUT");
			sendJsonObject(logout);	
		}
	}

	public void run() {
		try {
			initializeConnection();
			String data;
			if (this.socket.isClosed()) {
				textFrame.setConnectionStateText("Disconnected");
			}
			while((data = this.inreader.readLine())!=null){				
				JSONObject json = MessageHandler.stringToJSONObject(data);
				String command = (String)json.get("command");
				log.info("Recieved message from server : "+data);
				if (command.equals("ACTIVITY_BROADCAST"))
					setTextFrameOutput(json);
					
				if (command.equals("REDIRECT"))
					redirect(json);
				if (this.socket.isClosed()) {
					textFrame.setConnectionStateText("Disconnected");
				}
			}
			this.in.close();
		} catch (UnknownHostException e) {
			log.error("Could not connect to the specified server");
		} catch (IOException e) {
			log.error("connection "+Settings.socketAddress(socket)+" closed with exception: "+e);
			textFrame.setConnectionStateText("Disconnected");
		} catch (ParseException e) {
			e.printStackTrace();
			log.error("error parsing received json: "+e);
		}
	}

	private void setTextFrameOutput(JSONObject json) {
		JSONObject activity = (JSONObject)json.get("activity");
		this.textFrame.setOutputText(activity);
	}

	public void redirect(JSONObject json) throws IOException, ParseException {
		String hostname = (String)json.get("hostname");
		Long longPort = (Long) json.get("port");
		int port = longPort.intValue();
		log.info("Redirecting to "+hostname+":"+port);
		textFrame.setConnectionStateText("Redirecting to "+hostname+":"+port);
		Settings.setRemoteHostname(hostname);
		Settings.setRemotePort(port);

		initializeConnection();
	}

	public void sendLoginMessage() {
		JSONObject loginMessage = new JSONObject();
		loginMessage.put("command", "LOGIN");
		loginMessage.put("username", Settings.getUsername());
		loginMessage.put("secret", Settings.getSecret());
		sendJsonObject(loginMessage);
	}

	public void sendAnonymousLoginMessage() {
		JSONObject loginMessage = new JSONObject();
		loginMessage.put("command", "LOGIN");
		loginMessage.put("username", "anonymous");
		sendJsonObject(loginMessage);
	}

	public void sendRegisterMessage() {
		JSONObject registerMessage = new JSONObject();
		String username = Settings.getUsername();
		String secret = Settings.getSecret();

		if (secret == null) {
			secret = Settings.nextSecret();
			Settings.setSecret(secret);
			log.info("Created random secret: "+secret);
		}

		registerMessage.put("command", "REGISTER");
		registerMessage.put("username", Settings.getUsername());
		registerMessage.put("secret", Settings.getSecret());
		sendJsonObject(registerMessage);
	}

}
