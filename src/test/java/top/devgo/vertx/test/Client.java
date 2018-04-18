package top.devgo.vertx.test;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import top.devgo.vertx.message.Command;
import top.devgo.vertx.message.Message;
import top.devgo.vertx.message.MessageHelper;

public class Client {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        int clients = 1;
        for (int i = 0; i < clients; i++) {
            vertx.createNetClient(new NetClientOptions().setConnectTimeout(1000).setReconnectAttempts(10).setReconnectInterval(500))
                    .connect(7777, "127.0.0.1", result -> {
                        if (result.succeeded()) {
                            NetSocket netSocket = result.result();
                            netSocket.handler(buf -> RecordParser.newFixed(buf.getInt(0)).handler(buffer -> {
                                Message message = MessageHelper.decompose(buffer);
                                switch (message.getCommand()) {
                                    case client_heartbeat_resp:
                                        System.out.println("heartbeat response received");
                                        break;
                                    default:
                                        break;
                                }
                            }).handle(buf));
                            vertx.setPeriodic(5*1000, timerId -> netSocket.write(MessageHelper.compose(Command.client_heartbeat, null)));
                        } else {
                            System.out.println("Failed to connect: " + result.cause().getMessage());
                        }
                    });
        }
    }
}
