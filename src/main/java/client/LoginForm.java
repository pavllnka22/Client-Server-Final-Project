package client;

import protocol.MessagePacket;
import javax.swing.*;
import java.awt.*;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class LoginForm extends JFrame {
    private JTextField loginField;
    private JPasswordField passwordField;
    private JButton loginButton;

    public LoginForm() {
        setTitle("BattleShip");
        setSize(450, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JPanel inputPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        inputPanel.add(new JLabel("Login:"));
        loginField = new JTextField();
        inputPanel.add(loginField);

        inputPanel.add(new JLabel("Password:"));
        passwordField = new JPasswordField();
        inputPanel.add(passwordField);

        add(inputPanel, BorderLayout.CENTER);

        loginButton = new JButton("Log in");
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(loginButton);
        add(buttonPanel, BorderLayout.SOUTH);

        loginButton.addActionListener(e -> handleLogin());
    }

    private void handleLogin() {
        String login = loginField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();

        if (login.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Fill in all the fields!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            Socket socket = new Socket("localhost", 8080);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            MessagePacket authRequest = new MessagePacket(MessagePacket.Type.AUTH_REQUEST, login, "", password);
            out.writeObject(authRequest);
            out.flush();

            MessagePacket response = (MessagePacket) in.readObject();

            if (response.getType() == MessagePacket.Type.AUTH_SUCCESS) {
                JOptionPane.showMessageDialog(this, "Login success!", "Success", JOptionPane.INFORMATION_MESSAGE);

                this.dispose();

                GameForm gameForm = new GameForm(socket, out, in, login);
                gameForm.setVisible(true);

            } else {
                JOptionPane.showMessageDialog(this, response.getContent(), "Помилка авторизації", JOptionPane.ERROR_MESSAGE);
                socket.close();
            }

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error connecting to the server: " + ex.getMessage(), "Network error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
