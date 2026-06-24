package client.controllers.game;

import client.controllers.auth.ProfileController;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import protocol.MessagePacket;
import protocol.CryptoUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class GameController implements ChatManager.ChatListener, NetworkManager.NetworkListener, BoardManager.BoardClickListener {

    @FXML private GridPane playerBoardGrid;
    @FXML private GridPane opponentBoardGrid;
    @FXML private TextArea chatArea;
    @FXML private TextField chatField;
    @FXML private Button autoPlaceButton;
    @FXML private Label statusLabel;
    @FXML private Label playerNameLabel;
    @FXML private Label opponentNameLabel;
    @FXML private Label playerShipCount;
    @FXML private Label opponentShipCount;

    private BoardManager boardManager;
    private ChatManager chatManager;
    private NetworkManager networkManager;
    private String username;
    private boolean isMyTurn = false;
    private boolean gameOver = false;
    private ProfileController profileController;
    private Stage profileStage;

    public void initializeConnection(Socket socket, ObjectOutputStream out, ObjectInputStream in, String username) {
        try {
            this.username = username;
            playerNameLabel.setText(username);
            opponentNameLabel.setText("Waiting...");

            boardManager = new BoardManager(playerBoardGrid, opponentBoardGrid);
            boardManager.setBoardClickListener(this);
            boardManager.createBoards();

            networkManager = new NetworkManager(socket, out, in);
            networkManager.addListener(this);
            networkManager.startListening();

            chatManager = new ChatManager(chatArea, chatField, networkManager.getOutputStream(), username);
            chatManager.setListener(this);

            updateShipCount();
            updateStatus("Place your ships", "#94a3b8");
            updateOpponentShipCount(0);
        } catch (Exception e) {
            throw new RuntimeException("Game init failed", e);
        }
    }

    @Override
    public void onShot(int x, int y) {
        if (!isMyTurn) {
            chatManager.appendSystemMessageDirect("It's not your turn!");
            return;
        }
        if (gameOver) {
            chatManager.appendSystemMessageDirect("Game is over!");
            return;
        }

        try {
            networkManager.sendPacket(new MessagePacket(MessagePacket.Type.SHOT, username, x, y));
        } catch (Exception e) {
            chatManager.appendSystemMessageDirect("Failed to send shot: " + e.getMessage());
        }
    }

    @FXML
    private void handleAutoPlace() {
        ShipPlacer placer = new ShipPlacer(boardManager);
        placer.generateRandomBoard();
        autoPlaceButton.setDisable(true);
        boardManager.updateBoardDisplay();
        updateShipCount();
        updateStatus("Ships placed! ✓", "#22c55e");

        try {
            networkManager.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, username, "SHIPS:" + boardManager.getShipData()));
            chatManager.appendSystemMessageDirect("Ships placed successfully!");
        } catch (Exception e) {
            updateStatus("Error: " + e.getMessage(), "#ef4444");
        }
    }

    @FXML
    private void handleSendChat() {
        chatManager.sendMessage();
    }

    private void updateShipCount() {
        playerShipCount.setText(String.valueOf(boardManager.countShips()));
    }

    private void updateOpponentShipCount(int count) {
        opponentShipCount.setText(String.valueOf(count));
    }

    private void updateStatus(String status, String color) {
        statusLabel.setText(status);
        statusLabel.setStyle("-fx-text-fill: " + color + ";");
    }

    @Override
    public void onMessageReceived(String sender, String message) {
    }

    @Override
    public void onSystemMessage(String message) {
        if (message == null) return;

        if (message.startsWith("OPPONENT:")) {
            opponentNameLabel.setText(message.substring(9));
            updateStatus("Waiting for opponent to place ships...", "#f59e0b");
            chatManager.appendSystemMessageDirect("Waiting for opponent to place ships...");
            isMyTurn = false;
            return;
        }

        if (message.equals("Game started! Your turn.")) {
            isMyTurn = true;
            updateStatus("Your turn!", "#22c55e");
            chatManager.appendSystemMessageDirect("Game started! Your turn!");
            return;
        }

        if (message.equals("Game started! Wait for your opponent's shot.")) {
            isMyTurn = false;
            updateStatus("Opponent's turn...", "#f59e0b");
            chatManager.appendSystemMessageDirect("Game started! Wait for opponent...");
            return;
        }

        if (message.startsWith("GAME_UPDATE:")) {
            String[] parts = message.substring(12).split(",");
            if (parts.length >= 3) {
                String result = parts[0];
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                switch (result) {
                    case "HIT":
                        boardManager.setOpponentHit(x, y);
                        chatManager.appendSystemMessageDirect("Hit at (" + x + "," + y + ")!");
                        isMyTurn = true;
                        updateStatus("Your turn!", "#22c55e");
                        break;
                    case "MISS":
                        boardManager.setOpponentMiss(x, y);
                        chatManager.appendSystemMessageDirect("Miss at (" + x + "," + y + ")!");
                        isMyTurn = false;
                        updateStatus("Opponent's turn...", "#f59e0b");
                        break;
                    case "SUNK":
                        boardManager.setOpponentSunk(x, y);
                        chatManager.appendSystemMessageDirect("Ship sunk at (" + x + "," + y + ")!");
                        isMyTurn = true;
                        updateStatus("Your turn!", "#22c55e");
                        updateOpponentShipCount(0);
                        break;
                }
            }
            return;
        }

        if (message.startsWith("ENEMY_SHOT:")) {
            String[] parts = message.substring(11).split(",");
            if (parts.length >= 3) {
                String result = parts[0];
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                switch (result) {
                    case "HIT":
                        boardManager.setPlayerHit(x, y);
                        chatManager.appendSystemMessageDirect("Your ship was hit at (" + x + "," + y + ")!");
                        isMyTurn = false;
                        updateStatus("Opponent's turn...", "#f59e0b");
                        break;
                    case "MISS":
                        boardManager.setPlayerMiss(x, y);
                        chatManager.appendSystemMessageDirect("Opponent missed at (" + x + "," + y + ")!");
                        isMyTurn = true;
                        updateStatus("Your turn!", "#22c55e");
                        break;
                    case "SUNK":
                        boardManager.setPlayerSunk(x, y);
                        chatManager.appendSystemMessageDirect("Your ship was sunk at (" + x + "," + y + ")!");
                        isMyTurn = false;
                        updateStatus("Opponent's turn...", "#f59e0b");
                        break;
                }
                updateShipCount();
            }
            return;
        }

        if (message.startsWith("GAME_OVER:")) {
            gameOver = true;
            String winner = message.substring(10);
            boolean isWin = winner.equals(username);
            updateStatus(isWin ? "You win!" : "You lose!", isWin ? "#22c55e" : "#ef4444");
            chatManager.appendSystemMessageDirect(isWin ? "Congratulations! You won!" : "You lost! Better luck next time.");
            autoPlaceButton.setDisable(true);
            if (profileStage != null && profileStage.isShowing()) refreshProfile();
            return;
        }

        String[] simpleMessages = {
                "Hit! Your turn again.", "Miss! Opponent's turn.",
                "Your ship was hit!", "Your ship was sunk!",
                "Opponent missed! Your turn.", "Ship sunk! Your turn again."
        };
        for (String msg : simpleMessages) {
            if (message.startsWith(msg)) {
                chatManager.appendSystemMessageDirect(message);
                if (msg.contains("turn again") || msg.equals("Hit! Your turn again.") || msg.equals("Opponent missed! Your turn.")) {
                    isMyTurn = true;
                    updateStatus("Your turn!", "#22c55e");
                } else if (msg.equals("Miss! Opponent's turn.")) {
                    isMyTurn = false;
                    updateStatus("Opponent's turn...", "#f59e0b");
                }
                if (msg.equals("Your ship was hit!") || msg.equals("Your ship was sunk!")) {
                    updateShipCount();
                }
                return;
            }
        }
        chatManager.appendSystemMessageDirect(message);
    }

    @Override
    public void onPacketReceived(MessagePacket packet) {
        if (packet == null) return;
        switch (packet.getType()) {
            case SYSTEM:
                if (packet.getContent() != null) onSystemMessage(packet.getContent());
                break;
            case CHAT:
                chatManager.handleIncomingMessage(packet);
                break;
            case GAME_OVER:
                gameOver = true;
                String winner = packet.getContent();
                boolean isWin = winner != null && winner.equals(username);
                updateStatus(isWin ? "You win!" : "You lose!", isWin ? "#22c55e" : "#ef4444");
                chatManager.appendSystemMessageDirect(isWin ? "Congratulations! You won!" : "You lost! Better luck next time.");
                autoPlaceButton.setDisable(true);
                if (profileStage != null && profileStage.isShowing()) refreshProfile();
                break;
            default:
                break;
        }
    }

    @Override
    public void onConnectionLost() {
        chatManager.appendSystemMessageDirect("Connection lost");
        updateStatus("Connection lost", "#ef4444");
        autoPlaceButton.setDisable(true);
    }

    @Override
    public void onConnectionError(String error) {
        chatManager.appendSystemMessageDirect("Error: " + error);
        updateStatus("Error: " + error, "#ef4444");
    }

    @FXML
    private void handleOpenProfile() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/profile.fxml"));
            Scene scene = new Scene(loader.load(), 500, 750);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            profileController = loader.getController();
            profileController.initData(username, networkManager);

            profileStage = new Stage();
            profileStage.setTitle("Profile - " + username);
            profileStage.setScene(scene);
            profileStage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            profileStage.initOwner(chatField.getScene().getWindow());

            profileStage.setOnCloseRequest(e -> {
                if (profileController != null) profileController.cleanup();
                profileStage = null;
                profileController = null;
            });

            profileStage.show();
        } catch (IOException e) {
            chatManager.appendSystemMessageDirect("Error opening profile: " + e.getMessage());
        }
    }

    private void refreshProfile() {
        if (profileController != null) profileController.loadStats();
    }

    public void shutdown() {
        if (networkManager != null) networkManager.close();
    }
}