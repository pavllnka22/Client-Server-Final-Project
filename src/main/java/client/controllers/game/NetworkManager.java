package client.controllers.game;

import javafx.application.Platform;
import protocol.CryptoUtils;
import protocol.MessagePacket;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NetworkManager {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private List<NetworkListener> listeners = new CopyOnWriteArrayList<>();
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

    public void addListener(NetworkListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(NetworkListener listener) {
        listeners.remove(listener);
    }

    public void setListener(NetworkListener listener) {
        listeners.clear();
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void startListening() {
        if (running) {
            return;
        }
        running = true;

        executor.submit(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    MessagePacket incoming = (MessagePacket) CryptoUtils.receiveEncrypted(in);
                    if (incoming == null) {
                        break;
                    }
                    Platform.runLater(() -> {
                        for (NetworkListener listener : listeners) {
                            try {
                                listener.onPacketReceived(incoming);
                            } catch (Exception e) {
                                System.err.println("Error in listener: " + e.getMessage());
                            }
                        }
                    });
                } catch (IOException e) {
                    if (running) {
                        Platform.runLater(() -> {
                            for (NetworkListener listener : listeners) {
                                listener.onConnectionError("Connection lost: " + e.getMessage());
                                listener.onConnectionLost();
                            }
                        });
                    }
                    break;
                } catch (ClassNotFoundException e) {
                    Platform.runLater(() -> {
                        for (NetworkListener listener : listeners) {
                            listener.onConnectionError("Protocol error: " + e.getMessage());
                        }
                    });
                    break;
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        for (NetworkListener listener : listeners) {
                            listener.onConnectionError("Decryption error: " + e.getMessage());
                        }
                    });
                    break;
                }
            }
        });
    }

    public void sendPacket(MessagePacket packet) throws Exception {
        if (!isConnected()) {
            throw new IOException("Not connected to server");
        }
        synchronized (out) {
            CryptoUtils.sendEncrypted(out, packet);
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
        listeners.clear();
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