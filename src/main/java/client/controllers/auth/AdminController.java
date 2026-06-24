package client.controllers.auth;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import protocol.AdminActions;
import protocol.MessagePacket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

public class AdminController {
    @FXML private TextArea monitoringTextArea;
    @FXML private TextField targetUserField;
    @FXML private Label adminStatusLabel;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private ExecutorService executor;

    public void initData(ExecutorService executor, Socket socket, ObjectOutputStream out, ObjectInputStream in) {
        this.executor = executor;
        this.socket = socket;
        this.out = out;
        this.in = in;

        startAdminReader();

        handleRefreshMonitoring();
    }

    @FXML
    public void handleRefreshMonitoring() {
        sendAdminAction(AdminActions.Command.GET_MONITORING, "");
    }

    @FXML
    public void handleKick() {
        String target = targetUserField.getText().trim();
        if (target.isEmpty()) {
            adminStatusLabel.setText("Set a username to kick!");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Kick user  " + target + "?", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                sendAdminAction(AdminActions.Command.KICK, target);
                adminStatusLabel.setText("Request to Kick " + target + " sent.");
            }
        });
    }

    @FXML
    public void handleBan() {
        String target = targetUserField.getText().trim();
        if (target.isEmpty()) {
            adminStatusLabel.setText("Set a username to ban!");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "BAN player " + target + " ?", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                sendAdminAction(AdminActions.Command.BAN, target);
                adminStatusLabel.setText("Request to ban  " + target + " sent.");
            }
        });
    }

    @FXML
    public void handleUnban() {
        String target = targetUserField.getText().trim();
        if (target.isEmpty()) {
            adminStatusLabel.setText("Set a username to unban!");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Unban " + target + "?", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                sendAdminAction(AdminActions.Command.UNBAN, target);
            }
        });
    }

    private void sendAdminAction(AdminActions.Command command, String target) {
        new Thread(() -> {
            try {
                if (out == null) {
                    return;
                }

                AdminActions adminActions = new AdminActions(command, target);
                out.writeObject(adminActions);
                out.flush();

            } catch (IOException e) {
                Platform.runLater(() -> adminStatusLabel.setText("Network error: " + e.getMessage()));
            }
        }).start();
    }

    private void startAdminReader() {
        new Thread(() -> {
            try {
                while (true) {
                    Object obj = in.readObject();
                    if (obj == null) break;

                    if (obj instanceof MessagePacket) {
                        MessagePacket mp = (MessagePacket) obj;
                        String content = mp.getContent();

                        Platform.runLater(() -> {
                            if (content.contains("Active TCP-connections") ||
                                    content.contains("threads") ||
                                    content.contains("MONITORING")) {

                                monitoringTextArea.setText(content);
                            } else {
                                adminStatusLabel.setText(content);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                Platform.runLater(() -> adminStatusLabel.setText("Connection lost."));
            }
        }).start();
    }
    }