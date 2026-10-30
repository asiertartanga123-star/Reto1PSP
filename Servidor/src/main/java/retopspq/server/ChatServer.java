package retopspq.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class ChatServer implements AutoCloseable {
    private static final int LOGIN_TIMEOUT_MILLIS = 10_000;
    private static final int MAX_FRAME_LENGTH = 8_192;
    private static final int MAX_MESSAGE_LENGTH = 2_000;
    private static final int MAX_USERNAME_LENGTH = 24;

    private final int port;
    private final int maxClients;
    private final int statsIntervalSeconds;
    private final long startedAt = System.currentTimeMillis();
    private final Object clientsLock = new Object();
    private final Map<String, ClientConnection> clients = new LinkedHashMap<>();
    private final ChatLogger logger = new ChatLogger(Paths.get("servidor.log"));
    private final AtomicReference<String> lastMessage = new AtomicReference<>("Ninguno");
    private volatile boolean closed;
    private ServerSocket serverSocket;

    private ChatServer(int port, int maxClients, int statsIntervalSeconds) {
        this.port = port;
        this.maxClients = maxClients;
        this.statsIntervalSeconds = statsIntervalSeconds;
    }

    public static void main(String[] args) throws IOException {
        int port = parseArgument(args, 0, 5000, "puerto");
        int maxClients = parseArgument(args, 1, 10, "numero maximo de clientes");
        int statsIntervalSeconds = parseArgument(args, 2, 10, "intervalo de estadisticas");
        if (port < 1 || port > 65_535 || maxClients < 1 || statsIntervalSeconds < 1) {
            throw new IllegalArgumentException("Puerto, maximo de clientes e intervalo deben ser positivos y validos.");
        }

        ChatServer server = new ChatServer(port, maxClients, statsIntervalSeconds);
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                server.close();
            }
        }, "chat-server-shutdown"));
        server.run();
    }

    private static int parseArgument(String[] args, int index, int defaultValue, String name) {
        if (args.length <= index) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("El " + name + " debe ser un numero entero.", exception);
        }
    }

    private void run() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Servidor iniciado en el puerto " + port + ". Maximo de clientes: " + maxClients + ".");
        Thread statsThread = new Thread(new Runnable() {
            @Override
            public void run() {
                printStatistics();
            }
        }, "chat-server-statistics");
        statsThread.setDaemon(true);
        statsThread.start();

        try {
            while (!closed) {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                Thread clientThread = new Thread(
                        new ClientConnection(socket),
                        "chat-client-" + socket.getRemoteSocketAddress());
                clientThread.start();
            }
        } catch (IOException exception) {
            if (!closed) {
                throw exception;
            }
        } finally {
            close();
        }
    }

    @Override
    public void close() {
        closed = true;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException exception) {
                System.err.println("No se pudo cerrar el socket del servidor: " + exception.getMessage());
            }
        }

        for (ClientConnection client : clientSnapshot()) {
            try {
                client.socket.close();
            } catch (IOException exception) {
                System.err.println("No se pudo cerrar la conexion de " + client.username + ": "
                        + exception.getMessage());
            }
        }
    }

    private void printStatistics() {
        while (!closed) {
            try {
                Thread.sleep(statsIntervalSeconds * 1_000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            if (closed) {
                return;
            }
            int connectedClients;
            synchronized (clientsLock) {
                connectedClients = clients.size();
            }
            Duration uptime = Duration.ofMillis(System.currentTimeMillis() - startedAt);
            long hours = uptime.toHours();
            long minutes = uptime.toMinutesPart();
            long seconds = uptime.toSecondsPart();
            System.out.printf("Usuarios conectados: %d | Tiempo activo: %02d:%02d:%02d | Ultimo mensaje: %s%n",
                    connectedClients, hours, minutes, seconds, lastMessage.get());
            System.out.flush();
        }
    }

    private List<ClientConnection> clientSnapshot() {
        synchronized (clientsLock) {
            return new ArrayList<>(clients.values());
        }
    }

    private String register(ClientConnection client) throws IOException {
        synchronized (clientsLock) {
            if (clients.size() >= maxClients) {
                return "El servidor ha alcanzado el maximo de clientes.";
            }
            for (ClientConnection connected : clients.values()) {
                if (connected.username.equalsIgnoreCase(client.username)) {
                    return "Ese nombre de usuario ya esta conectado.";
                }
            }
            clients.put(client.username, client);
            logger.log("ENTRA | " + client.username);
            return null;
        }
    }

    private void unregister(ClientConnection client) {
        boolean removed;
        synchronized (clientsLock) {
            removed = clients.remove(client.username, client);
            if (removed) {
                try {
                    logger.log("SALE | " + client.username);
                } catch (IOException exception) {
                    System.err.println("No se pudo registrar la desconexion de " + client.username + ": "
                            + exception.getMessage());
                }
            }
        }
        if (removed) {
            broadcast(new String[] {"INFO", client.username + " ha salido del chat."}, null);
        }
    }

    void handleFrame(ClientConnection sender, String[] frame) throws IOException {
        if (frame == null || frame.length == 0) {
            sender.send(new String[] {"ERROR", "Formato de mensaje no valido."});
            return;
        }
        try {
            if ("PUBLIC".equals(frame[0]) && frame.length == 2) {
                handlePublicMessage(sender, frame[1]);
            } else if ("PRIVATE".equals(frame[0]) && frame.length == 3) {
                handlePrivateMessage(sender, frame[1], frame[2]);
            } else {
                sender.send(new String[] {"ERROR", "Formato de mensaje no valido."});
            }
        } catch (IllegalArgumentException exception) {
            sender.send(new String[] {"ERROR", "El mensaje contiene datos no validos."});
        }
    }

    private void handlePublicMessage(ClientConnection sender, String message) throws IOException {
        if (!isValidMessage(message)) {
            sender.send(new String[] {"ERROR", "El mensaje no puede estar vacio ni superar 2000 caracteres."});
            return;
        }
        logger.log("PUBLICO | " + sender.username + " | " + logSafe(message));
        lastMessage.set("Publico de " + sender.username + ": " + logSafe(message));
        broadcast(new String[] {"PUBLIC", sender.username, message}, null);
    }

    private void handlePrivateMessage(ClientConnection sender, String recipientName, String message)
            throws IOException {
        if (!isValidMessage(message)) {
            sender.send(new String[] {"ERROR", "El mensaje no puede estar vacio ni superar 2000 caracteres."});
            return;
        }
        ClientConnection recipient = findClient(recipientName);
        if (recipient == null) {
            sender.send(new String[] {"ERROR", "El destinatario no esta conectado."});
            return;
        }
        if (recipient == sender) {
            sender.send(new String[] {"ERROR", "No puedes enviarte un mensaje privado a ti mismo."});
            return;
        }

        logger.log("PRIVADO | " + sender.username + " para " + recipient.username + " | " + logSafe(message));
        lastMessage.set("Privado de " + sender.username + " para " + recipient.username + ": "
                + logSafe(message));
        String[] frame = new String[] {"PRIVATE", sender.username, recipient.username, message};
        try {
            recipient.send(frame);
        } catch (IOException exception) {
            unregister(recipient);
            sender.send(new String[] {"ERROR", "No se pudo entregar el mensaje: el destinatario se ha desconectado."});
            return;
        }
        sender.send(frame);
    }

    private ClientConnection findClient(String username) {
        synchronized (clientsLock) {
            for (ClientConnection client : clients.values()) {
                if (client.username.equalsIgnoreCase(username)) {
                    return client;
                }
            }
            return null;
        }
    }

    private     void broadcast(String[] frame, ClientConnection excluded) {
        for (ClientConnection client : clientSnapshot()) {
            if (client != excluded) {
                try {
                    client.send(frame);
                } catch (IOException exception) {
                    System.err.println("Error enviando a " + client.username + ": " + exception.getMessage());
                    unregister(client);
                }
            }
        }
    }
    private static boolean isValidMessage(String message) {
        return !message.trim().isEmpty() && message.length() <= MAX_MESSAGE_LENGTH;
    }

    private static String logSafe(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String encode(String type, String... values) {
        StringBuilder frame = new StringBuilder(type);
        for (String value : values) {
            frame.append('|').append(Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(value.getBytes(StandardCharsets.UTF_8)));
        }
        return frame.toString();
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private final class ClientConnection implements Runnable {
        private final Socket socket;
        private String username;
        private ObjectInputStream objectReader;
        private ObjectOutputStream objectWriter;

        private ClientConnection(Socket socket) {
            this.socket = socket;
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

                String registrationError = register(this);
                if (registrationError != null) {
                    send(new String[] {"REJECT", registrationError});
                    return;
                }

                send(new String[] {"WELCOME", username});
                clientSocket.setSoTimeout(0);
                broadcast(new String[] {"INFO", username + " se ha unido al chat."}, this);
                while (true) {
                    Object message = objectReader.readObject();
                    if (message == null) {
                        break;
                    }
                    if (message instanceof String[]) {
                        handleFrame(this, (String[]) message);
                    }
                }
            } catch (IOException | ClassNotFoundException exception) {
                if (!closed) {
                    System.err.println("Conexion finalizada con error: " + exception.getMessage());
                }
            } finally {
                if (username != null) {
                    unregister(this);
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

        private synchronized void send(String[] frame) throws IOException {
            objectWriter.writeObject(frame);
            objectWriter.flush();
        }
    }

    private static final class ChatLogger {
        private final Path logFile;

        private ChatLogger(Path logFile) {
            this.logFile = logFile;
        }

        private synchronized void log(String event) throws IOException {
            String line = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    + " | " + logSafe(event) + System.lineSeparator();
            Files.write(logFile, line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        }
    }
}