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
            //decompose message and do logic
            clientSocket.handler(buf -> RecordParser.newFixed(buf.getInt(0)).handler(fixedBuf -> {
                Message message = MessageHelper.decompose(fixedBuf);
                switch (message.getCommand()) {
                    case client_heartbeat:
                        clientSocket.write(MessageHelper.compose(Command.client_heartbeat_resp, null));
                        break;
                    default:
                        break;
                }
            }).handle(buf));

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
