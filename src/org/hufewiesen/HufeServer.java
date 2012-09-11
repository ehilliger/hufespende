package org.hufewiesen;

import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.CookieDecoder;
import org.jboss.netty.handler.codec.http.CookieEncoder;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.deploy.Verticle;


public class HufeServer extends Verticle {
	public final static Logger LOG = Logger.getLogger(HufeServer.class.getName());
	
	public static final String SESSION_COOKIE = "hsid";
	
	@Override
	public void start() throws Exception {
		JsonObject config = container.getConfig();
		LOG.info("configuration: " + config);
		LOG.info("test:" + config.getString("test"));
		
		container.deployModule("vertx.mongo-persistor-v1.0", config.getObject("mongodb"));
		
		PaypalConnector paypalConnector = new PaypalConnector(config.getObject("paypal"), container, vertx);
		
		HufeLogic logic = new HufeLogic(vertx, config, paypalConnector);
		
		HttpServer server = vertx.createHttpServer();
		
				
		server.requestHandler(new Handler<HttpServerRequest>() {
			@Override
			public void handle(HttpServerRequest req) {
				Cookie sessionCookie = null;
				String cookieHeader = req.headers().get("Cookie");
				if(cookieHeader != null) {
					Set<Cookie> cookies = new CookieDecoder().decode(cookieHeader);
					for(Cookie c : cookies) {
						if(SESSION_COOKIE.equals(c.getName())) {
							sessionCookie = c;
							break;
						}
					}

				}
				// create a session if needed
				if(sessionCookie == null) {
					String sessionId = UUID.randomUUID().toString();
					CookieEncoder ce = new CookieEncoder(true);
					ce.addCookie(SESSION_COOKIE, sessionId);
					req.response.headers().put("Set-Cookie", ce.encode());
				}
				LOG.info("request: " + req.path);
				if(req.path.equals("/")) {
					req.response.sendFile("webroot/index.html");
				} else {
					req.response.sendFile("webroot" + req.path);
				}
			}
		});
			
		SockJSServer sockJS = vertx.createSockJSServer(server);
		sockJS.bridge(
				new JsonObject().putString("prefix", "/hufedb"), 
				config.getArray("sockJS_inbound"), 
				config.getArray("sockJS_outbound")
		);
		

		vertx.eventBus().registerHandler("hs.server.submit", logic.getFormSubmitHandler());		
		vertx.eventBus().registerHandler("hs.server.pxUpdate", logic.getPxUpdateHanlder());
		vertx.eventBus().registerHandler("hs.server.loadPixels", logic.getLoadPixelsHandler());
		
		vertx.setPeriodic(1000, logic.getPxUpdateCleanup());
		vertx.setPeriodic(120000, logic.getBuyerInfoTxHandler());
		vertx.setPeriodic(5000, logic.getCapturePaymentTxHandler());
		
		server.listen(8080);
		
		
		
	}

	
	
	

}
