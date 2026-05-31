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

    public static void main(String[] args) {

        DatabaseManager.initialize();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();

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
    public static List<GameRoom> activeRooms = new CopyOnWriteArrayList<>();
    private static ClientHandler waitingPlayer = null;

    public static synchronized void matchPlayer(ClientHandler newPlayer) {
        if (waitingPlayer == null) {

            waitingPlayer = newPlayer;
            newPlayer.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Waiting for the opponent..."));
        } else {

            GameRoom room = new GameRoom(waitingPlayer, newPlayer);
            activeRooms.add(room);


            waitingPlayer.setGameRoom(room);
            newPlayer.setGameRoom(room);

            waitingPlayer = null;
        }
    }
}