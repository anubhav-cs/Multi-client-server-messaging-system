package activitystreamer.util;

import java.util.HashMap;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.fasterxml.jackson.core.JsonProcessingException;

public class MessageHandler {

	public static String hashmapToJsonString(HashMap<String, String> fieldMap) throws JsonProcessingException {
		return hashmapToJsonObject(fieldMap).toJSONString();
	}

	public static JSONObject hashmapToJsonObject(HashMap<String, String> fieldMap) {
		JSONObject object = new JSONObject();
		object.putAll(fieldMap);
		return object;
	}

	public static HashMap<String, String> unmarshallJsonStringToMap(String jsonString) throws ParseException {
		JSONParser parser = new JSONParser();
		Object obj = parser.parse(jsonString);
		HashMap<String, String> map = (HashMap<String, String>) obj;
		return map;
	}

	public static JSONObject stringToJSONObject(String jsonString) throws ParseException {
		JSONParser parser = new JSONParser();
		JSONObject json;
		try {
			json = (JSONObject) parser.parse(jsonString);
			return json;
		} catch (ParseException e) {
			throw e;
		}
	}
}
