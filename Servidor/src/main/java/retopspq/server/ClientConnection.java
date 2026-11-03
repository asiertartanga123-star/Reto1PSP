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
    // Tiempo máximo que el cliente tiene para identificarse.
    private static final int LOGIN_TIMEOUT_MILLIS = 10_000;

    // Límite máximo de caracteres para el nombre de usuario.
    private static final int MAX_USERNAME_LENGTH = 24;

    // Socket dedicado a este cliente.
    private final Socket socket;

    // Registro central de usuarios activos en el servidor.
    private final ClientRegistry clients;

    // Manejador de mensajes del protocolo del chat.
    private final ChatMessageHandler messageHandler;

    // Nombre del usuario actual.
    private String username;

    // Flujos para leer y escribir objetos con el cliente.
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
            // Pantalla de espera para recibir el login del cliente.
            clientSocket.setSoTimeout(LOGIN_TIMEOUT_MILLIS);
            objectWriter = new ObjectOutputStream(clientSocket.getOutputStream());
            objectWriter.flush();
            objectReader = new ObjectInputStream(clientSocket.getInputStream());

            // Lee el primer frame, que debe ser el login del usuario.
            Object loginMessage = objectReader.readObject();
            String loginError = parseLogin(loginMessage);
            if (loginError != null) {
                send(new String[] {"REJECT", loginError});
                return;
            }

            // Intenta registrar el usuario en el servidor.
            String registrationError = clients.register(this);
            if (registrationError != null) {
                send(new String[] {"REJECT", registrationError});
                return;
            }

            // Envío de bienvenida y anuncio de entrada al resto.
            send(new String[] {"WELCOME", username});
            clientSocket.setSoTimeout(0);
            clients.broadcast(new String[] {"INFO", username + " se ha unido al chat."}, this);

            // Mientras la conexión esté abierta, cada objeto recibido será un mensaje del cliente.
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
            // Si el usuario quedó registrado, se elimina del listado al cerrar.
            if (username != null) {
                clients.unregister(this);
            }
        }
    }

    // Valida el nombre de usuario enviado durante el login.
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

    // Devuelve el nombre del usuario asociado a esta conexión.
    String username() {
        return username;
    }

    // Envía un frame al cliente de forma sincronizada.
    void send(String[] frame) throws IOException {
        synchronized (this) {
            objectWriter.writeObject(frame);
            objectWriter.flush();
        }
    }

    // Cierra el socket para forzar la desconexión del cliente.
    void closeSocket() throws IOException {
        socket.close();
    }
}
