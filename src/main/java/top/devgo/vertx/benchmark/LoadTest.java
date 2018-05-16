package top.devgo.vertx.benchmark;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import top.devgo.vertx.message.Command;
import top.devgo.vertx.message.Message;
import top.devgo.vertx.message.MessageHelper;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;


public class LoadTest {

    //172.16.6.125,172.16.6.125 1024 10
    public static void main(String[] args) {
        if (args.length > 0) {
            String[] ips = args[0].indexOf(",") > 0 ? args[0].split(",") : new String[]{args[0]};
            int clients = Integer.parseInt(args[1]);
            int talksPerClient = Integer.parseInt(args[2]);
            Arrays.asList(ips).forEach(ip -> new Client(ip, clients, talksPerClient, 1000).run());
        }
    }


    static class Client {
        String host;
        int port = 7777;
//        int qos = 0;
        int qos = 1;

        int clients;
        int talksPerClient;
        long talkInterval;
        List<String> uncheckedMsg = new ArrayList<>();

        public Client(String ip, int clients, int talksPerClient, int talkInterval) {
            host = ip;
            this.clients = clients;
            this.talksPerClient = talksPerClient;
            this.talkInterval = talkInterval;
        }


        void run() {
            Vertx vertx = Vertx.vertx();
            EventBus eventBus = vertx.eventBus();

            Future[] connects = new Future[clients];
            NetClientOptions netClientOptions = new NetClientOptions().setConnectTimeout(5*1000).setReconnectAttempts(3).setReconnectInterval(10*1000L);
            long start = System.currentTimeMillis();
            for (int i = 0; i < clients; i++) {
                Future<NetSocket> connect = Future.future();
                vertx.createNetClient(netClientOptions).connect(port, host, connect.completer());
                connects[i] = connect;
            }

            AtomicReference<Long> talkStart = new AtomicReference<>();
            List<String> finishSockets = new ArrayList<>(clients);
            eventBus.consumer("fin", msg -> {
                String socketId = (String) msg.body();
                finishSockets.add(socketId);
                if (finishSockets.size() == clients){
                    System.out.println(String.format("[%s]talk send in %d ms. (ground truth %d ms)", Thread.currentThread().getName(), System.currentTimeMillis()-talkStart.get(), talksPerClient*talkInterval));
                    vertx.setTimer(10*1000L, timerId -> {
                        vertx.close();
                        System.out.println(String.format("[%s]unchecked msg: %d / %d", Thread.currentThread().getName(), uncheckedMsg.stream().distinct().count(), clients*(talksPerClient+2)));
                    });
                }
            });

            CompositeFuture.all(Arrays.asList(connects)).setHandler(all -> {
                if (all.succeeded()) {
                    System.out.println(String.format("[%s]all %s clients established in %d ms.", Thread.currentThread().getName(), all.result().size(), (System.currentTimeMillis()-start)));
                    talkStart.set(System.currentTimeMillis());

                    List<NetSocket> netSockets = all.result().list();

                    for (int i = 0; i < netSockets.size(); i++) {
                        NetSocket netSocket = netSockets.get(i);
                        int clientIndex = i;
                        CountDownLatch cdl = new CountDownLatch(talksPerClient);

                        RecordParser recordParser = RecordParser.newDelimited(MessageHelper.delimiter, netSocket);
                        netSocket.exceptionHandler(Throwable::printStackTrace).handler(recordParser.handler(fixedBuf -> {
                            Message message = MessageHelper.decompose(fixedBuf);
                            switch (message.getCommand()) {
                                case heartbeat_resp:
                                    System.out.println("heartbeat response received");
                                    break;
                                case downstream:
                                    Map<String, Object> m = (Map<String, Object>) message.getBody();
                                    switch ((String) m.get("type")) {
                                        case "talk": {
                                            netSocket.write(MessageHelper.buildConfirmMsg(Command.upstream, String.valueOf(m.get("id")), qos));
                                            System.out.println("[talk]" + m);
                                        } break;
                                        case "group_talk": {
                                            netSocket.write(MessageHelper.buildConfirmMsg(Command.upstream, String.valueOf(m.get("id")), qos));
//                                            System.out.println("[group_talk]" + m);
                                        } break;
                                        case "msg_confirm": {
                                            uncheckedMsg.remove(m.get("msgId"));
                                        } break;
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
                                    put("id", genId());
                                    put("type", "join_group");
                                    put("qos", qos);
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
                                        put("id", genId());
                                        put("type", "group_talk");
                                        put("qos", qos);
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
                                            put("id", genId());
                                            put("type", "leave_group");
                                            put("qos", qos);
                                            put("groupId", "test-group-1");
                                            put("fromId", "client-" + clientIndex);
                                            put("ts", "");
                                        }}));
                                eventBus.send("fin", netSocket.writeHandlerID());
                            }
                        });
                    }

                }else {
                    System.out.println(String.format("[%s]connection establish failed: ", Thread.currentThread().getName(), all.cause().getMessage()));
                    all.cause().printStackTrace();
                    vertx.close();
                }
            });

        }

        String genId() {
            String uuid =  UUID.randomUUID().toString();
            uncheckedMsg.add(uuid);
            return uuid;
        }

    }



}
