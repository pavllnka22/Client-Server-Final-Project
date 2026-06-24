package protocol;

import java.io.Serializable;

public class MessagePacket implements Serializable {

    public enum Type {
        CONNECT, CHAT, SHOT, SYSTEM,
        AUTH_REQUEST, AUTH_SUCCESS, AUTH_FAIL,
        REGISTER_REQUEST, REGISTER_SUCCESS, REGISTER_FAIL,
        GAME_UPDATE, GAME_OVER,
        STATS_REQUEST, STATS_RESPONSE
    }

    private Type type;
    private String sender;
    private String content;
    private String password;
    private int x;
    private int y;

    public MessagePacket(Type type, String sender, String content) {
        this.type = type;
        this.sender = sender;
        this.content = content;
    }

    public MessagePacket(Type type, String sender, String content, String password) {
        this.type = type;
        this.sender = sender;
        this.content = content;
        this.password = password;
    }

    public MessagePacket(Type type, String sender, int x, int y) {
        this.type = type;
        this.sender = sender;
        this.x = x;
        this.y = y;
    }

    public Type getType() { return type; }
    public String getSender() { return sender; }
    public String getContent() { return content; }
    public String getPassword() { return password; }
    public int getX() { return x; }
    public int getY() { return y; }
}