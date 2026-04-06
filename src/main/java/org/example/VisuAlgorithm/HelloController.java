package org.example.VisuAlgorithm;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.util.Duration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HelloController {

    // ---- Navigation ----
    @FXML private void gotoSorting(MouseEvent event) throws IOException { Launcher.switchScene("sorting-view.fxml"); }
    @FXML private void gotoArray(MouseEvent event) throws IOException { Launcher.switchScene("array-view.fxml"); }
    @FXML private void gotoLinkedList(MouseEvent event) throws IOException { Launcher.switchScene("linked-list-view.fxml"); }
    @FXML private void gotoStack(MouseEvent event) throws IOException { Launcher.switchScene("stack-view.fxml"); }
    @FXML private void gotoQueue(MouseEvent event) throws IOException { Launcher.switchScene("queue-view.fxml"); }
    @FXML private void gotoGraph(MouseEvent event) throws IOException { Launcher.switchScene("graph-view.fxml"); }
    @FXML private void gotoBST(MouseEvent event) throws IOException { Launcher.switchScene("bst-view.fxml"); }

    // ---- Tree Pathfinding Background Animation ----
    @FXML Pane backgroundAnimationPane;

    private Timeline drawTimeline;
    private final double CELL_SIZE = 35.0;
    private final List<TreeWalker> walkers = new ArrayList<>();
    private final Random globalRng = new Random();

    @FXML
    public void initialize() {
        Platform.runLater(this::setupBackgroundGrid);
    }

    private void setupBackgroundGrid() {
        if (backgroundAnimationPane == null) return;

        double width = backgroundAnimationPane.getWidth() > 0 ? backgroundAnimationPane.getWidth() : 1000;
        double height = backgroundAnimationPane.getHeight() > 0 ? backgroundAnimationPane.getHeight() : 650;

        backgroundAnimationPane.getChildren().clear();
        walkers.clear();

        // 1. Draw the background dot grid (Slightly larger dots: 2.0 radius)
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
            boolean startLeftToRight = (i % 2 == 0);
            int startDelayTicks = globalRng.nextInt(30) + 1;
            TreeWalker walker = new TreeWalker(startLeftToRight, themeColors[i], startDelayTicks);
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

    // =========================================================================
    //  TREE SPREADING AGENT
    // =========================================================================
    private class TreeWalker {
        Group treeGroup;
        List<GridNode> frontier;
        boolean[][] visited;

        boolean leftToRight;
        boolean fading;
        Color themeColor;
        int delayTicks;

        int cols, rows;
        int endCol, endRow;

        TreeWalker(boolean startLeftToRight, Color color, int delayTicks) {
            this.leftToRight = startLeftToRight;
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

            int startCol = leftToRight ? 1 : cols - 2;
            int startRow = leftToRight ? 1 : rows - 2;
            endCol = leftToRight ? cols - 2 : 1;
            endRow = leftToRight ? rows - 2 : 1;

            frontier.add(new GridNode(startCol, startRow));
            visited[startCol][startRow] = true;

            // BIGGER ROOT NODE: Changed radius from 4 to 7
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

            if (frontier == null) {
                spawn();
                return;
            }

            if (fading) return;

            List<GridNode> nextFrontier = new ArrayList<>();
            boolean reachedTarget = false;

            for (GridNode node : frontier) {
                int dirX = leftToRight ? 1 : -1;
                int dirY = leftToRight ? 1 : -1;

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

            // THICKER BRANCH LINE: Changed stroke width from 2.0 to 3.0
            Line branch = new Line(parent.c * CELL_SIZE, parent.r * CELL_SIZE, nc * CELL_SIZE, nr * CELL_SIZE);
            branch.setStroke(themeColor.deriveColor(0, 1, 1, 0.4));
            branch.setStrokeWidth(3.0);

            // BIGGER TIP NODE: Changed radius from 2.5 to 4.5
            Circle tip = new Circle(nc * CELL_SIZE, nr * CELL_SIZE, 4.5, themeColor);

            treeGroup.getChildren().addAll(branch, tip);

            return (nc == endCol || nr == endRow);
        }

        void fadeOutAndRestart() {
            FadeTransition fade = new FadeTransition(Duration.millis(1200), treeGroup);
            fade.setToValue(0);

            fade.setOnFinished(e -> {
                backgroundAnimationPane.getChildren().remove(treeGroup);
                leftToRight = !leftToRight;
                delayTicks = 20 + globalRng.nextInt(30);
            });

            fade.play();
        }
    }
}