package client.controllers.game;

import java.util.Random;

public class ShipPlacer {
    private BoardManager boardManager;

    public ShipPlacer(BoardManager boardManager) {
        this.boardManager = boardManager;
    }

    public void generateRandomBoard() {
        boardManager.clearBoard();
        int[] ships = {4, 3, 3, 2, 2, 2, 1, 1, 1, 1};
        Random random = new Random();

        for (int size : ships) {
            boolean placed = false;
            int attempts = 0;
            while (!placed && attempts < 500) {
                attempts++;
                int x = random.nextInt(10);
                int y = random.nextInt(10);
                boolean horizontal = random.nextBoolean();

                if (canPlaceShip(x, y, size, horizontal)) {
                    placeShipOnBoard(x, y, size, horizontal);
                    placed = true;
                }
            }
            if (!placed) {
                generateRandomBoard();
                return;
            }
        }
    }

    private boolean canPlaceShip(int x, int y, int size, boolean horizontal) {
        int[][] board = boardManager.getPlayerShipBoard();

        for (int i = 0; i < size; i++) {
            int cx = horizontal ? x : x + i;
            int cy = horizontal ? y + i : y;

            if (cx < 0 || cx >= 10 || cy < 0 || cy >= 10) return false;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int nx = cx + dx, ny = cy + dy;
                    if (nx >= 0 && nx < 10 && ny >= 0 && ny < 10) {
                        if (board[nx][ny] != 0) return false;
                    }
                }
            }
        }
        return true;
    }

    private void placeShipOnBoard(int x, int y, int size, boolean horizontal) {
        for (int i = 0; i < size; i++) {
            if (horizontal) {
                boardManager.setShipAt(x, y + i, 1);
            } else {
                boardManager.setShipAt(x + i, y, 1);
            }
        }
    }
}