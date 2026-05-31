package server;

import protocol.MessagePacket;

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


    public synchronized void handleShot(ClientHandler attacker, int x, int y) {

        if (attacker != currentTurn) {
            attacker.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "It's not your turn yet!"));
            return;
        }

        ClientHandler defender = (attacker == player1) ? player2 : player1;
        int[][] targetBoard = (attacker == player1) ? board2 : board1;


        if (targetBoard[x][y] == 1) {
            targetBoard[x][y] = 3;
            attacker.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "HIT  (" + x + "," + y + ")! Shoot again."));
            defender.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Your opponent HITS (" + x + "," + y + ")!"));


            if (checkWin(targetBoard)) {
                attacker.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "You won!!!"));
                defender.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "You lost"));
            }
        } else {
            targetBoard[x][y] = 2;
            attacker.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Miss (" + x + "," + y + "). Your opponent's turn."));
            defender.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Your opponent missed. Your turn!"));


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