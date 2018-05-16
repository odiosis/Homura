package top.devgo.vertx.server;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.MultiMap;
import io.reactivex.Observable;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;
import top.devgo.vertx.message.Command;
import top.devgo.vertx.message.MessageHelper;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class ReSendProcessor extends AbstractVerticle {

    private Logger logger = LoggerFactory.getLogger(ReSendProcessor.class);

    @Override
    public void start() {
        HazelcastInstance hazelcast = Hazelcast.getHazelcastInstanceByName("Homura");
        Map<String, Map> msgBufferMap = hazelcast.getMap("msg_buffer");//msgId - msg
        MultiMap<String, String> unconfirmedMsg = hazelcast.getMultiMap("unconfirmed_msg");//userId - msgId

        EventBus eventBus = vertx.eventBus();
        SharedData sharedData = vertx.sharedData();
        LocalMap<String, String> socketUserMap = sharedData.getLocalMap("socket_user_map");// socketId - userId [scope: this vertx app]

        resend(3*1000L, msgBufferMap, unconfirmedMsg, eventBus, socketUserMap);


    }

    private void resend(long delay, Map<String, Map> msgBufferMap, MultiMap<String, String> unconfirmedMsg, EventBus eventBus, LocalMap<String, String> socketUserMap) {
        Observable.fromIterable(unconfirmedMsg.entrySet()).groupBy(Map.Entry::getKey)
        .subscribe(group -> {
            Optional<String> socketId = socketUserMap.entrySet().stream().filter(en -> group.getKey().equals(en.getValue())).map(Map.Entry::getKey).findFirst();
            socketId.ifPresent(s -> group
                    .buffer(1000, TimeUnit.MILLISECONDS, 1000)
                    .filter(list -> !list.isEmpty())
                    .subscribe(list -> list.forEach(m -> {
                        eventBus.send(s, MessageHelper.compose(Command.downstream, msgBufferMap.get(m.getValue())));
                        logger.debug(String.format("re-send [%s] to %s", m.getValue(), group.getKey()));
                        vertx.setTimer(delay, timerId -> resend(delay, msgBufferMap, unconfirmedMsg, eventBus, socketUserMap));
                    })));
        });
    }

}
