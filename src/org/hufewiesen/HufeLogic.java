package org.hufewiesen;

import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;


public class HufeLogic {
	private static final Logger LOG = Logger.getLogger(HufeLogic.class.getName());
	private Vertx vertx;
	private JsonObject config;
	private PaypalConnector paypalConnector;
	
	
	public HufeLogic(Vertx vertx, JsonObject config, PaypalConnector paypal) {
		this.vertx = vertx;
		this.config = config;
		this.paypalConnector = paypal;
		
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
	
	public Handler<Message<JsonObject>> getPxUpdateHanlder() {
		return new Handler<Message<JsonObject>>(){

			@Override
			public void handle(Message<JsonObject> pxUpdate) {
				JsonArray pixels = pxUpdate.body.getArray("pixels");
				LOG.info("pixel udpate: " + pixels);
				if(pixels != null) {
					// update server state
					for(Object px : pixels) {
						if(px instanceof JsonObject) {
							String id = ((JsonObject) px).getString("_id");
							Boolean visible = ((JsonObject) px).getBoolean("visible");
							((JsonObject) px).putNumber("timestamp", System.currentTimeMillis());
							if(id != null && visible != null && visible) {
								vertx.sharedData().getMap("pxUpdates").put(id, ((JsonObject) px).encode());								
							} else {
								vertx.sharedData().getMap("pxUpdates").remove(id);
							}
						}
					}
					// update clients
					vertx.eventBus().publish("hs.client.pxUpdate", pxUpdate.body);
				}
				
				
			}
			
		};
	}
	
	public Handler<Long> getPxUpdateCleanup() {
		return new Handler<Long>(){
			@Override
			public void handle(Long arg0) {
				long timeout = config.getLong("pixelTimout");
				Map pxUpdates = vertx.sharedData().getMap("pxUpdates");
				JsonArray updates = new JsonArray();
				for(Object key : pxUpdates.keySet()) {
					Object pxValue = pxUpdates.get(key);
					if(pxValue instanceof String) {
						JsonObject px = new JsonObject(pxValue.toString());
						Long timestamp = px.getLong("timestamp");
						if(timestamp != null && timestamp < System.currentTimeMillis() - timeout) {
							px.putBoolean("visible", false);
							updates.add(px);
							pxUpdates.remove(key);
						}
					}
				}
				
				if(updates.size() > 0) {
					LOG.info("resetting reserved pixels: " + updates.size());
					vertx.eventBus().publish("hs.client.pxUpdate", new JsonObject().putArray("pixels", updates));
				}
				
			}
			
		};
	}
	
	public Handler<Message<JsonObject>> getLoadPixelsHandler() {
		return new Handler<Message<JsonObject>>(){

			@Override
			public void handle(final Message<JsonObject> msg) {
				JsonObject query = new JsonObject()
					.putString("action", "find")
					.putString("collection", "pixels")
					.putObject("matcher", new JsonObject());
				// LOG.info("querying DB: " + query.encode());
				vertx.eventBus().send("hs.db", query, new Handler<Message<JsonObject>>(){

					@Override
					public void handle(Message<JsonObject> reply) {
						// LOG.info("db result: " + reply.body.encode());
						if(reply.body.getString("status").equals("ok")) {
							
							// add pxUpdates to results
							for(Object px : vertx.sharedData().getMap("pxUpdates").values()) {
								reply.body.getArray("results").add(new JsonObject(px.toString()));
							}
						}
						msg.reply(reply.body);						
					}
					
				});
				
			}
			
		};
	}
	
	public Handler<Message<JsonObject>> getPaypalConfigHandler() {
		return new Handler<Message<JsonObject>>() {

			@Override
			public void handle(final Message<JsonObject> msg) {
				
				paypalConnector.setExpressCheckout(
					new JsonObject()
						.putString("AMT", "10")
						.putString("cancelUrl", config.getString("serverUrl"))
						.putString("returnUrl", config.getString("serverUrl")), 
					new Handler<JsonObject>(){
						@Override
						public void handle(JsonObject paypalResponse) {
							String token = paypalResponse.getString("TOKEN");
							LOG.info("received PaypalToken: " + token);
							JsonObject reply = new JsonObject();
							reply.putString("token", token)
								.putString("checkoutUrl", paypalConnector.getCheckoutUrl(token));
							msg.reply(reply);
							
						}
					});
				
			}
			
		};
	}
}
