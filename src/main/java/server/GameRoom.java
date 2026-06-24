package server;

import protocol.MessagePacket;
import java.util.*;

public class GameRoom {
    private ClientHandler player1;
    private ClientHandler player2;
    private int[][] board1 = new int[10][10];
    private int[][] board2 = new int[10][10];
    private ClientHandler currentTurn;
    private boolean gameOver = false;
    private boolean shipsPlaced1 = false;
    private boolean shipsPlaced2 = false;

    public GameRoom(ClientHandler p1, ClientHandler p2) {
        this.player1 = p1;
        this.player2 = p2;
        this.currentTurn = p1;

        System.out.println("=== GameRoom created ===");
        System.out.println("Player1: " + p1.getUsername());
        System.out.println("Player2: " + p2.getUsername());

        p1.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "OPPONENT:" + p2.getUsername()));
        p2.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "OPPONENT:" + p1.getUsername()));

        p1.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Waiting for opponent to place ships..."));
        p2.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Waiting for opponent to place ships..."));
    }

    public void setPlayerShips(ClientHandler player, String shipData) {
        System.out.println("setPlayerShips called for " + player.getUsername());

        int[][] board = (player == player1) ? board1 : board2;

        if (shipData.startsWith("SHIPS:")) {
            shipData = shipData.substring(6);
        }

        if (shipData.isEmpty()) {
            System.out.println("Ship data is empty!");
            return;
        }

        String[] parts = shipData.split(";");
        int shipCount = 0;
        for (String part : parts) {
            String[] coords = part.split(",");
            if (coords.length == 2) {
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                board[x][y] = 1;
                shipCount++;
            }
        }
        System.out.println("Total " + shipCount + " ships placed for " + player.getUsername());

        if (player == player1) {
            shipsPlaced1 = true;
        } else {
            shipsPlaced2 = true;
        }

        player.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Ships placed! Waiting for opponent..."));

        System.out.println("shipsPlaced1=" + shipsPlaced1 + ", shipsPlaced2=" + shipsPlaced2);

        if (shipsPlaced1 && shipsPlaced2) {
            System.out.println("=== BOTH PLAYERS PLACED SHIPS! STARTING GAME! ===");
            currentTurn = player1;
            System.out.println("First turn: " + currentTurn.getUsername());

            player1.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Game started! Your turn."));
            player2.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Game started! Wait for your opponent's shot."));
        }
    }

    public synchronized void handleShot(ClientHandler attacker, int x, int y) {
        System.out.println("handleShot from " + attacker.getUsername() + " at (" + x + "," + y + ")");

        if (gameOver) {
            attacker.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Game is already over!"));
            return;
        }

        if (attacker != currentTurn) {
            attacker.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "It's not your turn yet!"));
            return;
        }

        if (!shipsPlaced1 || !shipsPlaced2) {
            attacker.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Wait for both players to place ships!"));
            return;
        }

        ClientHandler defender = (attacker == player1) ? player2 : player1;
        int[][] targetBoard = (attacker == player1) ? board2 : board1;

        if (x < 0 || x >= 10 || y < 0 || y >= 10) {
            attacker.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Invalid coordinates!"));
            return;
        }

        if (targetBoard[x][y] == 2 || targetBoard[x][y] == 3 || targetBoard[x][y] == 4) {
            attacker.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "You already shot there!"));
            return;
        }

        if (targetBoard[x][y] == 1) {
            targetBoard[x][y] = 3;

            if (isShipSunk(targetBoard, x, y)) {
                attacker.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "GAME_UPDATE:SUNK," + x + "," + y));
                defender.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "ENEMY_SHOT:SUNK," + x + "," + y));
                attacker.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Ship sunk! Your turn again."));
                defender.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Your ship was sunk!"));

                if (checkWin(targetBoard)) {
                    gameOver = true;
                    String winner = attacker.getUsername();
                    attacker.sendPacket(new MessagePacket(MessagePacket.Type.GAME_OVER, "SERVER", winner));
                    defender.sendPacket(new MessagePacket(MessagePacket.Type.GAME_OVER, "SERVER", winner));
                    attacker.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "GAME_OVER:" + winner));
                    defender.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "GAME_OVER:" + winner));
                }
            } else {
                attacker.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "GAME_UPDATE:HIT," + x + "," + y));
                defender.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "ENEMY_SHOT:HIT," + x + "," + y));
                attacker.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Hit! Your turn again."));
                defender.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Your ship was hit!"));
            }
        } else {
            targetBoard[x][y] = 2;
            attacker.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "GAME_UPDATE:MISS," + x + "," + y));
            defender.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "ENEMY_SHOT:MISS," + x + "," + y));
            attacker.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Miss! Opponent's turn."));
            defender.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Opponent missed! Your turn."));
            currentTurn = defender;
            System.out.println("Turn switched to: " + currentTurn.getUsername());
        }
    }

    private boolean isShipSunk(int[][] board, int x, int y) {
        boolean[][] visited = new boolean[10][10];
        return checkIfShipIsSunk(board, x, y, visited);
    }

    private boolean checkIfShipIsSunk(int[][] board, int x, int y, boolean[][] visited) {
        if (x < 0 || x >= 10 || y < 0 || y >= 10 || visited[x][y]) return true;

        if (board[x][y] == 1) return false;

        if (board[x][y] != 3) return true;

        visited[x][y] = true;

        boolean up = checkIfShipIsSunk(board, x - 1, y, visited);
        boolean down = checkIfShipIsSunk(board, x + 1, y, visited);
        boolean left = checkIfShipIsSunk(board, x, y - 1, visited);
        boolean right = checkIfShipIsSunk(board, x, y + 1, visited);

        return up && down && left && right;
    }

    private boolean checkWin(int[][] board) {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                if (board[i][j] == 1) return false;
            }
        }
        return true;
    }

    public boolean containsPlayer(ClientHandler handler) {
        return player1 == handler || player2 == handler;
    }
}