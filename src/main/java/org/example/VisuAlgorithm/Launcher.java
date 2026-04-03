package org.example.VisuAlgorithm;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.animation.FadeTransition;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.util.Duration;

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
            var currentRoot = mainScene.getRoot();

            FadeTransition fadeOut = new FadeTransition(Duration.millis(180), currentRoot);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);

            fadeOut.setOnFinished(event -> {
                try {
                    FXMLLoader loader = new FXMLLoader(
                            Launcher.class.getResource(
                                    "/org/example/VisuAlgorithm/" + fxmlFile
                            )
                    );

                    Parent newRoot = loader.load();
                    newRoot.setOpacity(0);

                    mainScene.setRoot(newRoot);

                    FadeTransition fadeIn = new FadeTransition(
                            Duration.millis(220), newRoot
                    );
                    fadeIn.setFromValue(0.0);
                    fadeIn.setToValue(1.0);
                    fadeIn.play();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            fadeOut.play();

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