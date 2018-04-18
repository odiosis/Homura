package top.devgo.vertx.test;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;

public class Client {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        for (int i = 0; i < 10; i++) {
            vertx.createNetClient(new NetClientOptions().setConnectTimeout(1000).setReconnectAttempts(10).setReconnectInterval(500))
                    .connect(7777, "127.0.0.1", result -> {
                        if (result.succeeded()) {
                            System.out.println("Connected!");
                            NetSocket netSocket = result.result();
                        } else {
                            System.out.println("Failed to connect: " + result.cause().getMessage());
                        }
                    });
        }
    }
}
