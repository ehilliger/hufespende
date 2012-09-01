package org.hufewiesen;

import java.util.logging.Logger;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.deploy.Verticle;


public class HufeServer extends Verticle {
	public final static Logger LOG = Logger.getLogger(HufeServer.class.getName());
	
	@Override
	public void start() throws Exception {
		JsonObject config = container.getConfig();
		LOG.info("configuration: " + config);
		LOG.info("test:" + config.getString("test"));
		
		container.deployModule("vertx.mongo-persistor-v1.0", config.getObject("mongodb"));
		
		HufeLogic logic = new HufeLogic(vertx);
		
		HttpServer server = vertx.createHttpServer();
		
				
		server.requestHandler(new Handler<HttpServerRequest>() {
			@Override
			public void handle(HttpServerRequest req) {
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
		
		server.listen(8080);
		
		
		
	}

	
	
	

}
