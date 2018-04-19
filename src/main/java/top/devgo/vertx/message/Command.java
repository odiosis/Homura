package top.devgo.vertx.message;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum Command {

    heartbeat(2),
    heartbeat_resp(3),
    upstream(4),
    downstream(5),
    auth(6),
    auth_resp(7);

    private int code;
    Command(int code) {
        this.code = code;
    }

    public static Command of(int command) {
        Optional<Command> res = Arrays.stream(Command.values()).filter(cmd -> cmd.getCode() == command).findFirst();
        if (res.isPresent())
            return res.get();
        else
            throw new RuntimeException("NO SUCH COMMAND");
    }
}
