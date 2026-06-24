package server;

import protocol.MessagePacket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimpleServer {
    private static final int PORT = 8080;

    private static ExecutorService threadPool = Executors.newCachedThreadPool();

    public static List<ClientHandler> activeClients = new CopyOnWriteArrayList<>();
    public static List<GameRoom> activeRooms = new CopyOnWriteArrayList<>();
    private static ClientHandler waitingPlayer = null;

    public static void main(String[] args) {

        DatabaseManager.initialize();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected");
                ClientHandler handler = new ClientHandler(clientSocket);
                activeClients.add(handler);
                threadPool.execute(handler);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    public static synchronized void matchPlayer(ClientHandler newPlayer) {
        System.out.println("matchPlayer called for: " + newPlayer.getUsername());
        System.out.println("Waiting player: " + (waitingPlayer != null ? waitingPlayer.getUsername() : "null"));

        if (waitingPlayer == null) {

            waitingPlayer = newPlayer;
            newPlayer.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Waiting for the opponent..."));
            System.out.println(newPlayer.getUsername() + " is waiting for opponent");
        } else {
            ClientHandler player1 = waitingPlayer;
            ClientHandler player2 = newPlayer;
            System.out.println("Creating GameRoom for " + player1.getUsername() + " and " + player2.getUsername());

            GameRoom room = new GameRoom(player1, player2);
            activeRooms.add(room);

            player1.setGameRoom(room);
            player2.setGameRoom(room);

            waitingPlayer = null;
            System.out.println("GameRoom created successfully!");
        }
    }

    public static ClientHandler getClientHandlerByUsername(String username) {

        for (ClientHandler handler : activeClients) {
            if (username.equalsIgnoreCase(handler.getUsername())) {
                return handler;
            }
        }
        return null;
    }


    public static String getNetworkMonitoringStatistics() {
        int activeConnections = activeClients.size();
        int threadCount = Thread.activeCount();

        return String.format(
                "---Active TCP-connections: %d\nNum of threads: %d\nServer status: RUNNING",
                activeConnections, threadCount
        );
    }

    public static synchronized void removeClient(ClientHandler client) {
        if (activeClients != null) {
            activeClients.remove(client);
        }
    }
}