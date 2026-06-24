package protocol;

import java.io.Serializable;

public class AdminActions implements Serializable {

    public enum Command {
        KICK, BAN, GET_MONITORING, UNBAN
    }

    private Command command;
    private String targetUsername;

    public AdminActions(Command command, String targetUsername) {
        this.command = command;
        this.targetUsername = targetUsername;
    }

    public Command getCommand() { return command; }
    public String getTargetUsername() { return targetUsername; }
}