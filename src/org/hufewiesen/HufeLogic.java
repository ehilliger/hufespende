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
			public void handle(final Message<JsonObject> submitMsg) {
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
				
				JsonArray savedPxIds = new JsonArray();
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
					final String pxId = px.getId();
					savedPxIds.add(pxId);
					vertx.eventBus().send("hs.db", saveMsg, new Handler<Message<JsonObject>>(){
						@Override
						public void handle(Message<JsonObject> msg) {
							if(msg.body.getString("status").equals("ok")) {
								// remove pixels from shared reserved
								vertx.sharedData().getMap("pxUpdates").remove(pxId);
							} else {
								LOG.severe("save operation error: " + msg.body.encode());								
							}							
						}
						
					});
									
				}
				
				int qty = savedPxIds.size();
				// TODO make price configurable
				int amt = savedPxIds.size() * 5;
				
				// create transaction
				final JsonObject txnRecord = new JsonObject()
					.putString("name", name)
					.putString("email", email)
					.putNumber("qty", qty)
					.putNumber("amt", amt)
					.putArray("pixelIds", savedPxIds);
				
				// get PaypalToken
				JsonObject paypalRequest = new JsonObject()
					.putString("PAYMENTREQUEST_0_PAYMENTACTION", "SALE")
					.putString("PAYMENTREQUEST_0_AMT", String.valueOf(amt))
					.putString("PAYMENTREQUEST_0_ITEMAMT", String.valueOf(amt))
					.putString("PAYMENTREQUEST_0_CURRENCYCODE", "EUR")
					.putString("L_PAYMENTREQUEST_0_ITEMCATEGORY0", "Digital")
					.putString("L_PAYMENTREQUEST_0_NAME0", "Hufewiesen Pixel")
					.putString("L_PAYMENTREQUEST_0_QTY0", String.valueOf(qty))
					.putString("L_PAYMENTREQUEST_0_AMT0", "5")
					// TODO make this Thank You / close URLs
					.putString("cancelUrl", config.getString("serverUrl"))
					.putString("returnUrl", config.getString("serverUrl")); 
				
				LOG.info("sending Paypal request: " + paypalRequest.encode());
				
				paypalConnector.setExpressCheckout(
					paypalRequest
					,new Handler<JsonObject>(){
						@Override
						public void handle(JsonObject paypalResponse) {
							final String token = paypalResponse.getString("TOKEN");
							
							LOG.info("received PaypalToken: " + token);
							
							final JsonObject submitReply = new JsonObject()
								.putString("token", token)
								.putString("checkoutUrl", paypalConnector.getCheckoutUrl(token));
							
							// and save a DB TXN record
							txnRecord.putString("token", token);
							txnRecord.putString("state", "SetExpressCheckout");
							
							JsonObject saveTxnMsg = new JsonObject()
								.putString("action", "save")
								.putString("collection", "transactions")
								.putObject("document", txnRecord);
							
							LOG.info("saving paypal transaction: " + saveTxnMsg.encode());
							vertx.eventBus().send("hs.db", saveTxnMsg, new Handler<Message<JsonObject>>(){

								@Override
								public void handle(Message<JsonObject> txnReply) {
									if("ok".equals(txnReply.body.getString("status"))) {
										submitReply.putString("status", "ok");
										// and send back to client
										submitMsg.reply(submitReply);
									}
									
								}
								
							});
							
							
						}
					});
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
	
	
	public Handler<Long> getBuyerInfoTxHandler() {
		return new Handler<Long>() {

			@Override
			public void handle(Long arg0) {
				JsonObject query = new JsonObject()
					.putString("action", "findone")
					.putString("collection", "transactions")
					.putObject("matcher", new JsonObject()
							.putString("state", "SetExpressCheckout"));
				// query
				vertx.eventBus().send("hs.db", query, new Handler<Message<JsonObject>>(){

					@Override
					public void handle(Message<JsonObject> dbReply) {
						if("ok".equals(dbReply.body.getString("status"))) {
							final JsonObject txRecord = dbReply.body.getObject("result");
							if(txRecord != null) {
								String token = txRecord.getString("token");
								LOG.info("getting byer info for transaction: " + txRecord.getString("_id"));
								
								// do Paypal Call
								paypalConnector.getExpressCheckoutDetails(
									new JsonObject().putString("TOKEN", token), 
									new Handler<JsonObject>(){

										@Override
										public void handle(JsonObject paypalResponse) {
											if("Success".equals(paypalResponse.getString("ACK"))) {
												LOG.info("got checkout details:" + paypalResponse.encode());
												
												String payerId = paypalResponse.getString("PAYERID");
												if(payerId != null) {
													// save transaction record
													txRecord.putString("payerId", payerId);
													txRecord.putString("state", "GetExpressCheckoutDetails");
													vertx.eventBus().send("hs.db", new JsonObject()
														.putString("action", "save")
														.putString("collection", "transactions")
														.putObject("document", txRecord));
													
												} else {
													txRecord.putString("state", "failed-noPayerId");
													vertx.eventBus().send("hs.db", new JsonObject()
														.putString("action", "save")
														.putString("collection", "transactions")
														.putObject("document", txRecord));
													// TODO remove pixels from DB
												}
											}											
										}										
									}
								);								
							}
						}						
					}					
				});				
			}			
		};
	}
	
	public Handler<Long> getCapturePaymentTxHandler() {
		return new Handler<Long>() {

			@Override
			public void handle(Long arg0) {
				JsonObject query = new JsonObject()
					.putString("action", "findone")
					.putString("collection", "transactions")
					.putObject("matcher", new JsonObject()
							.putString("state", "GetExpressCheckoutDetails"));
				// query
				vertx.eventBus().send("hs.db", query, new Handler<Message<JsonObject>>(){

					@Override
					public void handle(Message<JsonObject> dbReply) {
						if("ok".equals(dbReply.body.getString("status"))) {
							final JsonObject txRecord = dbReply.body.getObject("result");
							if(txRecord != null) {
								LOG.info("capturing transaction: " + txRecord.getString("_id"));
								
								// do Paypal Call
								paypalConnector.getExpressCheckoutDetails(
									new JsonObject()
										.putString("TOKEN", txRecord.getString("token"))
										.putString("PAYERID", txRecord.getString("payerId"))
										.putString("PAYMENTREQUEST_0_PAYMENTACTION", "SALE")
										.putString("PAYMENTREQUEST_0_AMT", String.valueOf(txRecord.getNumber("amt")))
										.putString("PAYMENTREQUEST_0_CURRENCYCODE", "EUR")
									,new Handler<JsonObject>(){

										@Override
										public void handle(JsonObject paypalResponse) {
											if("Success".equals(paypalResponse.getString("ACK"))) {
												LOG.info("captured payment:" + paypalResponse.encode());
												
												String token = paypalResponse.getString("TOKEN");
												if(token != null) {
													// save transaction record
													txRecord.putString("captureToken", token);
													txRecord.putString("state", "Captured");
													vertx.eventBus().send("hs.db", new JsonObject()
														.putString("action", "save")
														.putString("collection", "transactions")
														.putObject("document", txRecord));
													
													// make the pixels "green"
													updatePixels(txRecord.getArray("pixelIds"), "bought", true);
													
												} else {
													txRecord.putString("state", "failed-capturing");
													vertx.eventBus().send("hs.db", new JsonObject()
														.putString("action", "save")
														.putString("collection", "transactions")
														.putObject("document", txRecord));
												}
											}											
										}										
									}
								);								
							}
						}						
					}					
				});				
			}			
		};
	}
	
	
	/**
	 * Update pixels collection with new states and propagate to clients
	 * 
	 * @param pxIds array of pixel IDs
	 * @param state the new state
	 * @param visible visible or not
	 */
	private void updatePixels(JsonArray pxIds, final String state, final boolean visible) {
		JsonObject query = new JsonObject()
			.putString("action", "find")
			.putString("collection", "pixels")
			.putObject("matcher", new JsonObject()
				.putObject("_id", new JsonObject()
					.putArray("$in", pxIds)));
		
		vertx.eventBus().send("hs.db", query, new Handler<Message<JsonObject>>(){

			@Override
			public void handle(Message<JsonObject> dbReply) {
				if("ok".equals(dbReply.body.getString("status"))) {
					JsonArray updatedPixels = new JsonArray();					
					for(final Object px : dbReply.body.getArray("results")) {
						((JsonObject) px)
							.putString("state", state)
							.putBoolean("visible", visible);
						
						JsonObject update = new JsonObject()
							.putString("action", "save")
							.putString("collection", "pixels")
							.putObject("document", (JsonObject) px);
						
						// save the update
						vertx.eventBus().send("hs.db", update);
						// remember to tell clients
						updatedPixels.add(px);
					}
					
					// tell clients
					vertx.eventBus().publish("hs.client.pxUpdate", new JsonObject().putArray("pixels", updatedPixels));					
				}
				
			}
			
		});
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
}
