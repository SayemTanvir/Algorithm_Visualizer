package org.example.VisuAlgorithm;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.image.Image;

import java.io.IOException;

public class HelloApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {

        // Load INTRO instead of hello-view
        FXMLLoader fxmlLoader = new FXMLLoader(
                HelloApplication.class.getResource("intro-view.fxml")
        );

        Scene scene = new Scene(fxmlLoader.load(), 1400, 700);

        // Apply CSS
        scene.getStylesheets().add(getClass().getResource("/org/example/VisuAlgorithm/styles/main.css").toExternalForm());

        stage.setTitle("VisuAlgorithm - Beautiful DSA Visualizations");

        // Icon (safe)
        try {
            Image image = new Image(getClass().getResourceAsStream("/Images/sorting.png"));
            stage.getIcons().add(image);
        } catch (Exception e) {
            System.out.println("Icon not found");
        }

        stage.setScene(scene);
        stage.setMaximized(false); // Start normal size
        stage.show();
    }

}