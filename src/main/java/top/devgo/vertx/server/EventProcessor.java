package top.devgo.vertx.server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;
import top.devgo.vertx.message.Command;
import top.devgo.vertx.message.MessageHelper;

import java.util.Map;

public class EventProcessor extends AbstractVerticle {

    private Logger logger = LoggerFactory.getLogger(EventProcessor.class);

    @Override
    public void start() {
        EventBus eventBus = vertx.eventBus();
        SharedData sharedData = vertx.sharedData();
        LocalMap<String, String> socketGroupMap = sharedData.getLocalMap("socket_group_map");// socketId - groupId [scope: this vertx app]
        LocalMap<String, String> socketUserMap = sharedData.getLocalMap("socket_user_map");// socketId - groupId [scope: this vertx app]

        eventBus.consumer("group_talk", message -> {
            Map<String, Object> m = Json.decodeValue((String) message.body(),Map.class) ;
            String groupId = (String) m.get("groupId");
            String userId = (String) m.get("fromId");

            socketGroupMap.entrySet().stream()
                    .filter(entry -> groupId.equals(entry.getValue()) &&
                            socketUserMap.keySet().contains(entry.getKey()) &&
                            !userId.equals(socketUserMap.get(entry.getKey()))
                    )
                    .forEach(entry -> {
                        eventBus.send(entry.getKey(), MessageHelper.compose(Command.downstream, m));
                        logger.debug(String.format("[%s] to [%s]: %s", userId, socketUserMap.get(entry.getKey()), m.get("msg")));
                    });
        });
    }
}
