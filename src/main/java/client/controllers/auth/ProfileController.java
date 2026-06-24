package client.controllers.auth;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import protocol.MessagePacket;
import client.controllers.game.NetworkManager;

public class ProfileController implements NetworkManager.NetworkListener {
    @FXML private Label usernameLabel;
    @FXML private Label gamesPlayedLabel;
    @FXML private Label winsLabel;
    @FXML private Label lossesLabel;
    @FXML private Label ratingLabel;
    @FXML private Label historyLabel;
    @FXML private Button closeButton;

    private String username;
    private NetworkManager networkManager;

    public void initData(String username, NetworkManager networkManager) {
        this.username = username;
        this.networkManager = networkManager;
        networkManager.addListener(this);
        usernameLabel.setText(username);
        setDefaultStats();
        loadStats();
    }

    public void loadStats() {
        try {
            MessagePacket request = new MessagePacket(MessagePacket.Type.STATS_REQUEST, username, null);
            networkManager.sendPacket(request);
        } catch (Exception e) {
            Platform.runLater(() -> {
                setDefaultStats();
                historyLabel.setText("Error loading stats: " + e.getMessage());
            });
        }
    }

    @Override
    public void onPacketReceived(MessagePacket packet) {
        if (packet.getType() == MessagePacket.Type.STATS_RESPONSE) {
            String content = packet.getContent();
            if (content != null && !content.isEmpty()) {
                Platform.runLater(() -> updateStats(content));
            }
        }
    }

    public void updateStats(String statsData) {
        if (statsData == null || statsData.isEmpty() || statsData.equals("No stats available")) {
            setDefaultStats();
            historyLabel.setText("No stats available");
            return;
        }
        parseStats(statsData);
    }

    private void parseStats(String data) {
        String[] lines = data.split("\n");
        boolean hasStats = false;
        StringBuilder history = new StringBuilder();

        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("Games played:")) {
                gamesPlayedLabel.setText(line.substring(14).trim());
                hasStats = true;
            } else if (line.startsWith("Wins:")) {
                winsLabel.setText(line.substring(5).trim());
                hasStats = true;
            } else if (line.startsWith("Losses:")) {
                lossesLabel.setText(line.substring(7).trim());
                hasStats = true;
            } else if (line.startsWith("Rating:")) {
                ratingLabel.setText(line.substring(7).trim());
                hasStats = true;
            } else if (line.contains(" vs ")) {
                history.append(line).append("\n");
                hasStats = true;
            }
        }

        if (!hasStats) setDefaultStats();
        historyLabel.setText(history.isEmpty() ? "No games played yet." : history.toString());
    }

    private void setDefaultStats() {
        gamesPlayedLabel.setText("0");
        winsLabel.setText("0");
        lossesLabel.setText("0");
        ratingLabel.setText("1000");
        historyLabel.setText("Loading stats...");
    }

    @Override
    public void onConnectionLost() {
        Platform.runLater(() -> {
            setDefaultStats();
            historyLabel.setText("Connection lost");
        });
    }

    @Override
    public void onConnectionError(String error) {
        Platform.runLater(() -> {
            setDefaultStats();
            historyLabel.setText("Error: " + error);
        });
    }

    @FXML
    private void handleClose() {
        cleanup();
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }

    public void cleanup() {
        if (networkManager != null) networkManager.removeListener(this);
    }
}