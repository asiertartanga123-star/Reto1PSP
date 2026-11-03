module retopspq {
    requires transitive javafx.controls;
    requires transitive javafx.graphics;
    requires javafx.fxml;

    opens retopspq to javafx.fxml;
    exports retopspq;
}
