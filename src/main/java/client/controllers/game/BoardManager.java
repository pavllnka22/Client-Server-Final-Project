package client.controllers.game;

import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;

public class BoardManager {
    private Button[][] playerBoard;
    private Button[][] opponentBoard;
    private int[][] playerShipBoard;
    private int[][] opponentShipBoard;
    private GridPane playerBoardGrid;
    private GridPane opponentBoardGrid;

    public BoardManager(GridPane playerBoardGrid, GridPane opponentBoardGrid) {
        this.playerBoardGrid = playerBoardGrid;
        this.opponentBoardGrid = opponentBoardGrid;
        this.playerBoard = new Button[10][10];
        this.opponentBoard = new Button[10][10];
        this.playerShipBoard = new int[10][10];
        this.opponentShipBoard = new int[10][10];
    }

    public void createBoards() {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                Button playerBtn = createBoardButton();
                playerBoardGrid.add(playerBtn, j, i);
                playerBoard[i][j] = playerBtn;

                Button opponentBtn = createBoardButton();
                opponentBoardGrid.add(opponentBtn, j, i);
                opponentBoard[i][j] = opponentBtn;
            }
        }
        updateBoardDisplay();
    }

    private Button createBoardButton() {
        Button btn = new Button();
        btn.setPrefWidth(32);
        btn.setPrefHeight(32);
        btn.getStyleClass().add("board-cell");
        btn.setStyle("-fx-background-insets: 0; -fx-padding: 0;");
        return btn;
    }

    public void updateBoardDisplay() {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                Button btn = playerBoard[i][j];
                btn.getStyleClass().removeAll("ship-cell", "empty-cell", "hit-cell", "miss-cell", "sunk-cell");

                if (playerShipBoard[i][j] == 1) {
                    btn.getStyleClass().add("ship-cell");
                    btn.setText("");
                    btn.setDisable(true);
                } else {
                    btn.getStyleClass().add("empty-cell");
                    btn.setText("");
                    btn.setDisable(true);
                }
            }
        }
    }

    public void clearBoard() {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                playerShipBoard[i][j] = 0;
            }
        }
    }

    public void setShipAt(int x, int y, int value) {
        playerShipBoard[x][y] = value;
    }

    public int getShipAt(int x, int y) {
        return playerShipBoard[x][y];
    }

    public int[][] getPlayerShipBoard() {
        return playerShipBoard;
    }

    public Button[][] getPlayerBoard() {
        return playerBoard;
    }

    public Button[][] getOpponentBoard() {
        return opponentBoard;
    }

    public int countShips() {
        boolean[][] visited = new boolean[10][10];
        int shipCount = 0;

        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                if (playerShipBoard[i][j] == 1 && !visited[i][j]) {
                    shipCount++;
                    markConnectedShip(i, j, visited);
                }
            }
        }
        return shipCount;
    }

    private void markConnectedShip(int x, int y, boolean[][] visited) {
        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};

        java.util.Queue<int[]> queue = new java.util.LinkedList<>();
        queue.add(new int[]{x, y});
        visited[x][y] = true;

        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            for (int d = 0; d < 4; d++) {
                int nx = pos[0] + dx[d];
                int ny = pos[1] + dy[d];
                if (nx >= 0 && nx < 10 && ny >= 0 && ny < 10 &&
                        playerShipBoard[nx][ny] == 1 && !visited[nx][ny]) {
                    visited[nx][ny] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }
    }

    public String getShipData() {
        StringBuilder shipData = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                if (playerShipBoard[i][j] == 1) {
                    if (shipData.length() > 0) shipData.append(";");
                    shipData.append(i).append(",").append(j);
                }
            }
        }
        return shipData.toString();
    }

    public void resetOpponentBoard() {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                opponentShipBoard[i][j] = 0;
                Button btn = opponentBoard[i][j];
                btn.getStyleClass().removeAll("hit-cell", "miss-cell", "sunk-cell");
                btn.getStyleClass().add("empty-cell");
                btn.setText("");
            }
        }
    }
}