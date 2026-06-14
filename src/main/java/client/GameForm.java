package client;

import protocol.MessagePacket;
import javax.swing.*;
import java.awt.*;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class GameForm extends JFrame {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;

    private JButton[][] playerBoard = new JButton[10][10];
    private JButton[][] opponentBoard = new JButton[10][10];

    private JTextArea chatArea;
    private JTextField chatField;

    public GameForm(Socket socket, ObjectOutputStream out, ObjectInputStream in, String username) {
        this.socket = socket;
        this.out = out;
        this.in = in;
        this.username = username;

        setTitle("BattleShip — Player: " + username);
        setSize(900, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JPanel boardsPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        boardsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel playerBoard = createBoardPanel(this.playerBoard, "Your board (Waiting to place the ships...)", false);
        JPanel opponentBoard = createBoardPanel(this.opponentBoard, "Your opponent's board (Shoot here)", true);

        boardsPanel.add(playerBoard);
        boardsPanel.add(opponentBoard);
        add(boardsPanel, BorderLayout.CENTER);

        JPanel chatPanel = new JPanel(new BorderLayout(5, 5));
        chatPanel.setPreferredSize(new Dimension(250, 0));
        chatPanel.setBorder(BorderFactory.createTitledBorder("Game Chat"));

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        JPanel chatInputPanel = new JPanel(new BorderLayout(5, 5));
        chatField = new JTextField();
        JButton sendButton = new JButton("->");
        chatInputPanel.add(chatField, BorderLayout.CENTER);
        chatInputPanel.add(sendButton, BorderLayout.EAST);
        chatPanel.add(chatInputPanel, BorderLayout.SOUTH);

        add(chatPanel, BorderLayout.EAST);


        sendButton.addActionListener(e -> sendChatMessage());
        chatField.addActionListener(e -> sendChatMessage());

        startNetworkReader();
    }

    private JPanel createBoardPanel(JButton[][] grid, String title, boolean isEnemyBoard) {
        JPanel container = new JPanel(new BorderLayout(5, 5));
        container.add(new JLabel(title, SwingConstants.CENTER), BorderLayout.NORTH);

        JPanel gridPanel = new JPanel(new GridLayout(10, 10));
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                grid[i][j] = new JButton();
                grid[i][j].setBackground(Color.LIGHT_GRAY);

                grid[i][j].setOpaque(true);
                grid[i][j].setBorderPainted(true);

                if (isEnemyBoard) {
                    final int x = i;
                    final int y = j;
                    grid[i][j].addActionListener(e -> makeShot(x, y));
                } else {
                    grid[i][j].setEnabled(false);
                }
                gridPanel.add(grid[i][j]);
            }
        }
        container.add(gridPanel, BorderLayout.CENTER);
        return container;
    }

    private void makeShot(int x, int y) {
        try {
            MessagePacket shotPacket = new MessagePacket(MessagePacket.Type.SHOT, username, x, y);
            out.writeObject(shotPacket);
            out.flush();
        } catch (Exception e) {
            appendChat("SYSTEM", "Error while sending the shot.");
        }
    }

    private void sendChatMessage() {
        String text = chatField.getText().trim();
        if (!text.isEmpty()) {
            try {
                MessagePacket chatPacket = new MessagePacket(MessagePacket.Type.CHAT, username, text);
                out.writeObject(chatPacket);
                out.flush();

                chatArea.append("You: " + text + "\n");
                chatField.setText("");
            } catch (Exception e) {
                appendChat("SYSTEM", "Error while sending the message.");
            }
        }
    }

    private void appendChat(String sender, String message) {
        chatArea.append("[" + sender + "]: " + message + "\n");
    }

    private void startNetworkReader() {
        Thread reader = new Thread(() -> {
            try {
                while (true) {
                    MessagePacket incoming = (MessagePacket) in.readObject();
                    if (incoming == null) break;

                    SwingUtilities.invokeLater(() -> {
                        if (incoming.getType() == MessagePacket.Type.CHAT) {
                            appendChat(incoming.getSender(), incoming.getContent());
                        } else if (incoming.getType() == MessagePacket.Type.SYSTEM) {
                            appendChat("SYSTEM", incoming.getContent());
                            parseSystemMessage(incoming.getContent());
                        }
                    });
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> appendChat("SYSTEM", "Connection with server lost."));
            }
        });
        reader.setDaemon(true);
        reader.start();
    }

    private void parseSystemMessage(String msg) {

        try {
            if (msg.contains("(") && msg.contains(")")) {
                String coords = msg.substring(msg.indexOf("(") + 1, msg.indexOf(")"));
                String[] parts = coords.split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);

                if (msg.startsWith("Bad shot") || msg.startsWith("You got in") || msg.startsWith("The ship is destroyed")) {
                    if (msg.startsWith("Bad shot")) opponentBoard[x][y].setBackground(Color.DARK_GRAY);
                    else opponentBoard[x][y].setBackground(Color.RED);
                } else if (msg.contains("Opponent")) {

                    if (msg.contains("Bad shot")) {
                        playerBoard[x][y].setBackground(Color.BLUE);
                    } else {
                        playerBoard[x][y].setBackground(Color.RED);
                    }
                }
            }
        } catch (Exception e) {
        }
    }
}