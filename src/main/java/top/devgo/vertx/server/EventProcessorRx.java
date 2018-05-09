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
import io.vertx.reactivex.ObservableHelper;
import top.devgo.vertx.message.Command;
import top.devgo.vertx.message.MessageHelper;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class EventProcessorRx extends AbstractVerticle {

    private Logger logger = LoggerFactory.getLogger(EventProcessorRx.class);

    @Override
    public void start() {
        HazelcastInstance hazelcast = Hazelcast.getHazelcastInstanceByName("Homura");
        Map<String, Map> msgBufferMap = hazelcast.getMap("msg_buffer");//msgId - msg

        EventBus eventBus = vertx.eventBus();
        SharedData sharedData = vertx.sharedData();
        LocalMap<String, String> socketUserMap = sharedData.getLocalMap("socket_user_map");// socketId - userId [scope: this vertx app]

        ObservableHelper.toObservable(eventBus.consumer("re_sending").bodyStream())
                .map(msg -> Json.decodeValue((String) msg, Map.class))
                .groupBy(m -> m.get("userId").toString())
                .subscribe(group -> {
                    String socketId = socketUserMap.entrySet().stream().filter(en -> group.getKey().equals(en.getValue())).map(Map.Entry::getKey).findFirst().get();
                    group
                            .buffer(1, TimeUnit.SECONDS, 1000)
                            .filter(list -> !list.isEmpty())
                            .subscribe(list -> list.forEach(m -> {
                                eventBus.send(socketId, MessageHelper.compose(Command.downstream, msgBufferMap.get(m.get("msgId"))));
                                logger.debug(String.format("re-send [%s] to %s", m.get("msgId"), group.getKey()));
                            }));
                });
    }
}
