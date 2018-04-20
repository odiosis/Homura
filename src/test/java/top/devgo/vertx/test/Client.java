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
        int clients = 500;
        CountDownLatch cdl = new CountDownLatch(clients);
        for (int i = 0; i < clients; i++) {
            vertx.createNetClient(new NetClientOptions().setConnectTimeout(1000).setReconnectAttempts(3).setReconnectInterval(2000))
                    .connect(7777, "127.0.0.1", result -> {
                        if (result.succeeded()) {
                            long clientIndex = clients - cdl.getCount();
                            cdl.countDown();
                            NetSocket netSocket = result.result();
                            netSocket.exceptionHandler(Throwable::printStackTrace);

                            RecordParser recordParser = RecordParser.newDelimited(MessageHelper.delimiter, netSocket);
                            netSocket.handler(recordParser.handler(buffer -> {
                                Message message = MessageHelper.decompose(buffer);
                                switch (message.getCommand()) {
                                    case heartbeat_resp:
                                        System.out.println("heartbeat response received");
                                        break;
                                    default:
//                                        System.out.println(message);
                                        break;
                                }
                            })::handle);

                            //heartbeat
//                            vertx.setPeriodic(5*1000, timerId -> netSocket.write(MessageHelper.compose(Command.heartbeat, null)));

                            //join group
                            netSocket.write(MessageHelper.compose(
                                    Command.upstream,
                                    new HashMap<String, Object>() {{
                                        put("id", "");
                                        put("type", "join_group");
                                        put("groupId", "test-group-1");
                                        put("fromId", "client-"+clientIndex);
                                        put("ts", "");
                                    }}));

                            //group talk
                            vertx.setPeriodic(5 * 1000, timeId ->
                                netSocket.write(MessageHelper.compose(
                                        Command.upstream,
                                        new HashMap<String, Object>() {{
                                            put("id", "");
                                            put("type", "group_talk");
                                            put("groupId", "test-group-1");
                                            put("fromId", "client-"+clientIndex);
                                            put("msg", "hello world!");
                                            put("ts", "");
                                        }}))
                            );

                            //leave group with 3s delay
//                            vertx.setTimer(3 * 1000, timerId -> {
//                                netSocket.write(MessageHelper.compose(
//                                        Command.upstream,
//                                        new HashMap<String, Object>() {{
//                                            put("id", "");
//                                            put("type", "leave_group");
//                                            put("groupId", "test-group-1");
//                                            put("fromId", "client-" + clientIndex);
//                                            put("ts", "");
//                                        }}));
//                                vertx.close();
//                            });

                        } else {
                            System.out.println("Failed to connect: " + result.cause().getMessage());
                        }
                    });
        }
    }
}
