package org.hufewiesen;

import java.util.StringTokenizer;
import java.util.logging.Level;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.deploy.Container;

import sun.security.action.GetLongAction;


public class PaypalConnector extends BusModBase {	
	
	JsonObject paypalCfg = null;
	
	HttpClient client = null;

	// TODO make this a real module some time
	public PaypalConnector(JsonObject cfg, Container container, Vertx vertx) {
		this.paypalCfg = cfg;
		setContainer(container);
		setVertx(vertx);		
		start();
	}



	@Override
	public void start() {
		super.start();
		client = vertx.createHttpClient()
				.setSSL(true)
				.setTrustAll(true)
				.setHost(paypalCfg.getString("server"))
				.setPort(paypalCfg.getInteger("port"));
	}

	@Override
	public void stop() throws Exception {
		// TODO Auto-generated method stub
		super.stop();
	}
	
	public String getCheckoutUrl(String token) {
		return paypalCfg.getString("checkoutUrl") + token;
	}
	
	public void setExpressCheckout(JsonObject formData, final Handler<JsonObject> responseBodyHandler) {
		formData.putString("METHOD", "SetExpressCheckout");
		doPaypalCall(formData, responseBodyHandler);
	}
	
	private PaypalConnector doPaypalCall(JsonObject  formData, final Handler<JsonObject> responseBodyHandler) {
		HttpClientRequest req =	client.post(paypalCfg.getString("api"), new Handler<HttpClientResponse>(){

			@Override
			public void handle(final HttpClientResponse response) {
				response.exceptionHandler(new Handler<Exception>(){

					@Override
					public void handle(Exception e) {						
						logger.error("error during paypal communication: ", e);
						logger.error("status code: " + response.statusCode);
						logger.error("status msg:  " + response.statusMessage);								
					}
					
				});
				response.bodyHandler(new Handler<Buffer>(){

					@Override
					public void handle(Buffer body) {
						JsonObject paypalResponse = fromFormValues(body.toString());
						if(paypalResponse.getString("ACK").equalsIgnoreCase("Success")) {
							responseBodyHandler.handle(paypalResponse);							
						} else {
							logger.error("paypal error: " + body.toString());
						}
					}
					
				});
				
			}
			
		});			
		
		String form = toFormValues(formData
			.putString("USER", paypalCfg.getString("api_user"))
			.putString("PWD", paypalCfg.getString("api_pwd"))
			.putString("SIGNATURE", paypalCfg.getString("api_sgn"))
			.putString("VERSION", "89")
			// .putString("METHOD", "SetExpressCheckout")
			// .putString("cancelUrl", config.getString("serverUrl"))
			// .putString("returnUrl", config.getString("serverUrl"))
			// .putString("AMT", "10")
			);
		
		if(logger.isDebugEnabled()) {
			logger.debug("sending Paypal form: " + form);
		}
		
		req
			.putHeader("Content-Length", form.length())
			.write(form)
			.end();
		return this;
	}
	
	public static String toFormValues(JsonObject values) {
		StringBuilder result = new StringBuilder();
		String separator = "";
		for(String fieldName : values.getFieldNames()) {
			result.append(separator);
			result.append(fieldName).append("=").append(values.getString(fieldName));
			separator = "&";
		}
		return result.toString();
	}
	
	public JsonObject fromFormValues(String values) {
		StringTokenizer fields = new StringTokenizer(values, "& \n\t");
		JsonObject result = new JsonObject();
		while(fields.hasMoreTokens()) {
			String field = fields.nextToken();
			int sep = field.indexOf("=");
			if(sep > -1) {
				String name = field.substring(0, sep);
				String value = field.substring(sep+1);
				if(name != null && !name.isEmpty() && value != null && !values.isEmpty()) {
					result.putString(name, value);
				}
			}
		}
		return result;
	}
}
