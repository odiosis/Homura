package top.devgo.vertx.message;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;

import java.util.HashMap;
import java.util.Map;

public class MessageHelper {
    public static final String delimiter = "--==HOMURA-PKG-END==--";//length 22
    public static final short headerLength = 16;
    public static final short version = 1;


    public static Buffer compose(Command command, Object content) {
        return compose(command, content, 1);
    }

    public static Buffer compose(Command command, Object content, int seq) {
        Buffer header = Buffer.buffer().appendShort(headerLength) //包头长度
                .appendShort(version) //协议版本
                .appendInt(command.getCode()) //command
                .appendInt(seq); //seq 客户端去重用到
        Buffer delimit = Buffer.buffer(delimiter);
        if (content != null){
            Buffer body = Buffer.buffer(Json.encode(content));
            return Buffer.buffer().appendInt(4 + header.length() + body.length() + delimit.length()) //包长度
                    .appendBuffer(header) //包头
                    .appendBuffer(body) //包体
                    .appendBuffer(delimit);
        }else {
            return Buffer.buffer().appendInt(4 + header.length() + delimit.length()) //包长度
                    .appendBuffer(header) //包头
                    .appendBuffer(delimit);
        }
    }

    public static Message decompose(Buffer buffer){
        int packageLength = buffer.getInt(0);
        int command = buffer.getInt(4+2+2);
        if (command > Command.heartbeat_resp.getCode()){
            Object body = Json.decodeValue(buffer.getBuffer(headerLength, packageLength - delimiter.length()), Object.class);
            return new Message(Command.of(command), body);
        }else {
            return new Message(Command.of(command), null);
        }
    }


    public static Buffer buildConfirmMsg(Command cmd, String msgId, int qos) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "msg_confirm");
        body.put("msgId",  msgId);
        body.put("qos",  qos);
        return compose(cmd, body);
    }
}
