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
    private volatile boolean running = true;

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
        adminStatusLabel.setText("Refreshing...");
    }

    @FXML
    public void handleKick() {
        String target = targetUserField.getText().trim();
        if (target.isEmpty()) {
            adminStatusLabel.setText("Enter username to kick!");
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Kick " + target + "?", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                sendAdminAction(AdminActions.Command.KICK, target);
                adminStatusLabel.setText("Kick request sent for " + target);
            }
        });
    }

    @FXML
    public void handleBan() {
        String target = targetUserField.getText().trim();
        if (target.isEmpty()) {
            adminStatusLabel.setText("Enter username to ban!");
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Ban " + target + "?", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                sendAdminAction(AdminActions.Command.BAN, target);
                adminStatusLabel.setText("Ban request sent for " + target);
            }
        });
    }

    @FXML
    public void handleUnban() {
        String target = targetUserField.getText().trim();
        if (target.isEmpty()) {
            adminStatusLabel.setText("Enter username to unban!");
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Unban " + target + "?", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                sendAdminAction(AdminActions.Command.UNBAN, target);
                adminStatusLabel.setText("Unban request sent for " + target);
            }
        });
    }

    private void sendAdminAction(AdminActions.Command command, String target) {
        executor.submit(() -> {
            try {
                if (out == null) return;
                AdminActions adminActions = new AdminActions(command, target);
                out.writeObject(adminActions);
                out.flush();
            } catch (IOException e) {
                Platform.runLater(() -> adminStatusLabel.setText("Network error: " + e.getMessage()));
            }
        });
    }

    private void startAdminReader() {
        new Thread(() -> {
            try {
                while (running) {
                    Object obj = in.readObject();
                    if (obj == null) break;
                    if (obj instanceof MessagePacket mp) {
                        String content = mp.getContent();
                        Platform.runLater(() -> {
                            if (content != null && content.startsWith("---Active TCP-connections")) {
                                monitoringTextArea.setText(content);
                                adminStatusLabel.setText("Monitoring updated");
                            } else {
                                adminStatusLabel.setText(content);
                            }
                        });
                    }
                }
            } catch (IOException e) {
                if (running) {
                    Platform.runLater(() -> adminStatusLabel.setText("Connection lost!"));
                }
            } catch (ClassNotFoundException e) {
                Platform.runLater(() -> adminStatusLabel.setText("Protocol error!"));
            }
        }, "AdminReader-Thread").start();
    }

    public void shutdown() {
        running = false;
    }
}