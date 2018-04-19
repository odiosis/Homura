package top.devgo.vertx.server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.shareddata.SharedData;
import io.vertx.core.streams.Pump;
import top.devgo.vertx.message.Command;
import top.devgo.vertx.message.Message;
import top.devgo.vertx.message.MessageHelper;

import java.util.Map;
import java.util.Set;

public class Server extends AbstractVerticle {
    private Logger logger = LoggerFactory.getLogger(Server.class);

    private Set<NetSocket> clients = new ConcurrentHashSet<>();
    private int port = 7777;
    private final String SD_ONLINE_NUMBERS = "online_numbers";

    @Override
    public void start() {
        EventBus eventBus = vertx.eventBus();
        SharedData sharedData = vertx.sharedData();

        NetServer server =  vertx.createNetServer();
        server.connectHandler(clientSocket -> {
            //new client in
            sharedData.getCounter(SD_ONLINE_NUMBERS, result -> {
                if (result.succeeded()){
                    result.result().incrementAndGet(num -> logger.info(String.format("online_number: %s", num.result())));
                }
            });
            clients.add(clientSocket);

            Pump.pump(clientSocket, clientSocket).start();//reactive
            RecordParser recordParser = RecordParser.newDelimited(MessageHelper.delimiter, clientSocket);

            //decompose message and do logic
            clientSocket.handler(recordParser.handler(fixedBuf -> {
                Message message = MessageHelper.decompose(fixedBuf);
                switch (message.getCommand()) {
                    case heartbeat:
                        clientSocket.write(MessageHelper.compose(Command.heartbeat_resp, null));
                        break;
                    case auth:
                        //TODO auth
                        break;
                    case upstream:
                        Map<String, Object> m = (Map<String, Object>) message.getBody();
                        //type: join_group/leave_group/talk/group_talk
                        switch ((String) m.get("type")) {
                            case "join_group": {
                                String groupId = (String) m.get("groupId");
                                String userId = (String) m.get("fromId");
                                if (vertx.isClustered()) {
                                    sharedData.getClusterWideMap(groupId, res -> {
                                        if (res.succeeded()) {
                                            res.result().putIfAbsent(userId, clientSocket.writeHandlerID(), r -> logger.info(String.format("[%s] join [%s] %s", userId, groupId, r.succeeded())));
                                        }
                                    });
                                } else {
                                    sharedData.getLocalMap(groupId).putIfAbsent(userId, clientSocket.writeHandlerID());
                                    logger.info(String.format("[%s] join [%s]", userId, groupId));
                                }
                                break;
                            }
                            case "leave_group": {
                                String groupId = (String) m.get("groupId");
                                String userId = (String) m.get("fromId");
                                if (vertx.isClustered()) {
                                    sharedData.getClusterWideMap(groupId, res -> {
                                        if (res.succeeded()) {
                                            res.result().removeIfPresent(userId, clientSocket.writeHandlerID(), r -> logger.info(String.format("[%s] leave [%s] %s", userId, groupId, r.succeeded())));
                                        }
                                    });
                                } else {
                                    sharedData.getLocalMap(groupId).remove(userId, clientSocket.writeHandlerID());
                                    logger.info(String.format("[%s] leave [%s]", userId, groupId));
                                }
                                break;
                            }
                            case "talk": {
                                //TODO single talk
                                break;
                            }
                            case "group_talk": {
                                break;
                            }
                        }
                        break;
                }
            })::handle);

            //client out
            clientSocket.closeHandler(Void -> {
                sharedData.getCounter(SD_ONLINE_NUMBERS, result -> {
                    if (result.succeeded()){
                        result.result().decrementAndGet(num -> logger.info(String.format("online_number: %s", num.result())));
                    }
                });
                clients.remove(clientSocket);
            });
        });
        server.listen(port);

        logger.info(String.format("server started at %s", port));
    }
}
