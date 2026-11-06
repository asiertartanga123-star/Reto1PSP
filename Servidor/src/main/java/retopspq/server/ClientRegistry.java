package retopspq.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ClientRegistry {
    private final int maxClients;
    private final ChatLogger logger;
    private final Object clientsLock = new Object();
    private final Map<String, ClientConnection> clients = new HashMap<>();
    private volatile boolean serverClosed;

    ClientRegistry(int maxClients, ChatLogger logger) {
        this.maxClients = maxClients;
        this.logger = logger;
    }

    String register(ClientConnection client) throws IOException {
        synchronized (clientsLock) {
            if (clients.size() >= maxClients) {
                return "El servidor ha alcanzado el maximo de clientes.";
            }
            for (ClientConnection connected : clients.values()) {
                if (connected.username().equalsIgnoreCase(client.username())) {
                    return "Ese nombre de usuario ya esta conectado.";
                }
            }
            clients.put(client.username(), client);
            logger.log("ENTRA | " + client.username());
            return null;
        }
    }

    void unregister(ClientConnection client) {
        boolean removed;
        synchronized (clientsLock) {
            removed = clients.remove(client.username(), client);
            if (removed) {
                try {
                    logger.log("SALE | " + client.username());
                } catch (IOException exception) {
                    System.err.println("No se pudo registrar la desconexion de " + client.username() + ": "
                            + exception.getMessage());
                }
            }
        }
        if (removed) {
            broadcast(new String[] {"INFO", client.username() + " ha salido del chat."}, null);
        }
    }

    ClientConnection find(String username) {
        synchronized (clientsLock) {
            for (ClientConnection client : clients.values()) {
                if (client.username().equalsIgnoreCase(username)) {
                    return client;
                }
            }
            return null;
        }
    }

    void broadcast(String[] frame, ClientConnection excluded) {
        for (ClientConnection client : snapshot()) {
            if (client != excluded) {
                try {
                    client.send(frame);
                } catch (IOException exception) {
                    System.err.println("Error enviando a " + client.username() + ": " + exception.getMessage());
                    unregister(client);
                }
            }
        }
    }

    int size() {
        synchronized (clientsLock) {
            return clients.size();
        }
    }

    List<ClientConnection> snapshot() {
        synchronized (clientsLock) {
            return new ArrayList<>(clients.values());
        }
    }

    void closeServer() {
        serverClosed = true;
    }

    boolean isServerClosed() {
        return serverClosed;
    }
}
