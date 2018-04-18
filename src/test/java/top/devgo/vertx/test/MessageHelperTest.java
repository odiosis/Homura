package top.devgo.vertx.test;

import io.vertx.core.buffer.Buffer;
import top.devgo.vertx.message.Command;
import top.devgo.vertx.message.Message;
import top.devgo.vertx.message.MessageHelper;

public class MessageHelperTest {

    public static void main(String[] args) {

        Buffer buffer = MessageHelper.compose(Command.client_heartbeat, null);
        System.out.println("buffer length: " + buffer.length());
        Message message = MessageHelper.decompose(buffer);

        System.out.println(message);
    }
}
