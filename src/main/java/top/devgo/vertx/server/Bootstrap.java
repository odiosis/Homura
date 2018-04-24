package top.devgo.vertx.server;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class Bootstrap {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx(new VertxOptions().setPreferNativeTransport(true));
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("native transport enabled: " + vertx.isNativeTransportEnabled());
        vertx.deployVerticle(Server.class, new DeploymentOptions().setInstances(cores));
        vertx.deployVerticle(EventProcessor.class.getCanonicalName());
//        vertx.deployVerticle(EventProcessorRx.class.getCanonicalName());
    }
}
