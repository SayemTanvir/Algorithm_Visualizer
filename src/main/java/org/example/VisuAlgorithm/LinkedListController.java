package org.example.VisuAlgorithm;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LinkedListController {

    @FXML private Label statusLabel;
    @FXML private Pane backgroundAnimationPane;

    private Timeline drawTimeline;
    private final double CELL_SIZE = 35.0;
    private final List<TreeWalker> walkers = new ArrayList<>();
    private final Random globalRng = new Random();

    @FXML
    public void initialize() {
        Platform.runLater(this::setupBackgroundGrid);
    }

    // ==================== Pathfinding Background Animation Code ====================

    private void setupBackgroundGrid() {
        if (backgroundAnimationPane == null) return;

        double width = backgroundAnimationPane.getWidth() > 0 ? backgroundAnimationPane.getWidth() : 1000;
        double height = backgroundAnimationPane.getHeight() > 0 ? backgroundAnimationPane.getHeight() : 650;

        backgroundAnimationPane.getChildren().clear();
        walkers.clear();

        // 1. Draw the background dot grid (Larger dots: 2.0 radius)
        for (double x = CELL_SIZE; x < width; x += CELL_SIZE) {
            for (double y = CELL_SIZE; y < height; y += CELL_SIZE) {
                Circle gridDot = new Circle(x, y, 2.0, Color.web("#ffffff", 0.05));
                backgroundAnimationPane.getChildren().add(gridDot);
            }
        }

        // 2. Create multiple independent tree builders
        Color[] themeColors = {
                Color.web("#38bdf8"), // Light Blue
                Color.web("#a78bfa"), // Purple
                Color.web("#2dd4bf")  // Teal
        };

        for (int i = 0; i < 3; i++) {
            boolean startTopRight = (i % 2 == 0); // Alternate starting positions
            int startDelayTicks = globalRng.nextInt(30);

            TreeWalker walker = new TreeWalker(startTopRight, themeColors[i], startDelayTicks);
            walkers.add(walker);
        }

        // 3. Main Animation Loop
        if (drawTimeline != null) drawTimeline.stop();
        drawTimeline = new Timeline(new KeyFrame(Duration.millis(50), e -> {
            for (TreeWalker w : walkers) w.step();
        }));
        drawTimeline.setCycleCount(Timeline.INDEFINITE);
        drawTimeline.play();
    }

    private static class GridNode {
        int c, r;
        GridNode(int c, int r) { this.c = c; this.r = r; }
    }

    private class TreeWalker {
        Group treeGroup;
        List<GridNode> frontier;
        boolean[][] visited;

        boolean startTopRight;
        boolean fading;
        Color themeColor;
        int delayTicks;

        int cols, rows;
        int endCol, endRow;

        TreeWalker(boolean startTopRight, Color color, int delayTicks) {
            this.startTopRight = startTopRight;
            this.themeColor = color;
            this.delayTicks = delayTicks;
            this.fading = false;
        }

        void spawn() {
            fading = false;
            double w = backgroundAnimationPane.getWidth();
            double h = backgroundAnimationPane.getHeight();

            cols = (int) (w / CELL_SIZE);
            rows = (int) (h / CELL_SIZE);

            visited = new boolean[cols + 2][rows + 2];
            frontier = new ArrayList<>();
            treeGroup = new Group();

            // Set coordinates for Top-Right to Bottom-Left pathfinding
            int startCol = startTopRight ? cols : 1;
            int startRow = startTopRight ? 1 : rows ;
            endCol = startTopRight ? 1 : cols - 2;
            endRow = startTopRight ? rows - 2 : 1;

            frontier.add(new GridNode(startCol, startRow));
            visited[startCol][startRow] = true;

            // BIGGER ROOT NODE
            Circle root = new Circle(startCol * CELL_SIZE, startRow * CELL_SIZE, 7, themeColor);
            treeGroup.getChildren().add(root);

            backgroundAnimationPane.getChildren().add(treeGroup);
        }

        void step() {
            if (delayTicks > 0) {
                delayTicks--;
                if (delayTicks == 0) spawn();
                return;
            }

            if (fading) return;

            List<GridNode> nextFrontier = new ArrayList<>();
            boolean reachedTarget = false;

            for (GridNode node : frontier) {
                // If starting Top-Right, move Left (-1) and Down (1)
                // If starting Bottom-Left, move Right (1) and Up (-1)
                int dirX = startTopRight ? -1 : 1;
                int dirY = startTopRight ? 1 : -1;

                if (globalRng.nextDouble() < 0.6) {
                    if (tryGrow(node, node.c + dirX, node.r, nextFrontier)) reachedTarget = true;
                }

                if (globalRng.nextDouble() < 0.6) {
                    if (tryGrow(node, node.c, node.r + dirY, nextFrontier)) reachedTarget = true;
                }

                if (nextFrontier.isEmpty() && globalRng.nextDouble() < 0.8) {
                    if (globalRng.nextBoolean()) {
                        if (tryGrow(node, node.c + dirX, node.r, nextFrontier)) reachedTarget = true;
                    } else {
                        if (tryGrow(node, node.c, node.r + dirY, nextFrontier)) reachedTarget = true;
                    }
                }
            }

            frontier = nextFrontier;

            if (reachedTarget || frontier.isEmpty()) {
                fading = true;
                fadeOutAndRestart();
            }
        }

        private boolean tryGrow(GridNode parent, int nc, int nr, List<GridNode> nextFrontier) {
            if (nc <= 0 || nc >= cols || nr <= 0 || nr >= rows) return false;
            if (visited[nc][nr]) return false;

            visited[nc][nr] = true;
            nextFrontier.add(new GridNode(nc, nr));

            // THICKER BRANCH LINE
            Line branch = new Line(parent.c * CELL_SIZE, parent.r * CELL_SIZE, nc * CELL_SIZE, nr * CELL_SIZE);
            branch.setStroke(themeColor.deriveColor(0, 1, 1, 0.4));
            branch.setStrokeWidth(3.0);

            // BIGGER TIP NODE
            Circle tip = new Circle(nc * CELL_SIZE, nr * CELL_SIZE, 4.5, themeColor);

            treeGroup.getChildren().addAll(branch, tip);

            return (nc == endCol || nr == endRow);
        }

        void fadeOutAndRestart() {
            FadeTransition fade = new FadeTransition(Duration.millis(1200), treeGroup);
            fade.setToValue(0);

            fade.setOnFinished(e -> {
                backgroundAnimationPane.getChildren().remove(treeGroup);
                startTopRight = !startTopRight; // Swap direction
                delayTicks = 20 + globalRng.nextInt(30);
            });

            fade.play();
        }
    }

    // ==================== Navigation Code ====================

    @FXML
    private void onBack() {
        if (drawTimeline != null) drawTimeline.stop(); // Stop animation before leaving
        Launcher.switchScene("hello-view.fxml");
    }

    @FXML
    private void gotoSinglyLinkedList(MouseEvent event) {
        if (drawTimeline != null) drawTimeline.stop();
        Launcher.switchScene("singly-linked-list-view.fxml");
    }

    @FXML
    private void gotoDoublyLinkedList(MouseEvent event) {
        if (drawTimeline != null) drawTimeline.stop();
        Launcher.switchScene("doubly-linked-list-view.fxml");
    }

    @FXML
    private void gotoCircularLinkedList(MouseEvent event) {
        if (drawTimeline != null) drawTimeline.stop();
        Launcher.switchScene("circular-linked-list-view.fxml");
    }

    private void openView(String fxmlName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlName));
            Parent root = loader.load();
            Stage stage = getCurrentStage();
            if (stage != null) {
                Scene scene = new Scene(root);
                scene.getStylesheets().add(getClass().getResource("/org/example/VisuAlgorithm/styles/main.css").toExternalForm());
                stage.setScene(scene);
                stage.show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Stage getCurrentStage() {
        if (statusLabel == null || statusLabel.getScene() == null) return null;
        return (Stage) statusLabel.getScene().getWindow();
    }
}