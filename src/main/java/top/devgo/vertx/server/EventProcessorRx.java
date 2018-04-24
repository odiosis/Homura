package top.devgo.vertx.server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;
import io.vertx.reactivex.ObservableHelper;
import top.devgo.vertx.message.Command;
import top.devgo.vertx.message.MessageHelper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class EventProcessorRx extends AbstractVerticle {

    private Logger logger = LoggerFactory.getLogger(EventProcessorRx.class);

    @Override
    public void start() {
        EventBus eventBus = vertx.eventBus();
        SharedData sharedData = vertx.sharedData();
        LocalMap<String, String> socketGroupMap = sharedData.getLocalMap("socket_group_map");// socketId - groupId [scope: this vertx app]
        LocalMap<String, String> socketUserMap = sharedData.getLocalMap("socket_user_map");// socketId - groupId [scope: this vertx app]

        ObservableHelper.toObservable(eventBus.consumer("group_talk").bodyStream())
                .map(msg -> Json.decodeValue((String) msg, Map.class))
                .groupBy(m -> m.get("groupId").toString())
                .subscribe(group -> {
                    String groupId = group.getKey();
                    group
                            .buffer(1, TimeUnit.SECONDS, 3000)
                            .filter(list -> list.size() > 0)
                            .subscribe(list -> {
                                logger.info(String.format("group [%s] batch size: %s", groupId, list.size()));
                                List<String> socketsInGroup = socketGroupMap.entrySet().stream()
                                        .filter(entry -> groupId.equals(entry.getValue()) && socketUserMap.keySet().contains(entry.getKey()))
                                        .map(Map.Entry::getKey)
                                        .collect(Collectors.toList());
                                list.forEach(m -> {
                                    String userId = (String) m.get("fromId");
                                    socketsInGroup.stream().filter(socketId -> !userId.equals(socketUserMap.get(socketId))).forEach(socketId -> {
                                        eventBus.send(socketId, MessageHelper.compose(Command.downstream, m));
                                    });
                                });
                            });
                });
    }
}
