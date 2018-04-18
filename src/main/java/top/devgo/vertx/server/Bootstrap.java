package top.devgo.vertx.server;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;

public class Bootstrap {

    public static void main(String[] args) {
        Vertx.vertx().deployVerticle("top.devgo.vertx.server.Server", new DeploymentOptions().setInstances(2));//multi event-loop
    }
}
