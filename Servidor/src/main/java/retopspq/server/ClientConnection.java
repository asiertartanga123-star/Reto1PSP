package retopspq.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

final class ClientConnection implements Runnable {
    private static final int LOGIN_TIMEOUT = 10_000;
    private static final int MAX_USERNAME_LENGTH = 24;

    private final Socket socket;
    private final ClientRegistry clients;
    private final ChatMessageHandler messageHandler;
    private String username;
    private ObjectInputStream reader;
    private ObjectOutputStream writer;

    ClientConnection(Socket socket, ClientRegistry clients, ChatMessageHandler messageHandler) {
        this.socket = socket;
        this.clients = clients;
        this.messageHandler = messageHandler;
    }

    @Override
    public void run() {
        try (Socket clientSocket = socket) {
            clientSocket.setSoTimeout(LOGIN_TIMEOUT);
            writer = new ObjectOutputStream(clientSocket.getOutputStream());
            writer.flush();
            reader = new ObjectInputStream(clientSocket.getInputStream());

            String error = checkLogin(reader.readObject());
            if (error != null) {
                send(new String[] {"REJECT", error});
                return;
            }

            error = clients.register(this);
            if (error != null) {
                send(new String[] {"REJECT", error});
                return;
            }

            send(new String[] {"WELCOME", username});
            clientSocket.setSoTimeout(0);
            clients.broadcast(new String[] {"INFO", username + " se ha unido al chat."}, this);

            while (true) {
                Object message = reader.readObject();
                if (message == null) {
                    break;
                }
                if (message instanceof String[]) {
                    messageHandler.handleFrame(this, (String[]) message);
                }
            }
        } catch (IOException | ClassNotFoundException exception) {
            if (!clients.isServerClosed()) {
                System.err.println("Conexion finalizada: " + exception.getMessage());
            }
        } finally {
            if (username != null) {
                clients.unregister(this);
            }
        }
    }

    private String checkLogin(Object frame) {
        if (!(frame instanceof String[])) {
            return "No se recibio un nombre de usuario valido.";
        }

        String[] parts = (String[]) frame;
        if (parts.length != 2 || !"LOGIN".equals(parts[0]) || parts[1] == null) {
            return "Debes identificarte antes de enviar mensajes.";
        }

        username = parts[1].trim();
        if (username.isEmpty() || username.length() > MAX_USERNAME_LENGTH) {
            return "El nombre debe tener entre 1 y 24 caracteres.";
        }
        for (int i = 0; i < username.length(); i++) {
            if (Character.isISOControl(username.charAt(i))) {
                return "El nombre contiene caracteres no permitidos.";
            }
        }
        return null;
    }

    String username() {
        return username;
    }

    synchronized void send(String[] message) throws IOException {
        writer.writeObject(message);
        writer.flush();
    }

    void closeSocket() throws IOException {
        socket.close();
    }
}
