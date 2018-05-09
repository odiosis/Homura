package top.devgo.vertx.test;

import io.vertx.core.buffer.Buffer;
import org.junit.Assert;
import org.junit.Test;
import top.devgo.vertx.message.Command;
import top.devgo.vertx.message.Message;
import top.devgo.vertx.message.MessageHelper;

public class MessageHelperTest {

    @Test
    public void testComposeAndDecompose() {

        Buffer buffer = MessageHelper.compose(Command.heartbeat, null);
        Assert.assertEquals("buffer length", MessageHelper.headerLength + MessageHelper.delimiter.length(), buffer.length());
        System.out.println("buffer length: " + buffer.length());

        Message message = MessageHelper.decompose(buffer);
        Assert.assertEquals("msg cmd", Command.heartbeat, message.getCommand());
        System.out.println(message);
    }
}
