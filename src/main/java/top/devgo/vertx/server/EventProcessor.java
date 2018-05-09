package top.devgo.vertx.server;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;
import top.devgo.vertx.message.Command;
import top.devgo.vertx.message.MessageHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class EventProcessor extends AbstractVerticle {

    private Logger logger = LoggerFactory.getLogger(EventProcessor.class);

    @Override
    public void start() {
        HazelcastInstance hazelcast = Hazelcast.getHazelcastInstanceByName("Homura");
        Map<String, Map> msgBufferMap = hazelcast.getMap("msg_buffer");//msgId - msg

        EventBus eventBus = vertx.eventBus();
        SharedData sharedData = vertx.sharedData();
        LocalMap<String, String> socketGroupMap = sharedData.getLocalMap("socket_group_map");// socketId - groupId [scope: this vertx app]
        LocalMap<String, String> socketUserMap = sharedData.getLocalMap("socket_user_map");// socketId - userId [scope: this vertx app]

        eventBus.consumer("group_talk", message -> {
            Map<String, Object> m = Json.decodeValue((String) message.body(),Map.class) ;
            String groupId = (String) m.get("groupId");
            String userId = (String) m.get("fromId");
            String msgId = (String) m.get("id");
            int qos = (int) m.get("qos");

            Optional<String> sender = socketUserMap.entrySet().stream().filter(en -> userId.equals(en.getValue())).map(Map.Entry::getKey).findFirst();

            socketGroupMap.entrySet().stream()
                    .filter(entry -> groupId.equals(entry.getValue()) &&
                            socketUserMap.keySet().contains(entry.getKey()) &&
                            sender.isPresent() && !sender.get().equals(entry.getKey())
                    )
                    .forEach(entry -> {
                        String socketId = entry.getKey();
                        eventBus.send(socketId, MessageHelper.compose(Command.downstream, m));
                        if (qos == 1) {
                            msgBufferMap.put(msgId, m);
                            eventBus.publish("re_sending", Json.encode(new HashMap<String, String>(){{put("userId", socketUserMap.get(socketId));put("msgId", msgId);}}));
                        }
                        logger.debug(String.format("[%s] to [%s]: %s", userId, socketUserMap.get(socketId), m.get("msg")));
                    });
        });

        eventBus.consumer("talk", message -> {
            Map<String, Object> m = Json.decodeValue((String) message.body(),Map.class) ;
            String toId = (String) m.get("toId");
            String userId = (String) m.get("fromId");
            String msgId = (String) m.get("id");
            int qos = (int) m.get("qos");

            socketUserMap.entrySet().stream()
                    .filter(entry -> toId.equals(entry.getValue()))
                    .forEach(entry -> {
                        String socketId = entry.getKey();
                        eventBus.send(socketId, MessageHelper.compose(Command.downstream, m));
                        if (qos == 1) {
                            msgBufferMap.put(msgId, m);
                            eventBus.publish("re_sending", Json.encode(new HashMap<String, String>(){{put("userId", socketUserMap.get(socketId));put("msgId", msgId);}}));
                        }
                        logger.debug(String.format("[%s] to [%s]: %s", userId, toId, m.get("msg")));
                    });
        });
    }
}
