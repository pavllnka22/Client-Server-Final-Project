package client.controllers.game;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import protocol.MessagePacket;

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
    private String opponentUsername;
    private boolean shipsPlaced = false;
    private boolean isMyTurn = false;
    private boolean gameOver = false;

    public void initializeConnection(Socket socket, ObjectOutputStream out, ObjectInputStream in, String username) {
        this.username = username;

        if (playerNameLabel != null) {
            playerNameLabel.setText(username);
        }
        if (opponentNameLabel != null) {
            opponentNameLabel.setText("Waiting...");
        }

        boardManager = new BoardManager(playerBoardGrid, opponentBoardGrid);
        boardManager.setBoardClickListener(this);
        boardManager.createBoards();

        shipPlacer = new ShipPlacer(boardManager);

        networkManager = new NetworkManager(socket, out, in);
        networkManager.setListener(this);
        networkManager.startListening();

        chatManager = new ChatManager(chatArea, chatField, networkManager.getOutputStream(), username);
        chatManager.setListener(this);

        updateShipCount();
        updateStatus("Place your ships", "#94a3b8");
        updateOpponentShipCount(0);
    }

    @Override
    public void onShot(int x, int y) {
        System.out.println("onShot called: (" + x + "," + y + "), isMyTurn=" + isMyTurn + ", gameOver=" + gameOver);

        if (!isMyTurn) {
            chatManager.appendSystemMessageDirect("It's not your turn!");
            return;
        }

        if (gameOver) {
            chatManager.appendSystemMessageDirect("Game is over!");
            return;
        }

        try {
            MessagePacket shotPacket = new MessagePacket(
                    MessagePacket.Type.SHOT, username, x, y
            );
            networkManager.sendPacket(shotPacket);
            System.out.println("Shot sent to server");
        } catch (IOException e) {
            chatManager.appendSystemMessageDirect("Failed to send shot: " + e.getMessage());
        }
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

    private void updateOpponentShipCount(int count) {
        if (opponentShipCount != null) {
            opponentShipCount.setText(String.valueOf(count));
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
        if (message == null) return;
        System.out.println("SYSTEM MESSAGE: " + message);

        if (message.startsWith("SHIPS_ACK:")) {
            chatManager.appendSystemMessageDirect("OK " + message.substring(10));
            return;
        }

        if (message.equals("Ships placed! Waiting for opponent...")) {
            chatManager.appendSystemMessageDirect("OK Ships placed! Waiting for opponent...");
            updateStatus("Waiting for opponent...", "#f59e0b");
            return;
        }

        if (message.startsWith("OPPONENT:")) {
            opponentUsername = message.substring(9);
            if (opponentNameLabel != null) {
                opponentNameLabel.setText(opponentUsername);
            }
            updateStatus("Waiting for opponent to place ships...", "#f59e0b");
            chatManager.appendSystemMessageDirect("Waiting for opponent to place ships...");
            isMyTurn = false;
            return;
        }

        if (message.equals("Game started! Your turn.")) {
            isMyTurn = true;
            updateStatus("Your turn!", "#22c55e");
            chatManager.appendSystemMessageDirect("Game started! Your turn!");
            System.out.println("isMyTurn set to TRUE");
            return;
        }

        if (message.equals("Game started! Wait for your opponent's shot.")) {
            isMyTurn = false;
            updateStatus("Opponent's turn...", "#f59e0b");
            chatManager.appendSystemMessageDirect("Game started! Wait for opponent...");
            System.out.println("isMyTurn set to FALSE");
            return;
        }

        if (message.equals("It's not your turn yet!")) {
            chatManager.appendSystemMessageDirect("Error: " + message);
            return;
        }

        if (message.equals("You are not in a game!")) {
            chatManager.appendSystemMessageDirect("Error: " + message);
            return;
        }

        if (message.equals("You are not in a game yet!")) {
            chatManager.appendSystemMessageDirect("Error: " + message);
            return;
        }

        if (message.equals("Wait for both players to place ships!")) {
            chatManager.appendSystemMessageDirect("Wait: " + message);
            return;
        }

        if (message.equals("Invalid coordinates!")) {
            chatManager.appendSystemMessageDirect("Error: " + message);
            return;
        }

        if (message.equals("You already shot there!")) {
            chatManager.appendSystemMessageDirect("Error: " + message);
            return;
        }

        if (message.startsWith("GAME_UPDATE:")) {
            String[] parts = message.substring(12).split(",");
            if (parts.length >= 3) {
                String result = parts[0];
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);

                if (result.equals("HIT")) {
                    boardManager.setOpponentHit(x, y);
                    chatManager.appendSystemMessageDirect("Hit at (" + x + "," + y + ")!");
                    isMyTurn = true;
                    updateStatus("Your turn!", "#22c55e");
                    System.out.println("isMyTurn set to TRUE (HIT)");
                } else if (result.equals("MISS")) {
                    boardManager.setOpponentMiss(x, y);
                    chatManager.appendSystemMessageDirect("Miss at (" + x + "," + y + ")!");
                    isMyTurn = false;
                    updateStatus("Opponent's turn...", "#f59e0b");
                    System.out.println("isMyTurn set to FALSE (MISS)");
                } else if (result.equals("SUNK")) {
                    boardManager.setOpponentSunk(x, y);
                    chatManager.appendSystemMessageDirect("Ship sunk at (" + x + "," + y + ")!");
                    isMyTurn = true;
                    updateStatus("Your turn!", "#22c55e");
                    System.out.println("isMyTurn set to TRUE (SUNK)");
                    updateOpponentShipCount(0);
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

                if (result.equals("HIT")) {
                    boardManager.setPlayerHit(x, y);
                    chatManager.appendSystemMessageDirect("Your ship was hit at (" + x + "," + y + ")!");
                    updateShipCount();
                    isMyTurn = false;
                    updateStatus("Opponent's turn...", "#f59e0b");
                    System.out.println("isMyTurn set to FALSE (ENEMY HIT)");
                } else if (result.equals("MISS")) {
                    boardManager.setPlayerMiss(x, y);
                    chatManager.appendSystemMessageDirect("Opponent missed at (" + x + "," + y + ")!");
                    isMyTurn = true;
                    updateStatus("Your turn!", "#22c55e");
                    System.out.println("isMyTurn set to TRUE (ENEMY MISS)");
                } else if (result.equals("SUNK")) {
                    boardManager.setPlayerSunk(x, y);
                    chatManager.appendSystemMessageDirect("Your ship was sunk at (" + x + "," + y + ")!");
                    updateShipCount();
                    isMyTurn = false;
                    updateStatus("Opponent's turn...", "#f59e0b");
                    System.out.println("isMyTurn set to FALSE (ENEMY SUNK)");
                }
            }
            return;
        }

        if (message.startsWith("GAME_OVER:")) {
            gameOver = true;
            String winner = message.substring(10);
            if (winner.equals(username)) {
                updateStatus("You win!", "#22c55e");
                chatManager.appendSystemMessageDirect("Congratulations! You won!");
            } else {
                updateStatus("You lose!", "#ef4444");
                chatManager.appendSystemMessageDirect("You lost! Better luck next time.");
            }
            autoPlaceButton.setDisable(true);
            return;
        }

        if (message.startsWith("Hit! Your turn again.")) {
            chatManager.appendSystemMessageDirect(message);
            isMyTurn = true;
            updateStatus("Your turn!", "#22c55e");
            System.out.println("isMyTurn set to TRUE (Hit! Your turn again.)");
            return;
        }

        if (message.startsWith("Miss! Opponent's turn.")) {
            chatManager.appendSystemMessageDirect(message);
            isMyTurn = false;
            updateStatus("Opponent's turn...", "#f59e0b");
            System.out.println("isMyTurn set to FALSE (Miss! Opponent's turn.)");
            return;
        }

        if (message.startsWith("Your ship was hit!")) {
            chatManager.appendSystemMessageDirect(message);
            updateShipCount();
            return;
        }

        if (message.startsWith("Your ship was sunk!")) {
            chatManager.appendSystemMessageDirect(message);
            updateShipCount();
            return;
        }

        if (message.startsWith("Opponent missed! Your turn.")) {
            chatManager.appendSystemMessageDirect(message);
            isMyTurn = true;
            updateStatus("Your turn!", "#22c55e");
            System.out.println("isMyTurn set to TRUE (Opponent missed! Your turn.)");
            return;
        }

        if (message.startsWith("Ship sunk! Your turn again.")) {
            chatManager.appendSystemMessageDirect(message);
            isMyTurn = true;
            updateStatus("Your turn!", "#22c55e");
            System.out.println("isMyTurn set to TRUE (Ship sunk! Your turn again.)");
            return;
        }

        chatManager.appendSystemMessageDirect(message);
    }

    @Override
    public void onPacketReceived(MessagePacket packet) {
        if (packet == null) return;

        System.out.println("Packet received: " + packet.getType());

        if (packet.getType() == MessagePacket.Type.SYSTEM) {
            String content = packet.getContent();
            if (content != null) {
                onSystemMessage(content);
            }
        } else if (packet.getType() == MessagePacket.Type.CHAT) {
            chatManager.handleIncomingMessage(packet);
        } else if (packet.getType() == MessagePacket.Type.GAME_OVER) {
            gameOver = true;
            String winner = packet.getContent();
            if (winner != null && winner.equals(username)) {
                updateStatus("You win!", "#22c55e");
                chatManager.appendSystemMessageDirect("Congratulations! You won!");
            } else {
                updateStatus("You lose!", "#ef4444");
                chatManager.appendSystemMessageDirect("You lost! Better luck next time.");
            }
            autoPlaceButton.setDisable(true);
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
}