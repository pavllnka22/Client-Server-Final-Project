package client.controllers.auth;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import protocol.MessagePacket;
import client.controllers.game.GameController;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginController {
    @FXML private TextField loginField;
    @FXML private PasswordField passwordField;
    @FXML private TextField visiblePasswordField;
    @FXML private CheckBox showPasswordCheckBox;
    @FXML private Label statusLabel;
    @FXML private Button loginButton;
    @FXML private Hyperlink registerLink;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private ExecutorService loginExecutor;
    private GameController gameController;

    @FXML
    public void initialize() {
        loginExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("Login-Thread");
            return t;
        });

        passwordField.textProperty().addListener((obs, oldVal, newVal) -> {
            visiblePasswordField.setText(newVal);
        });

        visiblePasswordField.textProperty().addListener((obs, oldVal, newVal) -> {
            passwordField.setText(newVal);
        });

        showPasswordCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                visiblePasswordField.setVisible(true);
                visiblePasswordField.setManaged(true);
                passwordField.setVisible(false);
                passwordField.setManaged(false);
                visiblePasswordField.setText(passwordField.getText());
            } else {
                passwordField.setVisible(true);
                passwordField.setManaged(true);
                visiblePasswordField.setVisible(false);
                visiblePasswordField.setManaged(false);
                passwordField.setText(visiblePasswordField.getText());
            }
        });
    }

    @FXML
    public void handleLogin() {
        String login = loginField.getText().trim();
        String password = getPassword();

        if (login.isEmpty() || password.isEmpty()) {
            showStatus("Fill in all fields!", true);
            return;
        }

        loginButton.setDisable(true);
        statusLabel.setText("Connecting...");

        loginExecutor.submit(() -> {
            try {
                socket = new Socket("localhost", 8080);
                socket.setSoTimeout(10000);
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                MessagePacket authRequest = new MessagePacket(
                        MessagePacket.Type.AUTH_REQUEST, login, "", password
                );
                out.writeObject(authRequest);
                out.flush();

                MessagePacket response = (MessagePacket) in.readObject();

                if (response.getType() == MessagePacket.Type.AUTH_SUCCESS) {
                    Platform.runLater(() -> {
                        showStatus("Login successful!", false);
                        openGameForm(login);
                    });
                } else {
                    Platform.runLater(() -> {
                        showStatus(response.getContent(), true);
                        loginButton.setDisable(false);
                    });
                    closeResources();
                }

            } catch (IOException e) {
                Platform.runLater(() -> {
                    showStatus("Connection error: " + e.getMessage(), true);
                    loginButton.setDisable(false);
                });
                closeResources();
            } catch (ClassNotFoundException e) {
                Platform.runLater(() -> {
                    showStatus("Protocol error", true);
                    loginButton.setDisable(false);
                });
                closeResources();
            }
        });
    }

    @FXML
    public void handleRegister() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/register.fxml"));
            Scene scene = new Scene(loader.load(), 500, 750);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            RegisterController registerController = loader.getController();

            Stage stage = new Stage();
            stage.setTitle("BattleShip - Register");
            stage.setScene(scene);
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            stage.initOwner(loginButton.getScene().getWindow());

            registerController.initData(loginExecutor, socket, out, in, stage);

            stage.setOnCloseRequest((WindowEvent event) -> {
                stage.close();
            });

            stage.show();

        } catch (IOException e) {
            showStatus("Error opening registration: " + e.getMessage(), true);
            e.printStackTrace();
        }
    }

    private String getPassword() {
        if (showPasswordCheckBox.isSelected()) {
            return visiblePasswordField.getText();
        } else {
            return passwordField.getText();
        }
    }

    private void openGameForm(String username) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/game.fxml"));
            Scene scene = new Scene(loader.load(), 1200, 700);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            gameController = loader.getController();

            Stage stage = new Stage();
            stage.setTitle("BattleShip - " + username);
            stage.setScene(scene);

            stage.setOnCloseRequest((WindowEvent event) -> {
                if (gameController != null) {
                    gameController.shutdown();
                }
                closeResources();
                Platform.exit();
                System.exit(0);
            });

            gameController.initializeConnection(socket, out, in, username);

            stage.show();

            Stage loginStage = (Stage) loginButton.getScene().getWindow();
            loginStage.close();

        } catch (IOException e) {
            showStatus("Error loading game: " + e.getMessage(), true);
            closeResources();
        }
    }

    private void closeResources() {
        try {
            if (in != null) {
                in.close();
                in = null;
            }
            if (out != null) {
                out.close();
                out = null;
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
                socket = null;
            }
        } catch (IOException e) {
        }
    }

    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: " + (isError ? "#ef4444" : "#22c55e") + "; -fx-font-size: 13;");
    }

    public void shutdown() {
        if (loginExecutor != null) {
            loginExecutor.shutdownNow();
            try {
                if (!loginExecutor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    loginExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                loginExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        closeResources();
    }
}