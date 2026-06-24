package server;

import protocol.MessagePacket;
import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private String username;
    private GameRoom gameRoom;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        try {

            this.out = new ObjectOutputStream(clientSocket.getOutputStream());
            this.in = new ObjectInputStream(clientSocket.getInputStream());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            boolean isAuthenticated = false;

            while (!isAuthenticated) {
                MessagePacket authPacket = (MessagePacket) in.readObject();
                if (authPacket == null) return;

                if (authPacket.getType() == MessagePacket.Type.AUTH_REQUEST) {
                    String login = authPacket.getSender();
                    String password = authPacket.getPassword();

                    String role = DatabaseManager.authenticateUser(login, password);

                    if (role != null) {
                        this.username = login;
                        isAuthenticated = true;

                        sendPacket(new MessagePacket(MessagePacket.Type.AUTH_SUCCESS, "SERVER", "Authorisation successful! Role: " + role));


                        SimpleServer.matchPlayer(this);
                    } else {
                        System.out.println("Error while login: " + login);
                        sendPacket(new MessagePacket(MessagePacket.Type.AUTH_FAIL, "SERVER", "Invalid username or password!"));
                    }
                } else if (authPacket.getType() == MessagePacket.Type.REGISTER_REQUEST) {
                    String login = authPacket.getSender();
                    String password = authPacket.getPassword();

                    boolean success = DatabaseManager.registerUser(login, password);

                    if (success) {
                        sendPacket(new MessagePacket(MessagePacket.Type.REGISTER_SUCCESS, "SERVER", "Registration successful!"));
                    } else {
                        sendPacket(new MessagePacket(MessagePacket.Type.REGISTER_FAIL, "SERVER", "Username already exists!"));
                    }
                }
            }

            while (true) {
                MessagePacket packet = (MessagePacket) in.readObject();
                if (packet == null) break;

                System.out.println("Packet received from " + username + ": " + packet.getType());

                if (packet.getType() == MessagePacket.Type.CHAT) {
                    if (gameRoom != null && gameRoom.containsPlayer(this)) {
                        ClientHandler opponent = getOpponent();
                        if (opponent != null) {
                            opponent.sendPacket(packet);
                        }
                    } else {
                        sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "You are not in a game!"));
                    }
                } else if (packet.getType() == MessagePacket.Type.SHOT) {
                    System.out.println("SHOT from " + username + " at (" + packet.getX() + "," + packet.getY() + ")");
                    if (gameRoom != null) {
                        gameRoom.handleShot(this, packet.getX(), packet.getY());
                    } else {
                        sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "You are not in a game yet!"));
                    }
                } else if (packet.getType() == MessagePacket.Type.SYSTEM) {
                    String content = packet.getContent();
                    System.out.println("SYSTEM packet from " + username + ": " + content);

                    if (content != null && content.startsWith("SHIPS:")) {
                        System.out.println("Processing SHIPS data from " + username);
                        if (gameRoom != null) {
                            gameRoom.setPlayerShips(this, content);
                        } else {
                            System.out.println("GameRoom is null for " + username);
                            sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "Error: No game room!"));
                        }
                    }
                }
            }
        } catch (EOFException _) {
            System.out.println("Client disconnected: " + username);
        } catch (Exception e) {
            System.out.println("Error in ClientHandler: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }

    private ClientHandler getOpponent() {
        if (gameRoom == null) return null;
        try {
            java.lang.reflect.Field field1 = GameRoom.class.getDeclaredField("player1");
            java.lang.reflect.Field field2 = GameRoom.class.getDeclaredField("player2");
            field1.setAccessible(true);
            field2.setAccessible(true);
            ClientHandler p1 = (ClientHandler) field1.get(gameRoom);
            ClientHandler p2 = (ClientHandler) field2.get(gameRoom);
            if (p1 == this) return p2;
            if (p2 == this) return p1;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void closeConnection() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendPacket(MessagePacket packet) {
        try {
            out.writeObject(packet);
            out.flush();
        } catch (IOException e) {
            System.out.println("Failed to send packet: " + e.getMessage());
        }
    }

    public void setGameRoom(GameRoom room) {
        this.gameRoom = room;
        System.out.println("GameRoom set for " + username);
    }

    public String getUsername() {
        return username;
    }
}