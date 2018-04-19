package top.devgo.vertx.test;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import top.devgo.vertx.message.Command;
import top.devgo.vertx.message.Message;
import top.devgo.vertx.message.MessageHelper;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

public class Client {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        int clients = 1;
        CountDownLatch clientNum = new CountDownLatch(clients);
        for (int i = 0; i < clients; i++) {
            vertx.createNetClient(new NetClientOptions().setConnectTimeout(1000).setReconnectAttempts(3).setReconnectInterval(2000))
                    .connect(7777, "127.0.0.1", result -> {
                        if (result.succeeded()) {
                            clientNum.countDown();
                            NetSocket netSocket = result.result();
                            netSocket.handler(buf -> RecordParser.newFixed(buf.getInt(0)).handler(buffer -> {
                                Message message = MessageHelper.decompose(buffer);
                                switch (message.getCommand()) {
                                    case heartbeat_resp:
                                        System.out.println("heartbeat response received");
                                        break;
                                    default:
                                        break;
                                }
                            }).handle(buf));

                            //heartbeat
//                            vertx.setPeriodic(5*1000, timerId -> netSocket.write(MessageHelper.compose(Command.heartbeat, null)));

                            //join group
                            netSocket.write(MessageHelper.compose(
                                    Command.upstream,
                                    new HashMap<String, Object>() {{
                                        put("id", "");
                                        put("type", "join_group");
                                        put("groupId", "test-group-1");
                                        put("fromId", "client-"+(clients-clientNum.getCount()));
                                        put("ts", "");
                                    }}));

                            //leave group
                            netSocket.write(MessageHelper.compose(
                                    Command.upstream,
                                    new HashMap<String, Object>() {{
                                        put("id", "");
                                        put("type", "leave_group");
                                        put("groupId", "test-group-1");
                                        put("fromId", "client-"+(clients-clientNum.getCount()));
                                        put("ts", "");
                                    }}));
                        } else {
                            System.out.println("Failed to connect: " + result.cause().getMessage());
                        }
                    });
        }
    }
}
