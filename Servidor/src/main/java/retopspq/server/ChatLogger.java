package retopspq.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Escribe eventos del servidor en el fichero de registro con fecha y hora.
 */
final class ChatLogger {
    private final Path logFile;

    ChatLogger(Path logFile) {
        this.logFile = logFile;
    }

    synchronized void log(String event) throws IOException {
        String safeEvent = event.replace("\r", "\\r").replace("\n", "\\n");
        String line = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + " | " + safeEvent + System.lineSeparator();
        Files.write(logFile, line.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }
}
