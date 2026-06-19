package client.controllers.game;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import protocol.MessagePacket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class GameController implements ChatManager.ChatListener, NetworkManager.NetworkListener {
    @FXML private GridPane playerBoardGrid;
    @FXML private GridPane opponentBoardGrid;
    @FXML private TextArea chatArea;
    @FXML private TextField chatField;
    @FXML private Button autoPlaceButton;
    @FXML private Button sendButton;
    @FXML private Label statusLabel;
    @FXML private Label playerNameLabel;
    @FXML private Label opponentNameLabel;
    @FXML private Label playerShipCount;
    @FXML private Label opponentShipCount;

    private BoardManager boardManager;
    private ChatManager chatManager;
    private ShipPlacer shipPlacer;
    private NetworkManager networkManager;

    private String username;
    private boolean shipsPlaced = false;

    public void initializeConnection(Socket socket, ObjectOutputStream out, ObjectInputStream in, String username) {
        this.username = username;

        if (playerNameLabel != null) {
            playerNameLabel.setText(username);
        }
        if (opponentNameLabel != null) {
            opponentNameLabel.setText("Waiting...");
        }

        boardManager = new BoardManager(playerBoardGrid, opponentBoardGrid);
        boardManager.createBoards();

        shipPlacer = new ShipPlacer(boardManager);

        networkManager = new NetworkManager(socket, out, in);
        networkManager.setListener(this);
        networkManager.startListening();

        chatManager = new ChatManager(chatArea, chatField, networkManager.getOutputStream(), username);
        chatManager.setListener(this);

        updateShipCount();
        updateStatus("Place your ships", "#94a3b8");
    }

    @FXML
    private void handleAutoPlace() {
        shipPlacer.generateRandomBoard();
        shipsPlaced = true;
        autoPlaceButton.setDisable(true);
        boardManager.updateBoardDisplay();
        updateShipCount();
        updateStatus("Ships placed! ✓", "#22c55e");
        sendShipsToServer();
    }

    private void sendShipsToServer() {
        try {
            String shipData = boardManager.getShipData();
            MessagePacket shipPacket = new MessagePacket(
                    MessagePacket.Type.SYSTEM,
                    username,
                    "SHIPS:" + shipData
            );
            networkManager.sendPacket(shipPacket);
            chatManager.appendSystemMessageDirect("Ships placed successfully!");
        } catch (IOException e) {
            updateStatus("Error: " + e.getMessage(), "#ef4444");
            chatManager.appendSystemMessageDirect("Failed to send ships: " + e.getMessage());
        }
    }

    @FXML
    private void handleSendChat() {
        chatManager.sendMessage();
    }

    private void updateShipCount() {
        int count = boardManager.countShips();
        if (playerShipCount != null) {
            playerShipCount.setText(String.valueOf(count));
        }
    }

    private void updateStatus(String status, String color) {
        if (statusLabel != null) {
            statusLabel.setText(status);
            statusLabel.setStyle("-fx-text-fill: " + color + ";");
        }
    }

    public void shutdown() {
        if (networkManager != null) {
            networkManager.close();
        }
    }

    @Override
    public void onMessageReceived(String sender, String message) {
    }

    @Override
    public void onSystemMessage(String message) {
        if (message != null && message.contains("SHIPS")) {
        }
    }

    @Override
    public void onPacketReceived(MessagePacket packet) {
        chatManager.handleIncomingMessage(packet);
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
}