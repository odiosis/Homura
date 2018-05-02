package top.devgo.vertx.server;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;
import io.vertx.reactivex.ObservableHelper;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class EventProcessorRx extends AbstractVerticle {

    private Logger logger = LoggerFactory.getLogger(EventProcessorRx.class);

    @Override
    public void start() {
        HazelcastInstance hazelcast = Hazelcast.getHazelcastInstanceByName("Homura");
        Map<String, Buffer> msgBufferMap = hazelcast.getMap("msg_buffer");//msgId - msgBuff

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
                            .filter(list -> list.size() > 0)
                            .subscribe(list -> list.forEach(m -> eventBus.send(socketId, msgBufferMap.get(m.get("msgId")))));
                });
    }
}
