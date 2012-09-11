package org.hufewiesen;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;


public class DBPixel extends JsonObject {	
	
	public DBPixel(JsonObject from) {
		mergeIn(from);
	}

	public String getName() {
		return getString("name");
	}
	
	public void setName(String name) {
		putString("name", name);
	}
	
	public int getX() {
		return getInteger("x");
	}
	
	public void setX(int x) {
		putNumber("x", x);
	}
	
	public int getY() {
		return getInteger("y");
	}
	
	public void setY(int y) {
		putNumber("y", y);
	}
	
	public String getEmail() {
		return getString("email");
	}
	
	public void setEmail(String email) {
		putString("email", email);
	}
	
	public String getState() {
		return getString("state");
	}
	
	public void setState(String state) {
		putString("state", state);
	}
	
	public String getMessage() {
		return getString("message");		
	}
	
	public void setMessage(String message) {
		putString("message", message);
	}
	
	public String getUrl() {
		return getString("url");		
	}
	
	public void setUrl(String url) {
		putString("url", url);
	}
	
	public String getId() {
		return getString("_id");
	}
	
	public static List<DBPixel> fromResult(JsonArray result) {
		List<DBPixel> pixels = new ArrayList<DBPixel>();
		Iterator<Object> resultIt = result.iterator();
		while(resultIt.hasNext()) {
			pixels.add(new DBPixel((JsonObject) resultIt.next()));
		}
		return pixels;
	}
}
