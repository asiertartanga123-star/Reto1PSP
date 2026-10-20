module retopspq {
    requires javafx.controls;
    requires javafx.fxml;

    opens retopspq to javafx.fxml;
    exports retopspq;
}
