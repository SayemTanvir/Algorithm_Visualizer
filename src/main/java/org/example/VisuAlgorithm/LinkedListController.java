package org.example.VisuAlgorithm;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.*;

public class LinkedListController {

    @FXML private Label statusLabel;
    @FXML private Pane backgroundAnimationPane;

    private final List<AnimationGroup> dsaGroups = new ArrayList<>();
    private Timeline timeline;

    @FXML
    public void initialize() {
        setupDSAFloatingBackground();
    }

    private void setupDSAFloatingBackground() {
        if (backgroundAnimationPane == null) return;
        backgroundAnimationPane.getChildren().clear();
        dsaGroups.clear();

        // Add floating DSA elements (same as home page, but adjusted for this view)
        dsaGroups.add(AnimationGroup.randTree(155, 90, 2));
        dsaGroups.add(AnimationGroup.randGraph(1080, 110, 5, 8, 43));
        dsaGroups.add(AnimationGroup.stack(320, 480, 5));
        dsaGroups.add(AnimationGroup.queue(920, 465, 7));
        dsaGroups.add(AnimationGroup.randTree(630, 206, 3));
        dsaGroups.add(AnimationGroup.randGraph(650, 370, 6, 8, 50));
        dsaGroups.add(AnimationGroup.queue(640, 560, 4));
        dsaGroups.add(AnimationGroup.randGraph(350, 180, 3, 5, 32));

        for (AnimationGroup g : dsaGroups)
            backgroundAnimationPane.getChildren().addAll(g.allNodes);

        if (timeline != null) timeline.stop();
        timeline = new Timeline(new KeyFrame(Duration.millis(28), e -> animateAll()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void animateAll() {
        double t = System.currentTimeMillis() / 700.0;
        for (AnimationGroup g : dsaGroups) g.animate(t);
    }

    @FXML
    private void onBack() {
        Launcher.switchScene("hello-view.fxml");
    }

    @FXML
    private void gotoSinglyLinkedList(MouseEvent event) {
        Launcher.switchScene("singly-linked-list-view.fxml");
    }

    @FXML
    private void gotoDoublyLinkedList(MouseEvent event) {
        Launcher.switchScene("doubly-linked-list-view.fxml");
    }

    @FXML
    private void gotoCircularLinkedList(MouseEvent event) {
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

    // ==================== Floating DSA Animation Code ====================
    private static class AnimationGroup {
        List<javafx.scene.Node> allNodes = new ArrayList<>();
        interface Animated { void run(double t); }
        Animated animate = t -> {};

        static AnimationGroup randGraph(double cx, double cy, int minN, int maxN, double R) {
            int N = minN + new Random().nextInt(maxN - minN + 1);
            List<Circle> nodes = new ArrayList<>();
            List<Line> edges = new ArrayList<>();
            double theta0 = Math.random() * 6.28;
            for (int i = 0; i < N; i++) {
                double ang = theta0 + 2 * Math.PI * i / N;
                double nx = cx + Math.cos(ang) * R * (1 + Math.random() * 0.28);
                double ny = cy + Math.sin(ang) * R * 0.7 * (1 + Math.random() * 0.28);
                Circle c = new Circle(nx, ny, 15 + Math.random() * 6, Color.web("#80e6fa" + (Math.random() > 0.6 ? "b2" : "68")));
                c.setStroke(Color.web("#5bcafe99"));
                c.setStrokeWidth(1.5 + Math.random() * 1.25);
                c.setEffect(new DropShadow(14, Color.web("#3d95bf66")));
                nodes.add(c);
            }
            for (int i = 0; i < N; i++) for (int j = i + 1; j < N; j++)
                if (Math.random() < 0.44)
                    edges.add(makeEdge(nodes.get(i), nodes.get(j), Color.web("#a3dfff26"), 1.7 + Math.random() * 1.5));
            AnimationGroup g = new AnimationGroup();
            g.allNodes.addAll(edges);
            g.allNodes.addAll(nodes);
            double floatMag = 13 + Math.random() * 11, freq = 0.13 + Math.random() * 0.15;
            g.animate = t -> {
                for (int i = 0; i < nodes.size(); i++) {
                    double phase = t * freq + i * 1.27;
                    double bx = cx + Math.cos(theta0 + 2 * Math.PI * i / N) * R;
                    double by = cy + Math.sin(theta0 + 2 * Math.PI * i / N) * R * 0.68;
                    nodes.get(i).setCenterX(bx + Math.sin(phase) * floatMag);
                    nodes.get(i).setCenterY(by + Math.cos(phase * 1.11) * floatMag * 0.6);
                }
                for (Line l : edges) updateEdge(l);
            };
            return g;
        }

        static AnimationGroup randTree(double cx, double cy, int layers) {
            List<Circle> nodes = new ArrayList<>();
            List<Line> edges = new ArrayList<>();
            double yStep = 36, xSpread = 62;
            List<Circle> prevLayer = new ArrayList<>(), currLayer = new ArrayList<>();
            Circle root = new Circle(cx, cy, 16, Color.web("#ffe28b88"));
            root.setStroke(Color.web("#c6a65599"));
            root.setStrokeWidth(2);
            root.setEffect(new DropShadow(18, Color.web("#eec97477")));
            nodes.add(root);
            prevLayer.add(root);
            for (int l = 1; l <= layers; l++) {
                currLayer.clear();
                int count = 1 << l, parentCount = 1 << (l - 1);
                for (int i = 0; i < count; i++) {
                    double px = cx + (i - parentCount / 2.0 + 0.5) * xSpread * (1.32 - Math.abs(l - layers) * 0.14);
                    double py = cy + l * yStep * 1.23;
                    Circle c = new Circle(px, py, 13, Color.web("#ffeaaad8"));
                    c.setStroke(Color.web("#c1a66788"));
                    c.setStrokeWidth(1.1);
                    c.setEffect(new DropShadow(11, Color.web("#ffdaae99")));
                    nodes.add(c);
                    currLayer.add(c);
                    Circle p = prevLayer.get(i / 2);
                    edges.add(makeEdge(c, p, Color.web("#ffe7b955"), 1.58));
                }
                prevLayer = new ArrayList<>(currLayer);
            }
            AnimationGroup g = new AnimationGroup();
            g.allNodes.addAll(edges);
            g.allNodes.addAll(nodes);
            double floatMag = 11 + Math.random() * 7, freq = 0.10 + Math.random() * 0.10;
            g.animate = t -> {
                double dy = Math.sin(t * freq) * floatMag;
                int idx = 0, n = 1;
                root.setCenterY(cy + dy * 0.51);
                for (int l = 1; l <= layers; l++) {
                    int count = 1 << l;
                    for (int i = 0; i < count; i++) {
                        double px = cx + (i - (1 << (l - 1)) / 2.0 + 0.5) * xSpread * (1.32 - Math.abs(l - layers) * 0.14);
                        double py = cy + l * yStep * 1.23 + dy * Math.sin(t * 0.62 + i * 0.5 + l);
                        nodes.get(idx + n).setCenterX(px);
                        nodes.get(idx + n).setCenterY(py);
                    }
                    idx += count;
                }
                for (Line l : edges) updateEdge(l);
            };
            return g;
        }

        static AnimationGroup stack(double x, double y, int n) {
            List<Rectangle> rects = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Rectangle r = new Rectangle(x - 28, y - i * 32, 56, 26);
                r.setArcWidth(11);
                r.setArcHeight(11);
                r.setFill(Color.web("#89f0fc" + (i == n - 1 ? "a5" : "55")));
                r.setStroke(Color.web("#47cddb"));
                r.setStrokeWidth(1.4);
                r.setEffect(new DropShadow(7, Color.web("#21dee299")));
                rects.add(r);
            }
            AnimationGroup g = new AnimationGroup();
            g.allNodes.addAll(rects);
            double freq = 0.12 + Math.random() * 0.09, mag = 7 + Math.random() * 5;
            g.animate = t -> {
                for (int i = 0; i < n; i++) {
                    rects.get(i).setTranslateX(Math.sin(t * freq + i) * mag);
                    rects.get(i).setTranslateY(Math.cos(t * freq * 0.83 + i * 0.38) * mag * 0.67);
                }
            };
            return g;
        }

        static AnimationGroup queue(double x, double y, int n) {
            List<Rectangle> rects = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Rectangle r = new Rectangle(x + i * 34, y, 30, 24);
                r.setArcWidth(9);
                r.setArcHeight(9);
                r.setFill(Color.web("#b8beff" + (i == 0 ? "a0" : "44")));
                r.setStroke(Color.web("#8396e7"));
                r.setStrokeWidth(1.2);
                r.setEffect(new DropShadow(8, Color.web("#b8beffbb")));
                rects.add(r);
            }
            AnimationGroup g = new AnimationGroup();
            g.allNodes.addAll(rects);
            double freq = 0.14 + Math.random() * 0.06, mag = 6 + Math.random() * 5;
            g.animate = t -> {
                for (int i = 0; i < n; i++)
                    rects.get(i).setTranslateY(Math.sin(t * freq + i * 0.6) * mag);
            };
            return g;
        }

        void animate(double t) { animate.run(t); }

        static Line makeEdge(Circle a, Circle b, Color color, double width) {
            Line l = new Line();
            l.setStroke(color);
            l.setStrokeWidth(width);
            l.setViewOrder(100);
            l.setUserData(new Circle[]{a, b});
            return l;
        }

        static void updateEdge(Line l) {
            Circle[] pair = (Circle[]) l.getUserData();
            l.setStartX(pair[0].getCenterX());
            l.setStartY(pair[0].getCenterY());
            l.setEndX(pair[1].getCenterX());
            l.setEndY(pair[1].getCenterY());
        }
    }
}