package server;

import protocol.AdminActions;
import protocol.MessagePacket;

import java.io.*;
import java.net.Socket;

import static protocol.AdminActions.Command.*;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private String username;
    private String role;
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
                Object obj = in.readObject();
                if (obj == null) return;

                if (obj instanceof MessagePacket authPacket) {
                    if (authPacket.getType() == MessagePacket.Type.AUTH_REQUEST) {
                        String login = authPacket.getSender();
                        String password = authPacket.getPassword();

                        String role = DatabaseManager.authenticateUser(login, password);

                        if ("BANNED".equals(role)) {
                            sendPacket(new MessagePacket(MessagePacket.Type.AUTH_FAIL, "SERVER",
                                    "Your account has been banned by administrator!"));
                            continue;
                        }

                        if (role != null) {
                            this.username = login;
                            this.role = role;
                            isAuthenticated = true;

                            sendPacket(new MessagePacket(MessagePacket.Type.AUTH_SUCCESS, "SERVER",
                                    "Authorisation successful! Role: " + role));

                            if ("USER".equals(role)) {
                                System.out.println("[GAME] User " + login + " added to the wait list.");
                                SimpleServer.matchPlayer(this);
                            } else {
                                System.out.println("[ADMIN] " + login + " is now monitoring.");
                            }
                        } else {
                            System.out.println("Failed login attempt: " + login);
                            sendPacket(new MessagePacket(MessagePacket.Type.AUTH_FAIL, "SERVER",
                                    "Invalid username or password!"));
                        }
                    } else if (authPacket.getType() == MessagePacket.Type.REGISTER_REQUEST) {
                        String login = authPacket.getSender();
                        String password = authPacket.getPassword();
                        boolean success = DatabaseManager.registerUser(login, password);
                        sendPacket(new MessagePacket(
                                success ? MessagePacket.Type.REGISTER_SUCCESS : MessagePacket.Type.REGISTER_FAIL,
                                "SERVER",
                                success ? "Registration successful!" : "Username already exists!"
                        ));
                    }
                } else if (obj instanceof AdminActions adminActions) {
                    if ("ADMIN".equals(this.role)) {
                        handleAdminAction(adminActions);
                    } else {
                        sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER",
                                "Not allowed to perform this action!"));
                    }
                }
            }

            while (true) {
                Object obj = in.readObject();
                if (obj == null) break;

                if (obj instanceof MessagePacket packet) {
                    switch (packet.getType()) {
                        case CHAT -> {
                            if (gameRoom != null && gameRoom.containsPlayer(this)) {
                                ClientHandler opponent = getOpponent();
                                if (opponent != null) opponent.sendPacket(packet);
                            } else {
                                sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER",
                                        "You are not in a game!"));
                            }
                        }
                        case SHOT -> {
                            if (gameRoom != null) {
                                gameRoom.handleShot(this, packet.getX(), packet.getY());
                            } else {
                                sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER",
                                        "You are not in a game yet!"));
                            }
                        }
                        case SYSTEM -> {
                            String content = packet.getContent();
                            if (content != null && content.startsWith("SHIPS:")) {
                                if (gameRoom != null) {
                                    gameRoom.setPlayerShips(this, content);
                                } else {
                                    sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER",
                                            "Error: No game room!"));
                                }
                            }
                        }
                        case STATS_REQUEST -> {
                            String stats = DatabaseManager.getPlayerStats(username);
                            sendPacket(new MessagePacket(MessagePacket.Type.STATS_RESPONSE, "SERVER",
                                    stats != null ? stats : "No stats available"));
                        }
                        default -> {
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

    private void handleAdminAction(AdminActions packet) {
        String target = packet.getTargetUsername();
        ClientHandler targetHandler = SimpleServer.getClientHandlerByUsername(target);

        switch (packet.getCommand()) {
            case KICK -> {
                if (targetHandler != null) {
                    targetHandler.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER",
                            "You were kicked by admin!"));
                    targetHandler.closeConnection();
                    System.out.println("[ADMIN] User " + target + " was kicked.");
                }
            }
            case BAN -> {
                DatabaseManager.banUserInDB(target);
                System.out.println("[ADMIN] User " + target + " was banned.");

                if (targetHandler != null) {
                    targetHandler.sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER",
                            "You were banned by admin!"));
                    if (targetHandler.getGameRoom() != null) {
                        targetHandler.getGameRoom().forceWinDueToDisconnection(targetHandler);
                    }
                    targetHandler.closeConnection();
                }
            }
            case GET_MONITORING -> {
                String stats = SimpleServer.getNetworkMonitoringStatistics();
                sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", stats));
            }
            case UNBAN -> {
                String userToUnban = packet.getTargetUsername();
                boolean isUnbanned = DatabaseManager.unbanUserInDB(userToUnban);

                if (isUnbanned) {
                    sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "User " + userToUnban + " unbanned!"));
                } else {
                    sendPacket(new MessagePacket(MessagePacket.Type.SYSTEM, "SERVER", "User " + userToUnban + " not found!"));
                }
            }
        }
    }

    private ClientHandler getOpponent() {
        if (gameRoom == null) return null;
        try {
            var field1 = GameRoom.class.getDeclaredField("player1");
            var field2 = GameRoom.class.getDeclaredField("player2");
            field1.setAccessible(true);
            field2.setAccessible(true);
            ClientHandler p1 = (ClientHandler) field1.get(gameRoom);
            ClientHandler p2 = (ClientHandler) field2.get(gameRoom);
            return (p1 == this) ? p2 : (p2 == this) ? p1 : null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void closeConnection() {
        try {
            SimpleServer.removeClient(this);
            if (gameRoom != null) {
                gameRoom.forceWinDueToDisconnection(this);
            }
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
        } catch (IOException _) {
        }
    }

    public void sendPacket(MessagePacket packet) {
        try {
            out.writeObject(packet);
            out.flush();
        } catch (IOException e) {
            System.out.println("Failed to send packet to " + username + ": " + e.getMessage());
        }
    }

    public void setGameRoom(GameRoom room) {
        this.gameRoom = room;
        System.out.println("GameRoom set for " + username);
    }

    public String getUsername() {
        return username;
    }

    public GameRoom getGameRoom() {
        return gameRoom;
    }
}