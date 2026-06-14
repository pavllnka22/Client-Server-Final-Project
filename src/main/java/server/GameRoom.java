package server;

import protocol.MessagePacket;

import java.util.Random;

public class GameRoom {
    private ClientHandler player1;
    private ClientHandler player2;


    private int[][] board1 = new int[10][10];
    private int[][] board2 = new int[10][10];

    private ClientHandler currentTurn;

    public GameRoom(ClientHandler p1, ClientHandler p2) {
        this.player1 = p1;
        this.player2 = p2;
        this.currentTurn = p1;


        board1[3][3] = 1;
        board2[3][3] = 1;

        System.out.println("GameRoom created with  " + p1.getUsername() + " and " + p2.getUsername());


        p1.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Game started!Your shot."));
        p2.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Game started! Wait for your opponent's shot."));
    }

    private void generateRandomBoard(int[][] board) {
        int[] fleet = {4, 3, 3, 2, 2, 2, 1, 1, 1, 1};
        Random random = new Random();

        for (int size : fleet) {
            boolean placed = false;
            while (!placed) {
                int x = random.nextInt(10);
                int y = random.nextInt(10);
                boolean horizontal = random.nextBoolean();

                if (canPlaceShip(board, x, y, size, horizontal)) {
                    placeShip(board, x, y, size, horizontal);
                    placed = true;
                }
            }
        }
    }

    private boolean canPlaceShip(int[][] board, int x, int y, int size, boolean horizontal) {
        for (int i = 0; i < size; i++) {
            int currentX = horizontal ? x : x + i;
            int currentY = horizontal ? y + i : y;

            if (currentX < 0 || currentX >= 10 || currentY < 0 || currentY >= 10) return false;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int neighborX = currentX + dx;
                    int neighborY = currentY + dy;
                    if (neighborX >= 0 && neighborX < 10 && neighborY >= 0 && neighborY < 10) {
                        if (board[neighborX][neighborY] != 0) return false;
                    }
                }
            }
        }
        return true;
    }


    private void placeShip(int[][] board, int x, int y, int size, boolean horizontal) {
        for (int i = 0; i < size; i++) {
            if (horizontal) {
                board[x][y + i] = 1;
            } else {
                board[x + i][y] = 1;
            }
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
    public synchronized void handleShot(ClientHandler attacker, int x, int y) {

        if (attacker != currentTurn) {
            attacker.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "It's not your turn yet!"));
            return;
        }

        ClientHandler defender = (attacker == player1) ? player2 : player1;
        int[][] targetBoard = (attacker == player1) ? board2 : board1;


        if (targetBoard[x][y] == 1) {
            targetBoard[x][y] = 3;
            if (isShipSunk(targetBoard, x, y)) {

                attacker.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "The ship is destroyed. It's your turn again."));
                defender.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Oh no! Your ship is destroyed (" + x + "," + y + ")!"));

                if (checkWin(targetBoard)) {
                    attacker.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Congrats! You win!"));
                    defender.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "All your ships are destroyed. You lose."));
                }
            } else {
                attacker.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "You got in (" + x + "," + y + ")! Your turn again."));
                defender.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Your ship was shot at (" + x + "," + y + ")!"));
            }
        } else {
            targetBoard[x][y] = 2;
            attacker.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Bad shot (" + x + "," + y + "). Your opponent's turn."));
            defender.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Your opponent made a bad shot. Your turn!"));
            currentTurn = defender;
        }
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