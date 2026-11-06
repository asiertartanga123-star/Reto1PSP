package retopspq.vista;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import retopspq.controller.PrincipalController;

import java.io.IOException;

/**
 * JavaFX App
 */
public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(App.class.getResource("view/Principal.fxml"));
        Parent root = loader.load();
        PrincipalController controller = loader.getController();
        Scene scene = new Scene(root, 1100, 735);
        stage.setTitle("Chat multiusuario");
        stage.setMinWidth(850);
        stage.setMinHeight(500);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> controller.closeConnection());
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }

}