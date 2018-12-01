package activitystreamer.util;

import java.util.HashMap;

import com.fasterxml.jackson.core.JsonProcessingException;

public class InvalidMessage extends Exception {
    
	private static final long serialVersionUID = 6161969269779577451L;
    
	private static final String MSGCOMMAND = "INVALID_MESSAGE";

    public InvalidMessage(String msg) {
        super(createJsonStr(msg));
    }
    
    public InvalidMessage() {
        super(createJsonStr(""));
    }
    
    private static String createJsonStr(String msg) {
        HashMap<String, String> msgHashMap = new HashMap<>();
        msgHashMap.put("command", MSGCOMMAND);
        msgHashMap.put("info", msg);
        String jsonStr = null;
        try {
        jsonStr = MessageHandler.hashmapToJsonString(msgHashMap);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return jsonStr;
    }
} 