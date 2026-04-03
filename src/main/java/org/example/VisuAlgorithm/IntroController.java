package org.example.VisuAlgorithm;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Random;

public class IntroController {

    @FXML private Pane backgroundPane;
    @FXML private ProgressBar loadingBar;
    @FXML private Label loadingLabel;

    private Timeline backgroundTimeline;
    private Timeline loadingTimeline;

    @FXML
    public void initialize() {
        setupAnimatedBackground();
        setupLoadingAnimation();

    }

    // ============= ANIMATED BACKGROUND =============
    private void setupAnimatedBackground() {
        if (backgroundPane == null) return;

        // Create 15-20 floating particles
        Random rand = new Random();
        for (int i = 0; i < 18; i++) {
            double x = rand.nextDouble() * 1400;
            double y = rand.nextDouble() * 700;
            double size = 2 + rand.nextDouble() * 4;
            double duration = 8 + rand.nextDouble() * 6;

            Circle particle = new Circle(x, y, size);
            particle.setFill(Color.web(getRandomGradientColor()));
            particle.setOpacity(0.3 + rand.nextDouble() * 0.5);
            particle.setEffect(new DropShadow(8, Color.web(getRandomGradientColor()).darker()));

            backgroundPane.getChildren().add(particle);

            // Animate floating motion
            animateParticle(particle, duration);
        }
    }

    private void animateParticle(Circle particle, double duration) {
        Random rand = new Random();
        double endX = rand.nextDouble() * 1400;
        double endY = rand.nextDouble() * 700;
        double rotationAngle = rand.nextDouble() * 360;

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(particle.centerXProperty(), particle.getCenterX()),
                        new KeyValue(particle.centerYProperty(), particle.getCenterY()),
                        new KeyValue(particle.opacityProperty(), particle.getOpacity())
                ),
                new KeyFrame(Duration.seconds(duration),
                        new KeyValue(particle.centerXProperty(), endX),
                        new KeyValue(particle.centerYProperty(), endY),
                        new KeyValue(particle.opacityProperty(), 0.1 + Math.random() * 0.4)
                )
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.setAutoReverse(true);
        timeline.play();
    }

    // ============= LOADING ANIMATION =============
    private void setupLoadingAnimation() {
        // Progress bar animation
        loadingTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(loadingBar.progressProperty(), 0)),
                new KeyFrame(Duration.seconds(3.5), new KeyValue(loadingBar.progressProperty(), 1.0))
        );
        loadingTimeline.play();

        // Loading text animation
        Timeline textTimeline = new Timeline();
        String[] texts = {
                "Loading...",
                "Loading.",
                "Loading..",
                "Loading..."
        };

        for (int i = 0; i < texts.length; i++) {
            final int index = i;
            textTimeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(0.4 * i),
                            e -> loadingLabel.setText(texts[index])
                    )
            );
        }
        textTimeline.setCycleCount(Timeline.INDEFINITE);
        textTimeline.play();

        // After 4 seconds, transition to home
        Timeline transitionTimeline = new Timeline(
                new KeyFrame(Duration.seconds(4), e -> transitionToHome())
        );
        transitionTimeline.play();
    }

    // ============= TRANSITION TO HOME =============
    private void transitionToHome() {
        try {
            // Fade out intro
            Timeline fadeOut = new Timeline(
                    new KeyFrame(Duration.seconds(0.6),
                            new KeyValue(backgroundPane.opacityProperty(), 0),
                            new KeyValue(loadingBar.opacityProperty(), 0),
                            new KeyValue(loadingLabel.opacityProperty(), 0)
                    )
            );

            fadeOut.setOnFinished(e -> {
                try {
//                    FXMLLoader loader = new FXMLLoader(getClass().getResource("hello-view.fxml"));
//                    Parent root = loader.load();
//                    Stage stage = (Stage) backgroundPane.getScene().getWindow();
//                    Scene scene = new Scene(root);
//                    scene.getStylesheets().add(getClass().getResource("/org/example/VisuAlgorithm/styles/main.css").toExternalForm());
//                    stage.setScene(scene);
                    Launcher.switchScene("hello-view.fxml");

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            fadeOut.play();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ============= HELPER METHODS =============
    private String getRandomGradientColor() {
        String[] colors = {
                "#00d4ff",  // Cyan
                "#7c3aed",  // Purple
                "#ff006e",  // Pink
                "#00ff88",  // Green
                "#ffa500",  // Orange
                "#1e90ff"   // Dodger Blue
        };
        Random rand = new Random();
        return colors[rand.nextInt(colors.length)];
    }
}