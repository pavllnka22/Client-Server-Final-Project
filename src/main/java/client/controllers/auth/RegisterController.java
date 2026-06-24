package client.controllers.auth;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import protocol.MessagePacket;
import protocol.CryptoUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

public class RegisterController {
    @FXML private TextField registerLoginField;
    @FXML private PasswordField registerPasswordField;
    @FXML private TextField registerVisiblePasswordField;
    @FXML private CheckBox registerShowPasswordCheckBox;
    @FXML private Label registerStatusLabel;
    @FXML private Button registerButton;
    @FXML private Hyperlink backToLoginButton;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private ExecutorService loginExecutor;
    private Stage registerStage;

    @FXML
    public void initialize() {
        registerPasswordField.textProperty().addListener((obs, oldVal, newVal) -> {
            registerVisiblePasswordField.setText(newVal);
        });

        registerVisiblePasswordField.textProperty().addListener((obs, oldVal, newVal) -> {
            registerPasswordField.setText(newVal);
        });

        registerShowPasswordCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                registerVisiblePasswordField.setVisible(true);
                registerVisiblePasswordField.setManaged(true);
                registerPasswordField.setVisible(false);
                registerPasswordField.setManaged(false);
                registerVisiblePasswordField.setText(registerPasswordField.getText());
            } else {
                registerPasswordField.setVisible(true);
                registerPasswordField.setManaged(true);
                registerVisiblePasswordField.setVisible(false);
                registerVisiblePasswordField.setManaged(false);
                registerPasswordField.setText(registerVisiblePasswordField.getText());
            }
        });
    }

    public void initData(ExecutorService executor, Socket socket, ObjectOutputStream out, ObjectInputStream in, Stage stage) {
        this.loginExecutor = executor;
        this.socket = socket;
        this.out = out;
        this.in = in;
        this.registerStage = stage;
    }

    @FXML
    public void handleRegisterSubmit() {
        String login = registerLoginField.getText().trim();
        String password = getRegisterPassword();

        if (login.isEmpty() || password.isEmpty()) {
            showStatus("Fill in all fields!", true);
            return;
        }

        if (login.length() < 3) {
            showStatus("Login must be at least 3 characters!", true);
            return;
        }

        if (password.length() < 4) {
            showStatus("Password must be at least 4 characters!", true);
            return;
        }

        registerButton.setDisable(true);
        registerStatusLabel.setText("Connecting...");

        loginExecutor.submit(() -> {
            try {
                if (socket == null || socket.isClosed()) {
                    socket = new Socket("localhost", 8080);
                    socket.setSoTimeout(10000);
                    out = new ObjectOutputStream(socket.getOutputStream());
                    in = new ObjectInputStream(socket.getInputStream());
                }

                MessagePacket registerRequest = new MessagePacket(
                        MessagePacket.Type.REGISTER_REQUEST, login, "", password
                );
                CryptoUtils.sendEncrypted(out, registerRequest);

                MessagePacket response = (MessagePacket) CryptoUtils.receiveEncrypted(in);

                if (response.getType() == MessagePacket.Type.REGISTER_SUCCESS) {
                    Platform.runLater(() -> {
                        showStatus("Registration successful! Please login.", false);
                        registerButton.setDisable(false);
                        closeRegisterStage();
                    });
                } else {
                    Platform.runLater(() -> {
                        showStatus(response.getContent(), true);
                        registerButton.setDisable(false);
                    });
                }

            } catch (Exception e) {
                Platform.runLater(() -> {
                    showStatus("Connection error: " + e.getMessage(), true);
                    registerButton.setDisable(false);
                });
            }
        });
    }

    @FXML
    public void handleBackToLogin() {
        closeRegisterStage();
    }

    private void closeRegisterStage() {
        if (registerStage != null) {
            registerStage.close();
        }
    }

    private String getRegisterPassword() {
        if (registerShowPasswordCheckBox.isSelected()) {
            return registerVisiblePasswordField.getText();
        } else {
            return registerPasswordField.getText();
        }
    }

    private void showStatus(String message, boolean isError) {
        registerStatusLabel.setText(message);
        registerStatusLabel.setStyle("-fx-text-fill: " + (isError ? "#ef4444" : "#22c55e") + "; -fx-font-size: 13;");
    }
}