module retopspq {
    requires javafx.controls;
    requires javafx.fxml;

    opens retopspq.controller to javafx.fxml;
    exports retopspq;
}
