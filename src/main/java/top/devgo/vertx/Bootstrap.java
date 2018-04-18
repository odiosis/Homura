package top.devgo.vertx;

import io.vertx.core.Vertx;

public class Bootstrap {

    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(new Server());
    }
}
