package top.devgo.vertx.test;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import top.devgo.vertx.message.Command;
import top.devgo.vertx.message.Message;
import top.devgo.vertx.message.MessageHelper;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;


public class Client {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        EventBus eventBus = vertx.eventBus();

//        String host = "172.16.6.125";
        String host = "127.0.0.1";

        int clients = 50;
        int talksPerClient = 60;
        long talkInterval = 100;


        Future[] connects = new Future[clients];
        NetClientOptions netClientOptions = new NetClientOptions().setConnectTimeout(2000).setReconnectAttempts(3).setReconnectInterval(2000);
        long start = System.currentTimeMillis();
        for (int i = 0; i < clients; i++) {
            Future<NetSocket> connect = Future.future();
            vertx.createNetClient(netClientOptions).connect(7777, host, connect.completer());
            connects[i] = connect;
        }


        AtomicReference<Long> talkStart = new AtomicReference<>();
        List<String> finishSockets = new Vector<>(clients);
        eventBus.consumer("fin", msg -> {
            String socketId = (String) msg.body();
            finishSockets.add(socketId);
            if (finishSockets.size() == clients){
                vertx.close();
                System.out.println(String.format("talk cost %d ms. (ground truth %d ms)", System.currentTimeMillis()-talkStart.get(), talksPerClient*talkInterval));
            }
        });


        CompositeFuture.all(Arrays.asList(connects)).setHandler(all -> {
            if (all.succeeded()) {
                System.out.println(String.format("all %s clients established in %d ms.", all.result().size(), (System.currentTimeMillis()-start)));
                talkStart.set(System.currentTimeMillis());

                List<NetSocket> netSockets = all.result().list();

                for (int i = 0; i < netSockets.size(); i++) {
                    NetSocket netSocket = netSockets.get(i);
                    int clientIndex = i;
                    CountDownLatch cdl = new CountDownLatch(talksPerClient);

                    RecordParser recordParser = RecordParser.newDelimited(MessageHelper.delimiter, netSocket);
                    netSocket.exceptionHandler(Throwable::printStackTrace).handler(recordParser.handler(buffer -> {
                                Message message = MessageHelper.decompose(buffer);
                                switch (message.getCommand()) {
                                    case heartbeat_resp:
                                        System.out.println("heartbeat response received");
                                        break;
                                    case downstream:
                                        Map<String, Object> m = (Map<String, Object>) message.getBody();
                                        switch ((String) m.get("type")) {
                                            case "talk":
//                                                System.out.println("[talk]"+ m);
                                                break;
                                            case "group_talk":
//                                                System.out.println("[group_talk]"+ m);
                                                break;
                                        }
                                        break;
                                    default:
                                        break;
                                }
                            })::handle);

                    //heartbeat
//                    vertx.setPeriodic(5*1000, timerId -> netSocket.write(MessageHelper.compose(Command.heartbeat, null)));

                    //talk
//                    netSocket.write(MessageHelper.compose(
//                            Command.upstream,
//                            new HashMap<String, Object>() {{
//                                put("id", "");
//                                put("type", "talk");
//                                put("toId", "client-1");
//                                put("fromId", "client-" + clientIndex);
//                                put("ts", "");
//                            }}));

                    //join group
                    netSocket.write(MessageHelper.compose(
                            Command.upstream,
                            new HashMap<String, Object>() {{
                                put("id", "");
                                put("type", "join_group");
                                put("groupId", "test-group-1");
                                put("fromId", "client-" + clientIndex);
                                put("ts", "");
                            }}));

                    //group talk
                    vertx.setPeriodic(talkInterval, timeId -> {
                        cdl.countDown();
                        netSocket.write(MessageHelper.compose(
                                Command.upstream,
                                new HashMap<String, Object>() {{
                                    put("id", "");
                                    put("type", "group_talk");
                                    put("groupId", "test-group-1");
                                    put("fromId", "client-" + clientIndex);
                                    put("msg", "hello world!");
                                    put("ts", "");
                                }}));

                        if (cdl.getCount() == 0){
                            vertx.cancelTimer(timeId);
                            netSocket.write(MessageHelper.compose(
                                    Command.upstream,
                                    new HashMap<String, Object>() {{
                                        put("id", "");
                                        put("type", "leave_group");
                                        put("groupId", "test-group-1");
                                        put("fromId", "client-" + clientIndex);
                                        put("ts", "");
                                    }}));
                            eventBus.send("fin", netSocket.writeHandlerID());
                        }
                    });
                }

            }else {
                System.out.println("connection establish failed: " + all.cause().getMessage());
            }
        });

    }
}
