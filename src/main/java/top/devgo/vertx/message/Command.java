package top.devgo.vertx.message;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum Command {

    client_heartbeat(2),
    client_heartbeat_resp(3);

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
