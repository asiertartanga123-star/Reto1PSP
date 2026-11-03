package retopspq.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

/**
 * Coordina el arranque, la aceptacion de conexiones y el cierre del servidor.
 */
public final class ChatServer implements AutoCloseable {
    private final int port;
    private final int maxClients;
    private final int statsIntervalSeconds;
    private final long startedAt = System.currentTimeMillis();
    private final ChatLogger logger = new ChatLogger(Paths.get("servidor.log"));
    private final ClientRegistry clients;
    private final ChatMessageHandler messageHandler;
    private ServerSocket serverSocket;

    private ChatServer(int port, int maxClients, int statsIntervalSeconds) {
        this.port = port;
        this.maxClients = maxClients;
        this.statsIntervalSeconds = statsIntervalSeconds;
        clients = new ClientRegistry(maxClients, logger);
        messageHandler = new ChatMessageHandler(clients, logger);
    }

    /** Lee los argumentos, configura el cierre seguro y pone en marcha el servidor. */
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
            // Cada conexion se procesa en paralelo para atender a varios usuarios a la vez.
            while (!clients.isServerClosed()) {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                Thread clientThread = new Thread(
                        new ClientConnection(socket, clients, messageHandler),
                        "chat-client-" + socket.getRemoteSocketAddress());
                clientThread.start();
            }
        } catch (IOException exception) {
            if (!clients.isServerClosed()) {
                throw exception;
            }
        } finally {
            close();
        }
    }

    @Override
    public void close() {
        clients.closeServer();
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException exception) {
                System.err.println("No se pudo cerrar el socket del servidor: " + exception.getMessage());
            }
        }

        List<ClientConnection> connectedClients = clients.snapshot();
        for (ClientConnection client : connectedClients) {
            try {
                client.closeSocket();
            } catch (IOException exception) {
                System.err.println("No se pudo cerrar la conexion de " + client.username() + ": "
                        + exception.getMessage());
            }
        }
    }

    private void printStatistics() {
        while (!clients.isServerClosed()) {
            try {
                Thread.sleep(statsIntervalSeconds * 1_000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            if (clients.isServerClosed()) {
                return;
            }
            int connectedClients = clients.size();
            Duration uptime = Duration.ofMillis(System.currentTimeMillis() - startedAt);
            long hours = uptime.toHours();
            long minutes = uptime.toMinutesPart();
            long seconds = uptime.toSecondsPart();
            System.out.printf("Usuarios conectados: %d | Tiempo activo: %02d:%02d:%02d | Ultimo mensaje: %s%n",
                    connectedClients, hours, minutes, seconds, messageHandler.lastMessage());
            System.out.flush();
        }
    }
}
