package top.devgo.vertx.server;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;

public class Bootstrap {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        int cores = Runtime.getRuntime().availableProcessors();
        vertx.deployVerticle(Server.class, new DeploymentOptions().setInstances(2 * cores));//set instances=event-loops
    }
}
