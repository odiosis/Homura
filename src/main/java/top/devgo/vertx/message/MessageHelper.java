package top.devgo.vertx.message;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;

public class MessageHelper {
    private static final short headerLength = 16;
    private static final short version = 1;


    public static Buffer compose(Command command, Object content) {
        return compose(command, content, 1);
    }

    public static Buffer compose(Command command, Object content, int seq) {
        Buffer header = Buffer.buffer().appendShort(headerLength) //包头长度
                .appendShort(version) //协议版本
                .appendInt(command.getCode()) //command
                .appendInt(seq); //seq 客户端去重用到
        if (content != null){
            Buffer body = Buffer.buffer(Json.encode(content));
            return Buffer.buffer().appendInt(4 + header.length() + body.length()) //包长度
                    .appendBuffer(header) //包头
                    .appendBuffer(body); //包体
        }else {
            return Buffer.buffer().appendInt(4 + header.length()) //包长度
                    .appendBuffer(header); //包头
        }
    }

    public static Message decompose(Buffer buffer){
        int packageLength = buffer.getInt(0);
        int command = buffer.getInt(4+2+2);
        if (command > Command.client_heartbeat_resp.getCode()){
            Object body = Json.decodeValue(buffer.getBuffer(headerLength, packageLength), Object.class);
            return new Message(Command.of(command), body);
        }else {
            return new Message(Command.of(command), null);
        }
    }
}
