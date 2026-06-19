package client.controllers.game;

import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import protocol.MessagePacket;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ChatManager {
    private TextArea chatArea;
    private TextField chatField;
    private ObjectOutputStream out;
    private String username;
    private ChatListener listener;

    public interface ChatListener {
        void onMessageReceived(String sender, String message);
        void onSystemMessage(String message);
    }

    public ChatManager(TextArea chatArea, TextField chatField, ObjectOutputStream out, String username) {
        this.chatArea = chatArea;
        this.chatField = chatField;
        this.out = out;
        this.username = username;
    }

    public void setListener(ChatListener listener) {
        this.listener = listener;
    }

    public void sendMessage() {
        String message = chatField.getText().trim();
        if (message.isEmpty()) return;

        try {
            MessagePacket chatPacket = new MessagePacket(
                    MessagePacket.Type.CHAT, username, message
            );
            out.writeObject(chatPacket);
            out.flush();
            appendMyMessage(message);
            chatField.clear();
        } catch (IOException e) {
            appendSystemMessage("Failed to send message: " + e.getMessage());
        }
    }

    public void handleIncomingMessage(MessagePacket packet) {
        if (packet.getType() == MessagePacket.Type.CHAT) {
            appendOpponentMessage(packet.getSender(), packet.getContent());
            if (listener != null) {
                listener.onMessageReceived(packet.getSender(), packet.getContent());
            }
        } else if (packet.getType() == MessagePacket.Type.SYSTEM) {
            appendSystemMessage(packet.getContent());
            if (listener != null) {
                listener.onSystemMessage(packet.getContent());
            }
        }
    }

    private void appendMyMessage(String message) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        chatArea.appendText("You [" + time + "]\n" + message + "\n\n");
    }

    private void appendOpponentMessage(String sender, String message) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        chatArea.appendText(sender + " [" + time + "]\n" + message + "\n\n");
    }

    private void appendSystemMessage(String message) {
        chatArea.appendText("⚙ " + message + "\n\n");
    }

    public void appendMessage(String sender, String message) {
        if (sender.equals(username)) {
            appendMyMessage(message);
        } else {
            appendOpponentMessage(sender, message);
        }
    }

    public void appendSystemMessageDirect(String message) {
        appendSystemMessage(message);
    }

    public void clearChat() {
        chatArea.clear();
    }
}