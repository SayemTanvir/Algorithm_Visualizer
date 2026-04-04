//package org.example.VisuAlgorithm;
//
//import javafx.animation.KeyFrame;
//import javafx.animation.KeyValue;
//import javafx.animation.PauseTransition;
//import javafx.animation.Timeline;
//import javafx.fxml.FXML;
//import javafx.fxml.FXMLLoader;
//import javafx.scene.Parent;
//import javafx.scene.Scene;
//import javafx.scene.control.Label;
//import javafx.scene.control.ProgressBar;
//import javafx.scene.effect.DropShadow;
//import javafx.scene.layout.Pane;
//import javafx.scene.paint.Color;
//import javafx.scene.shape.Circle;
//import javafx.stage.Stage;
//import javafx.util.Duration;
//
//import java.util.Random;
//
//public class IntroController {
//
//    @FXML private Pane backgroundPane;
//    @FXML private ProgressBar loadingBar;
//    @FXML private Label loadingLabel;
//
//    private Timeline backgroundTimeline;
//    private Timeline loadingTimeline;
//
//    @FXML
//    public void initialize() {
//        setupAnimatedBackground();
//        setupLoadingAnimation();
//
//    }
//
//    // ============= ANIMATED BACKGROUND =============
//    private void setupAnimatedBackground() {
//        if (backgroundPane == null) return;
//
//        // Create 15-20 floating particles
//        Random rand = new Random();
//        for (int i = 0; i < 18; i++) {
//            double x = rand.nextDouble() * 1400;
//            double y = rand.nextDouble() * 700;
//            double size = 2 + rand.nextDouble() * 4;
//            double duration = 8 + rand.nextDouble() * 6;
//
//            Circle particle = new Circle(x, y, size);
//            particle.setFill(Color.web(getRandomGradientColor()));
//            particle.setOpacity(0.3 + rand.nextDouble() * 0.5);
//            particle.setEffect(new DropShadow(8, Color.web(getRandomGradientColor()).darker()));
//
//            backgroundPane.getChildren().add(particle);
//
//            // Animate floating motion
//            animateParticle(particle, duration);
//        }
//    }
//
//    private void animateParticle(Circle particle, double duration) {
//        Random rand = new Random();
//        double endX = rand.nextDouble() * 1400;
//        double endY = rand.nextDouble() * 700;
//        double rotationAngle = rand.nextDouble() * 360;
//
//        Timeline timeline = new Timeline(
//                new KeyFrame(Duration.ZERO,
//                        new KeyValue(particle.centerXProperty(), particle.getCenterX()),
//                        new KeyValue(particle.centerYProperty(), particle.getCenterY()),
//                        new KeyValue(particle.opacityProperty(), particle.getOpacity())
//                ),
//                new KeyFrame(Duration.seconds(duration),
//                        new KeyValue(particle.centerXProperty(), endX),
//                        new KeyValue(particle.centerYProperty(), endY),
//                        new KeyValue(particle.opacityProperty(), 0.1 + Math.random() * 0.4)
//                )
//        );
//        timeline.setCycleCount(Timeline.INDEFINITE);
//        timeline.setAutoReverse(true);
//        timeline.play();
//    }
//
//    // ============= LOADING ANIMATION =============
//    private void setupLoadingAnimation() {
//        // Progress bar animation
//        loadingTimeline = new Timeline(
//                new KeyFrame(Duration.ZERO, new KeyValue(loadingBar.progressProperty(), 0)),
//                new KeyFrame(Duration.seconds(3.5), new KeyValue(loadingBar.progressProperty(), 1.0))
//        );
//        loadingTimeline.play();
//
//        // Loading text animation
//        Timeline textTimeline = new Timeline();
//        String[] texts = {
//                "Loading...",
//                "Loading.",
//                "Loading..",
//                "Loading..."
//        };
//
//        for (int i = 0; i < texts.length; i++) {
//            final int index = i;
//            textTimeline.getKeyFrames().add(
//                    new KeyFrame(Duration.seconds(0.4 * i),
//                            e -> loadingLabel.setText(texts[index])
//                    )
//            );
//        }
//        textTimeline.setCycleCount(Timeline.INDEFINITE);
//        textTimeline.play();
//
//        // After 4 seconds, transition to home
//        Timeline transitionTimeline = new Timeline(
//                new KeyFrame(Duration.seconds(4), e -> transitionToHome())
//        );
//        transitionTimeline.play();
//    }
//
//    // ============= TRANSITION TO HOME =============
//    private void transitionToHome() {
//        try {
//            // Fade out intro
//            Timeline fadeOut = new Timeline(
//                    new KeyFrame(Duration.seconds(0.6),
//                            new KeyValue(backgroundPane.opacityProperty(), 0),
//                            new KeyValue(loadingBar.opacityProperty(), 0),
//                            new KeyValue(loadingLabel.opacityProperty(), 0)
//                    )
//            );
//
//            fadeOut.setOnFinished(e -> {
//                try {
////                    FXMLLoader loader = new FXMLLoader(getClass().getResource("hello-view.fxml"));
////                    Parent root = loader.load();
////                    Stage stage = (Stage) backgroundPane.getScene().getWindow();
////                    Scene scene = new Scene(root);
////                    scene.getStylesheets().add(getClass().getResource("/org/example/VisuAlgorithm/styles/main.css").toExternalForm());
////                    stage.setScene(scene);
//                    Launcher.switchScene("hello-view.fxml");
//
//                } catch (Exception ex) {
//                    ex.printStackTrace();
//                }
//            });
//            fadeOut.play();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    // ============= HELPER METHODS =============
//    private String getRandomGradientColor() {
//        String[] colors = {
//                "#00d4ff",  // Cyan
//                "#7c3aed",  // Purple
//                "#ff006e",  // Pink
//                "#00ff88",  // Green
//                "#ffa500",  // Orange
//                "#1e90ff"   // Dodger Blue
//        };
//        Random rand = new Random();
//        return colors[rand.nextInt(colors.length)];
//    }
//}
package org.example.VisuAlgorithm;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class IntroController {

    @FXML
    private StackPane rootPane;

    @FXML
    private Pane bgPane;

    @FXML
    private Label titleLabel;

    @FXML
    private Label subLabel;

    @FXML
    public void initialize() {
        createFloatingBackground();
        playIntro();
    }

    private void createFloatingBackground() {
        for (int i = 0; i < 10; i++) {
            Circle node = new Circle(12 + Math.random() * 8);
            node.setFill(Color.web("#00d4ff"));
            node.setOpacity(0.12);

            node.setLayoutX(Math.random() * 1200);
            node.setLayoutY(Math.random() * 700);

            bgPane.getChildren().add(node);

            TranslateTransition tt =
                    new TranslateTransition(Duration.seconds(3 + Math.random() * 2), node);

            tt.setByY(-40);
            tt.setAutoReverse(true);
            tt.setCycleCount(Animation.INDEFINITE);
            tt.play();
        }

        for (int i = 0; i < 8; i++) {
            Rectangle bar = new Rectangle(10, 40 + Math.random() * 40);

            bar.setArcWidth(8);
            bar.setArcHeight(8);

            bar.setFill(Color.web("#7c3aed"));
            bar.setOpacity(0.10);

            bar.setLayoutX(Math.random() * 1200);
            bar.setLayoutY(Math.random() * 700);

            bgPane.getChildren().add(bar);

            TranslateTransition tt =
                    new TranslateTransition(Duration.seconds(2.5 + Math.random() * 2), bar);

            tt.setByY(35);
            tt.setAutoReverse(true);
            tt.setCycleCount(Animation.INDEFINITE);
            tt.play();
        }
    }

    private void playIntro() {
        titleLabel.setOpacity(0);
        subLabel.setOpacity(0);

        titleLabel.setScaleX(0.7);
        titleLabel.setScaleY(0.7);

        titleLabel.setEffect(new DropShadow(25, Color.web("#00d4ff")));

        FadeTransition titleFade =
                new FadeTransition(Duration.millis(700), titleLabel);
        titleFade.setFromValue(0);
        titleFade.setToValue(1);

        ScaleTransition zoom =
                new ScaleTransition(Duration.millis(1000), titleLabel);
        zoom.setFromX(0.7);
        zoom.setFromY(0.7);
        zoom.setToX(1.0);
        zoom.setToY(1.0);

        FadeTransition subFade =
                new FadeTransition(Duration.millis(600), subLabel);
        subFade.setFromValue(0);
        subFade.setToValue(1);
        subFade.setDelay(Duration.millis(500));

        ScaleTransition pulse =
                new ScaleTransition(Duration.millis(500), titleLabel);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(1.05);
        pulse.setToY(1.05);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(2);
        pulse.setDelay(Duration.seconds(1));

        PauseTransition wait = new PauseTransition(Duration.seconds(2.4));

        FadeTransition fadeOut =
                new FadeTransition(Duration.millis(700), rootPane);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);

        fadeOut.setOnFinished(e -> Launcher.switchScene("hello-view.fxml"));

        titleFade.play();
        zoom.play();
        subFade.play();
        pulse.play();

        wait.setOnFinished(e -> fadeOut.play());
        wait.play();
    }
}