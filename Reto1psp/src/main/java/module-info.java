module com.mycompany.reto1psp {
    requires javafx.controls;
    requires javafx.fxml;

    opens com.mycompany.reto1psp to javafx.fxml;
    exports com.mycompany.reto1psp;
}
