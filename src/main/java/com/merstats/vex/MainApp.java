package com.merstats.vex;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

// Extending Application tells Java this is a visual program
public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {

        // 1. Locate the FXML file in the resources folder
        FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("/com/merstats/vex/main_view.fxml"));

        // 2. Load the FXML into a Scene (the contents of the window)
        Scene scene = new Scene(fxmlLoader.load(), 600, 400);

        // 3. Configure the Stage (the actual window border and title bar)
        stage.setTitle("VEX Skills Tracker");
        stage.setScene(scene);

        // 4. Show the window!
        stage.show();
    }

    public static void main(String[] args) {
        // This launches the JavaFX lifecycle
        launch();
    }
}