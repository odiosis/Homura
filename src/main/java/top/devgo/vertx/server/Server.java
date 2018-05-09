package top.devgo.vertx.server;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.MultiMap;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;
import io.vertx.core.streams.Pump;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.devgo.vertx.message.Command;
import top.devgo.vertx.message.Message;
import top.devgo.vertx.message.MessageHelper;

import java.util.HashMap;
import java.util.Map;

public class Server extends AbstractVerticle {
    private Logger logger = LoggerFactory.getLogger(Server.class);


    private int port = 7777;
    private final String SD_ONLINE_NUMBERS = "online_numbers";

    @Override
    public void start() {
        HazelcastInstance hazelcast = Hazelcast.getHazelcastInstanceByName("Homura");

        MultiMap<String, String> group = hazelcast.getMultiMap("group");// groupId -> [userId..]
        Map<String, Map> msgBufferMap = hazelcast.getMap("msg_buffer");//msgId - msg

        EventBus eventBus = vertx.eventBus();
        SharedData sharedData = vertx.sharedData();
        LocalMap<String, String> socketGroupMap = sharedData.getLocalMap("socket_group_map");// socketId - groupId [scope: this vertx app]
        LocalMap<String, String> socketUserMap = sharedData.getLocalMap("socket_user_map");// socketId - userId [scope: this vertx app]


        NetServer server = vertx.createNetServer(new NetServerOptions().setReusePort(true));//reuse port warning, see https://github.com/eclipse/vert.x/issues/2193
        server.connectHandler(clientSocket -> {
            //new client in
            sharedData.getCounter(SD_ONLINE_NUMBERS, result -> {
                if (result.succeeded()){
                    result.result().incrementAndGet(num -> logger.info(String.format("online_number: %s", num.result())));
                }
            });


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
                        int qos = (int) m.get("qos");
                        switch ((String) m.get("type")) {
                            case "join_group": {
                                String groupId = (String) m.get("groupId");
                                String userId = (String) m.get("fromId");
                                socketUserMap.put(clientSocket.writeHandlerID(), userId);
                                socketGroupMap.put(clientSocket.writeHandlerID(), groupId);
                                group.put(groupId, userId);
                                if (qos == 1) clientSocket.write(MessageHelper.compose(Command.downstream, new HashMap(){{put("type", "msg_conform"); put("msgId",  m.get("id"));}}));
                                logger.info(String.format("[%s] join [%s]", userId, groupId));
                            } break;
                            case "leave_group": {
                                String groupId = (String) m.get("groupId");
                                String userId = (String) m.get("fromId");
                                socketGroupMap.remove(clientSocket.writeHandlerID(), groupId);
                                group.remove(groupId, userId);
                                if (qos == 1) clientSocket.write(MessageHelper.compose(Command.downstream, new HashMap(){{put("type", "msg_conform"); put("msgId",  m.get("id"));}}));
                                logger.info(String.format("[%s] leave [%s]", userId, groupId));
                            } break;
                            case "talk": {
                                eventBus.publish("talk", Json.encode(m));
                                if (qos == 1) clientSocket.write(MessageHelper.compose(Command.downstream, new HashMap(){{put("type", "msg_conform"); put("msgId",  m.get("id"));}}));
                            } break;
                            case "group_talk": {
                                eventBus.publish("group_talk", Json.encode(m));
                                if (qos == 1) clientSocket.write(MessageHelper.compose(Command.downstream, new HashMap(){{put("type", "msg_conform"); put("msgId",  m.get("id"));}}));
                            } break;
                            case "msg_conform": {
                                String msgId = (String) m.get("msgId");
                                msgBufferMap.remove(msgId);
                            } break;
                        }
                        break;
                }
            }));


            //client out
            clientSocket.closeHandler(Void -> {
                sharedData.getCounter(SD_ONLINE_NUMBERS, result -> {
                    if (result.succeeded()){
                        result.result().decrementAndGet(num -> logger.info(String.format("online_number: %s", num.result())));
                    }
                });
                socketUserMap.remove(clientSocket.writeHandlerID());
                socketGroupMap.remove(clientSocket.writeHandlerID());
            });
        });
        server.listen(port);

        logger.info(String.format("server[%s] started at %s", Thread.currentThread().getName(), port));
    }
}
