package org.example.VisuAlgorithm;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

public class Launcher extends Application {

    private static Stage primaryStage;
    private static Scene mainScene;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("intro-view.fxml")
        );

        mainScene = new Scene(loader.load(), 1000, 650);

        mainScene.getStylesheets().add(
                getClass().getResource("/org/example/VisuAlgorithm/styles/main.css")
                        .toExternalForm()
        );

        stage.setTitle("Algorithm Visualizer");
        stage.setScene(mainScene);

        stage.setMinWidth(900);
        stage.setMinHeight(600);

        stage.centerOnScreen();
        stage.show();
    }

    public static void switchScene(String fxmlFile) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    Launcher.class.getResource("/org/example/VisuAlgorithm/" + fxmlFile)
            );

            mainScene.setRoot(loader.load());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}