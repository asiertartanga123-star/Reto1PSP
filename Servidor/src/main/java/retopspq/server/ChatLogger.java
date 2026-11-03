package retopspq.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Guarda los eventos del servidor en un fichero de registro.
 * Cada entrada incluye la fecha, la hora y el texto del evento.
 */
final class ChatLogger {
    // Ruta del archivo donde se almacenará el historial del servidor.
    private final Path logFile;

    // Recibe la ruta del fichero de log al crear la instancia.
    ChatLogger(Path logFile) {
        this.logFile = logFile;
    }

    // Escribe un evento en el log de forma sincronizada para evitar mezclas
    // si varios hilos intentan registrar cosas a la vez.
    synchronized void log(String event) throws IOException {
        // Se sustituyen saltos de línea y retornos de carro para que cada evento
        // quede en una sola línea del fichero.
        String safeEvent = event.replace("\r", "\\r").replace("\n", "\\n");

        // Se añade la marca temporal actual antes del texto del evento.
        String line = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + " | " + safeEvent + System.lineSeparator();

        // Se escribe al final del archivo, creando el fichero si no existe.
        Files.write(logFile, line.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }
}
