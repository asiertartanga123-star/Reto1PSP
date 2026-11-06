package retopspq.server;

import java.io.IOException;

final class ChatMessageHandler {
    private static final int MAX_MESSAGE_LENGTH = 2_000;
    private final ClientRegistry clients;
    private final ChatLogger logger;
    private volatile String lastMessage = "Ninguno";

    ChatMessageHandler(ClientRegistry clients, ChatLogger logger) {
        this.clients = clients;
        this.logger = logger;
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

    String lastMessage() {
        return lastMessage;
    }

    private void handlePublicMessage(ClientConnection sender, String message) throws IOException {
        if (!isValidMessage(message)) {
            sender.send(new String[] {"ERROR", "El mensaje no puede estar vacio ni superar 2000 caracteres."});
            return;
        }
        logger.log("PUBLICO | " + sender.username() + " | " + logSafe(message));
        lastMessage = "Publico de " + sender.username() + ": " + logSafe(message);
        clients.broadcast(new String[] {"PUBLIC", sender.username(), message}, null);
    }

    private void handlePrivateMessage(ClientConnection sender, String recipientName, String message)
            throws IOException {
        if (!isValidMessage(message)) {
            sender.send(new String[] {"ERROR", "El mensaje no puede estar vacio ni superar 2000 caracteres."});
            return;
        }
        ClientConnection recipient = clients.find(recipientName);
        if (recipient == null) {
            sender.send(new String[] {"ERROR", "El destinatario no esta conectado."});
            return;
        }
        if (recipient == sender) {
            sender.send(new String[] {"ERROR", "No puedes enviarte un mensaje privado a ti mismo."});
            return;
        }

        logger.log("PRIVADO | " + sender.username() + " para " + recipient.username() + " | " + logSafe(message));
        lastMessage = "Privado de " + sender.username() + " para " + recipient.username() + ": "
                + logSafe(message);
        String[] frame = new String[] {"PRIVATE", sender.username(), recipient.username(), message};
        try {
            recipient.send(frame);
        } catch (IOException exception) {
            clients.unregister(recipient);
            sender.send(new String[] {"ERROR", "No se pudo entregar el mensaje: el destinatario se ha desconectado."});
            return;
        }
        sender.send(frame);
    }

    private static boolean isValidMessage(String message) {
        return message != null && !message.trim().isEmpty() && message.length() <= MAX_MESSAGE_LENGTH;
    }

    private static String logSafe(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
