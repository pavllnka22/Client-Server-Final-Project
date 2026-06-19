package client.controllers.game;

import javafx.application.Platform;
import protocol.MessagePacket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NetworkManager {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private NetworkListener listener;
    private ExecutorService executor;
    private volatile boolean running = false;

    public interface NetworkListener {
        void onPacketReceived(MessagePacket packet);
        void onConnectionLost();
        void onConnectionError(String error);
    }

    public NetworkManager(Socket socket, ObjectOutputStream out, ObjectInputStream in) {
        this.socket = socket;
        this.out = out;
        this.in = in;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("NetworkManager-Thread");
            return t;
        });
    }

    public void setListener(NetworkListener listener) {
        this.listener = listener;
    }

    public void startListening() {
        if (running) {
            return;
        }
        running = true;

        executor.submit(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    MessagePacket incoming = (MessagePacket) in.readObject();
                    if (incoming == null) {
                        break;
                    }
                    Platform.runLater(() -> {
                        if (listener != null) {
                            listener.onPacketReceived(incoming);
                        }
                    });
                } catch (IOException e) {
                    if (running) {
                        Platform.runLater(() -> {
                            if (listener != null) {
                                listener.onConnectionError("Connection lost: " + e.getMessage());
                                listener.onConnectionLost();
                            }
                        });
                    }
                    break;
                } catch (ClassNotFoundException e) {
                    Platform.runLater(() -> {
                        if (listener != null) {
                            listener.onConnectionError("Protocol error: " + e.getMessage());
                        }
                    });
                    break;
                }
            }
        });
    }

    public void sendPacket(MessagePacket packet) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to server");
        }
        synchronized (out) {
            out.writeObject(packet);
            out.flush();
        }
    }

    public void stopListening() {
        running = false;
        if (executor != null) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public void close() {
        stopListening();
        try {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
        }
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected() && running;
    }

    public ObjectOutputStream getOutputStream() {
        return out;
    }

    public String getRemoteAddress() {
        return socket != null ? socket.getInetAddress().getHostAddress() : "unknown";
    }

    public int getLocalPort() {
        return socket != null ? socket.getLocalPort() : -1;
    }

    public int getRemotePort() {
        return socket != null ? socket.getPort() : -1;
    }
}