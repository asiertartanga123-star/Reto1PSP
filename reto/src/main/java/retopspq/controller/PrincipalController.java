package retopspq.controller;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class PrincipalController {
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int LOGIN_TIMEOUT_MILLIS = 10_000;
    private static final int MAX_MESSAGE_LENGTH = 2_000;
    private static final int MAX_USERNAME_LENGTH = 24;

    @FXML
    private TextField ipField;
    @FXML
    private TextField portField;
    @FXML
    private TextField usernameField;
    @FXML
    private TextField recipientField;
    @FXML
    private TextField messageField;
    @FXML
    private CheckBox privateCheckBox;
    @FXML
    private Button connectButton;
    @FXML
    private Button disconnectButton;
    @FXML
    private Button sendButton;
    @FXML
    private Label statusLabel;
    @FXML
    private TextArea chatArea;

    private volatile Socket clientSocket;
    private volatile ObjectOutputStream clientWriter;
    private volatile ObjectInputStream clientReader;
    private volatile String currentUsername;
    private volatile boolean connecting;

    @FXML
    private void initialize() {
        updateControls();
    }

    @FXML
    private void onConnect() {
        if (connecting || clientSocket != null) {
            return;
        }

        String host = ipField.getText().trim();
        String username = usernameField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException exception) {
            showConnectionError("El puerto debe ser un numero entre 1 y 65535.");
            return;
        }
        if (host.isEmpty() || port < 1 || port > 65_535) {
            showConnectionError("Indica una IP o nombre de servidor y un puerto valido.");
            return;
        }
        if (username.isEmpty() || username.length() > MAX_USERNAME_LENGTH
                || containsControlCharacter(username)) {
            showConnectionError("El nombre debe tener entre 1 y 24 caracteres y no contener controles.");
            return;
        }

        connecting = true;
        statusLabel.setText("Conectando...");
        updateControls();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                openConnection(host, port, username);
            }
        }, "chat-connect");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onDisconnect() {
        closeConnection();
    }

    public void closeConnection() {
        Socket socket = clientSocket;
        clientSocket = null;
        clientWriter = null;
        clientReader = null;
        currentUsername = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException exception) {
                appendMessage("Error al cerrar la conexion: " + exception.getMessage());
            }
        }
        connecting = false;
        updateControls();
        statusLabel.setText("No conectado");
    }

    @FXML
    private void onSend() {
        String message = messageField.getText().trim();
        if (message.isEmpty()) {
            return;
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            statusLabel.setText("El mensaje no puede superar 2000 caracteres.");
            return;
        }

        String[] frame;
        if (privateCheckBox.isSelected()) {
            String recipient = recipientField.getText().trim();
            if (recipient.isEmpty()) {
                statusLabel.setText("Indica el destinatario del mensaje privado.");
                return;
            }
            frame = new String[] {"PRIVATE", recipient, message};
        } else {
            frame = new String[] {"PUBLIC", message};
        }

        ObjectOutputStream writer = clientWriter;
        if (writer == null) {
            statusLabel.setText("No hay una conexion activa.");
            return;
        }
        synchronized (writer) {
            try {
                writer.writeObject(frame);
                writer.flush();
            } catch (IOException exception) {
                closeConnection();
                statusLabel.setText("Se perdio la conexion con el servidor.");
                return;
            }
        }
        messageField.clear();
    }

    @FXML
    private void onPrivateToggled() {
        updateControls();
    }

    private void openConnection(String host, int port, String username) {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
            socket.setSoTimeout(LOGIN_TIMEOUT_MILLIS);
            ObjectOutputStream writer = new ObjectOutputStream(socket.getOutputStream());
            writer.flush();
            ObjectInputStream reader = new ObjectInputStream(socket.getInputStream());

            writer.writeObject(new String[] {"LOGIN", username});
            writer.flush();

            Object responseObj = reader.readObject();
            if (!(responseObj instanceof String[])) {
                throw new IOException("El servidor envio una respuesta de conexion no valida.");
            }
            String[] fields = (String[]) responseObj;
            if (fields.length == 2 && "REJECT".equals(fields[0])) {
                throw new IOException(fields[1]);
            }
            if (fields.length != 2 || !"WELCOME".equals(fields[0]) || !username.equals(fields[1])) {
                throw new IOException("El servidor envio una respuesta de conexion no valida.");
            }

            socket.setSoTimeout(0);
            clientSocket = socket;
            clientWriter = writer;
            clientReader = reader;
            currentUsername = username;
            connecting = false;
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    if (clientSocket == socket) {
                        statusLabel.setText("Conectado como " + username);
                        appendMessage("Conectado al servidor " + host + ":" + port + ".");
                        updateControls();
                    }
                }
            });
            Thread receiver = new Thread(new Runnable() {
                @Override
                public void run() {
                    receiveMessages(socket, reader);
                }
            }, "chat-receiver-" + username);
            receiver.setDaemon(true);
            receiver.start();
        } catch (IOException | ClassNotFoundException | IllegalArgumentException exception) {
            closeSocket(socket);
            connecting = false;
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    if (clientSocket == null) {
                        statusLabel.setText("Error de conexion: " + exception.getMessage());
                        updateControls();
                    }
                }
            });
        }
    }

    private void receiveMessages(Socket socket, ObjectInputStream reader) {
        String disconnectReason = "El servidor ha cerrado la conexion.";
        try {
            while (true) {
                Object message = reader.readObject();
                if (message == null) {
                    break;
                }
                if (message instanceof String[]) {
                    displayFrame((String[]) message);
                }
            }
        } catch (IOException | ClassNotFoundException exception) {
            if (!socket.isClosed()) {
                disconnectReason = "Conexion perdida: " + exception.getMessage();
            }
        } finally {
            if (clientSocket == socket) {
                clientSocket = null;
                clientWriter = null;
                clientReader = null;
                currentUsername = null;
                String reason = disconnectReason;
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        if (clientSocket == null) {
                            statusLabel.setText(reason);
                            updateControls();
                        }
                    }
                });
            }
            closeSocket(socket);
        }
    }

    private void displayFrame(String[] fields) {
        try {
            switch (fields[0]) {
                case "INFO":
                    requireFieldCount(fields, 2);
                    appendMessage(fields[1]);
                    break;
                case "PUBLIC":
                    requireFieldCount(fields, 3);
                    appendMessage("[Publico] " + fields[1] + ": " + fields[2]);
                    break;
                case "PRIVATE":
                    requireFieldCount(fields, 4);
                    String sender = fields[1];
                    String recipient = fields[2];
                    String direction = sender.equals(currentUsername)
                            ? "para " + recipient : "de " + sender;
                    appendMessage("[Privado " + direction + "] " + fields[3]);
                    break;
                case "ERROR":
                    requireFieldCount(fields, 2);
                    appendMessage("Error: " + fields[1]);
                    break;
                case "REJECT":
                    requireFieldCount(fields, 2);
                    throw new IOException(fields[1]);
                case "WELCOME":
                    requireFieldCount(fields, 2);
                    break;
                default:
                    appendMessage("El servidor envio un mensaje desconocido.");
                    break;
            }
        } catch (IOException | IllegalArgumentException exception) {
            appendMessage("Se recibio un mensaje no valido del servidor.");
        }
    }

    private void updateControls() {
        boolean connected = clientSocket != null;
        connectButton.setDisable(connected || connecting);
        disconnectButton.setDisable(!connected);
        ipField.setDisable(connected || connecting);
        portField.setDisable(connected || connecting);
        usernameField.setDisable(connected || connecting);
        privateCheckBox.setDisable(!connected);
        recipientField.setDisable(!connected || !privateCheckBox.isSelected());
        messageField.setDisable(!connected);
        sendButton.setDisable(!connected);
    }

    private void showConnectionError(String message) {
        statusLabel.setText(message);
    }

    private void appendMessage(String message) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                chatArea.appendText(message + System.lineSeparator());
            }
        });
    }

    private static boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static void requireFieldCount(String[] fields, int expected) {
        if (fields.length != expected) {
            throw new IllegalArgumentException("Numero de campos incorrecto.");
        }
    }

    private static void closeSocket(Socket socket) {
        try {
            socket.close();
        } catch (IOException exception) {
            System.err.println("No se pudo cerrar el socket: " + exception.getMessage());
        }
    }
}