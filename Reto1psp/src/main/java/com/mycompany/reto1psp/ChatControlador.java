package com.mycompany.reto1psp;

import java.io.IOException;
import javafx.fxml.FXML;

public class ChatControlador {

    @FXML
    private void switchToSecondary() throws IOException {
        App.setRoot("secondary");
    }
}
