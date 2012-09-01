package org.hufewiesen;

import java.util.List;
import java.util.logging.Logger;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;


public class HufeLogic {
	private static final Logger LOG = Logger.getLogger(HufeLogic.class.getName());
	private Vertx vertx;
	
	// private Mongo mongo;
	// private DB db;
	
	
	public HufeLogic(Vertx vertx) {
		this.vertx = vertx;
		
	}
	
	public Handler<Message<JsonObject>> getFormSubmitHandler() {
		return new Handler<Message<JsonObject>>(){

			@Override
			public void handle(Message<JsonObject> submitMsg) {
				// convert submit message to pixel objects
				String name = submitMsg.body.getString("name");
				String email = submitMsg.body.getString("email");
				String message = submitMsg.body.getString("message");
				String url = submitMsg.body.getString("url");
				List<DBPixel> pixels = DBPixel.fromResult(submitMsg.body.getArray("pixels"));
				
				if(name == null || "".equals(name)) {
					LOG.severe("error: no name given");
					return;
				}
				
				if(email == null || "".equals(email)) {
					LOG.severe("error: no email given");
					return;
				}
				
				if(pixels == null || pixels.isEmpty()) {
					LOG.severe("error: no pixels to save");
					return;
				}
				
				for(DBPixel px : pixels) {
					px.setName(name);
					px.setEmail(email.toLowerCase());
					px.setMessage(message);
					px.setUrl(url);
					px.setState("reserved");
					
					LOG.info("saving pixel: " + px.encode());
					JsonObject saveMsg = new JsonObject()
						.putString("action", "save")
						.putString("collection", "pixels")
						.putObject("document", px);
					LOG.info("save msg: " + saveMsg.encode());
					// save to DB
					vertx.eventBus().send("hs.db", saveMsg, new Handler<Message<JsonObject>>(){
						@Override
						public void handle(Message<JsonObject> errMsg) {
							LOG.severe("save operation error: " + errMsg.body.encode());
							
						}
						
					});
					
					// TODO publish to clients
				}
				
			}
			
		};
	}

}
