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
                }
            }

            while (true) {
                MessagePacket packet = (MessagePacket) in.readObject();
                if (packet == null) break;

                if (packet.getType() == MessagePacket.Type.CHAT) {
                    broadcastMessage(packet);
                } else if (packet.getType() == MessagePacket.Type.SHOT) {
                    if (gameRoom != null) {
                        gameRoom.handleShot(this, packet.getX(), packet.getY());
                    } else {
                        sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "You are not in a game yet!"));
                    }
                }
            }
        } catch (EOFException _) {

        } catch (Exception e) {
            System.out.println( e.getMessage());
        } finally {
            closeConnection();
        }
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
        } catch (IOException _) {

        }
    }

    private void broadcastMessage(MessagePacket packet) {
        for (ClientHandler client : SimpleServer.activeClients) {
            if (client != this) {
                client.sendPacket(packet);
            }
        }
    }


    public void setGameRoom(GameRoom room) { this.gameRoom = room; }
    public String getUsername() { return username; }

}
