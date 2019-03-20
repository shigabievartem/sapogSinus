package sample;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import static sample.utils.SapogConst.WindowConfigLocations.mainWindowConfigLocation;

public class ApplicationStarter extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Sapog-Sinus Configuration Tool vXX");
        primaryStage.setResizable(false);
        primaryStage.setScene(new Scene(FXMLLoader.load(mainWindowConfigLocation), 800, 600));
        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }
}
