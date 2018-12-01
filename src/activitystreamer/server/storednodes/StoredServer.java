package activitystreamer.server.storednodes;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;

import activitystreamer.server.*;

public class StoredServer {
    private Logger log =  LogManager.getLogger();
    private String id;
    private String hostname;
    private int port;
    private int load;
    private Date stored;
    private Date updated;

    /**
     * Creates and sets user properties
     * 
     * @throws  InvalidArgsException    if any of the arguments are incorrect
     */
    public StoredServer(String id, String hostname, int port, int load) throws InvalidArgsException {
        if (id == null || id.isEmpty())
            throw new InvalidArgsException();
        this.id = id;
        this.update(hostname, port, load);
        this.stored = new Date();
    }

    /**
     * Update this user's hostname, port, load
     * 
     * @throws  InvalidArgsException    if any of the arguments are incorrect
     */
    public void update(String hostname, int port, int load) throws InvalidArgsException {
        if (hostname == null || hostname.isEmpty())
            throw new InvalidArgsException();
        if (port < 0 || port > 65535)
            throw new InvalidArgsException();
        if (load < 0)
            throw new InvalidArgsException();
        this.hostname = hostname;
        this.port = port;
        this.load = load;
        this.updated = new Date();
    }

    public String getId() {
        return this.id;
    }
    
    public JSONObject getJson() throws JSONException {
    	JSONObject json = new JSONObject();
		json.put("id", id);
		json.put("hostname", hostname);
		json.put("port", port);
		json.put("load", load);
		return json;
    }
    
    public JSONObject getRedirectJson() throws JSONException {
    	JSONObject json = new JSONObject();
		json.put("hostname", hostname);
		json.put("port", port);
		return json;
    }  
    
    public int getLoad() {
    	return this.load;
    }
    
    public String getHostname() {
    	return this.hostname;
    }
    
    public int getPort() {
    	return this.port;
    }
}