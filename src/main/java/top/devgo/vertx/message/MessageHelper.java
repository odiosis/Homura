package top.devgo.vertx.message;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;

public class MessageHelper {
    public static final int FIXED_LENGTH = 4;
    private static final short headerLength = 16;
    private static final short version = 1;


    public static Buffer compose(Command command, Object content) {
        Buffer header = Buffer.buffer().appendShort(headerLength) //包头长度
                .appendShort(version) //协议版本
                .appendInt(command.getCode()) //command
                .appendInt(1); //seq 客户端去重用到
        Buffer body = Buffer.buffer(Json.encode(content));
        Buffer result = Buffer.buffer().appendInt(FIXED_LENGTH + header.length() + body.length()) //包长度
                .appendBuffer(header) //包头
                .appendBuffer(body); //包体
        return result;
    }

    public static Message decompose(Buffer buffer){
        int packageLength = buffer.getInt(0);
        int command = buffer.getInt(FIXED_LENGTH+2+2);
        Object body = Json.decodeValue(buffer.getBuffer(headerLength, packageLength), Object.class);
        return new Message(Command.of(command), body);
    }
}
