package retopspq.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Atiende a un cliente: comprueba su inicio de sesion y procesa sus mensajes.
 * Cada instancia se ejecuta en su propio hilo.
 */
final class ClientConnection implements Runnable {
    private static final int LOGIN_TIMEOUT_MILLIS = 10_000;
    private static final int MAX_USERNAME_LENGTH = 24;

    private final Socket socket;
    private final ClientRegistry clients;
    private final ChatMessageHandler messageHandler;
    private String username;
    private ObjectInputStream objectReader;
    private ObjectOutputStream objectWriter;

    ClientConnection(Socket socket, ClientRegistry clients, ChatMessageHandler messageHandler) {
        this.socket = socket;
        this.clients = clients;
        this.messageHandler = messageHandler;
    }

    @Override
    public void run() {
        try (Socket clientSocket = socket) {
            clientSocket.setSoTimeout(LOGIN_TIMEOUT_MILLIS);
            objectWriter = new ObjectOutputStream(clientSocket.getOutputStream());
            objectWriter.flush();
            objectReader = new ObjectInputStream(clientSocket.getInputStream());

            Object loginMessage = objectReader.readObject();
            String loginError = parseLogin(loginMessage);
            if (loginError != null) {
                send(new String[] {"REJECT", loginError});
                return;
            }

            String registrationError = clients.register(this);
            if (registrationError != null) {
                send(new String[] {"REJECT", registrationError});
                return;
            }

            send(new String[] {"WELCOME", username});
            clientSocket.setSoTimeout(0);
            clients.broadcast(new String[] {"INFO", username + " se ha unido al chat."}, this);

            // Mientras la conexion siga abierta, cada objeto recibido es una peticion del cliente.
            while (true) {
                Object message = objectReader.readObject();
                if (message == null) {
                    break;
                }
                if (message instanceof String[]) {
                    messageHandler.handleFrame(this, (String[]) message);
                }
            }
        } catch (IOException | ClassNotFoundException exception) {
            if (!clients.isServerClosed()) {
                System.err.println("Conexion finalizada con error: " + exception.getMessage());
            }
        } finally {
            if (username != null) {
                clients.unregister(this);
            }
        }
    }

    private String parseLogin(Object frame) {
        if (!(frame instanceof String[])) {
            return "No se recibio un nombre de usuario valido.";
        }
        String[] parts = (String[]) frame;
        if (parts.length != 2 || !"LOGIN".equals(parts[0])) {
            return "Debes identificarte antes de enviar mensajes.";
        }
        try {
            username = parts[1].trim();
        } catch (RuntimeException exception) {
            return "El nombre de usuario no es valido.";
        }
        if (username.isEmpty() || username.length() > MAX_USERNAME_LENGTH) {
            return "El nombre debe tener entre 1 y 24 caracteres.";
        }
        for (int i = 0; i < username.length(); i++) {
            if (Character.isISOControl(username.charAt(i))) {
                return "El nombre de usuario contiene caracteres no permitidos.";
            }
        }
        return null;
    }

    String username() {
        return username;
    }

    void send(String[] frame) throws IOException {
        synchronized (this) {
            objectWriter.writeObject(frame);
            objectWriter.flush();
        }
    }

    void closeSocket() throws IOException {
        socket.close();
    }
}
