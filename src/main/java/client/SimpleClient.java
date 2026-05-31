package client;

import protocol.MessagePacket;
import java.io.*;
import java.net.*;
import java.util.Scanner;

public class SimpleClient {
    private static final String HOST = "localhost";
    private static final int PORT = 8080;
    private static String username;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        try {
            Socket socket = new Socket(HOST, PORT);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            boolean loggedIn = false;
            while (!loggedIn) {
                System.out.print("Enter username: ");
                String login = scanner.nextLine();
                System.out.print("Enter password: ");
                String password = scanner.nextLine();

                MessagePacket authRequest = new MessagePacket(MessagePacket.Type.AUTH_REQUEST, login, "", password);
                out.writeObject(authRequest);
                out.flush();

                MessagePacket response = (MessagePacket) in.readObject();

                if (response.getType() == MessagePacket.Type.AUTH_SUCCESS) {
                    System.out.println("Server: " + response.getContent());
                    username = login;
                    loggedIn = true;
                } else if (response.getType() == MessagePacket.Type.AUTH_FAIL) {
                    System.out.println("Error: " + response.getContent() + " Try again.\n");
                }
            }


            Thread readerThread = new Thread(() -> {
                try {
                    while (true) {
                        MessagePacket incoming = (MessagePacket) in.readObject();
                        if (incoming == null) break;

                        if (incoming.getType() == MessagePacket.Type.CHAT) {
                            System.out.println("\n[" + incoming.getSender() + "]: " + incoming.getContent());
                            System.out.print("You (>): ");
                        } else if (incoming.getType() == MessagePacket.Type.SYSTEM) {
                            System.out.println("\nSystem: " + incoming.getContent());
                            System.out.print("You (>): ");
                        }
                    }
                } catch (Exception _) {

                }
            });
            readerThread.setDaemon(true);
            readerThread.start();

            System.out.println("\nYou are in a game room. Send texts or use 'shot X Y' when it's your turn.");
            while (true) {
                System.out.print("You(>): ");
                String text = scanner.nextLine();

                if (text.equalsIgnoreCase("exit")) break;

                if (!text.trim().isEmpty()) {
                    MessagePacket packet;
                    if (text.startsWith("shot ")) {
                        String[] parts = text.split(" ");
                        int x = Integer.parseInt(parts[1]);
                        int y = Integer.parseInt(parts[2]);
                        packet = new MessagePacket(MessagePacket.Type.SHOT, username, x, y);
                    } else {
                        packet = new MessagePacket(MessagePacket.Type.CHAT, username, text);
                    }
                    out.writeObject(packet);
                    out.flush();
                }
            }

            socket.close();
            System.out.println("Bye!");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}