package com.merstats.vex;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {

        FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("/com/merstats/vex/main_view.fxml"));

        // Load the scene with a fallback default resolution
        Scene scene = new Scene(fxmlLoader.load(), 1150, 760);

        stage.setTitle("MerStats VEX - Tech Portal");
        stage.setScene(scene);

        // --- ADDED: Make the window maximized on startup ---
        stage.setMaximized(true);

        // Note: If you want TRUE full screen (hiding the Windows taskbar completely),
        // delete the line above and uncomment the line below:
        // stage.setFullScreen(true);

        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}