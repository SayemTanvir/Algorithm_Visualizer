package org.example.VisuAlgorithm;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.*;

public class HelloController {
    // ---- Navigation (assumes all FXML files are in the same Java package as this class) ----
    @FXML private void gotoSorting(MouseEvent event) throws IOException {
        Launcher.switchScene("sorting-view.fxml");
    }
    @FXML private void gotoArray(MouseEvent event) throws IOException {
        Launcher.switchScene("array-view.fxml");
    }
    @FXML private void gotoLinkedList(MouseEvent event) throws IOException {
        Launcher.switchScene("linked-list-view.fxml");
    }
    @FXML private void gotoStack(MouseEvent event) throws IOException {
        Launcher.switchScene("stack-view.fxml");
    }
    @FXML private void gotoQueue(MouseEvent event) throws IOException {
        Launcher.switchScene("queue-view.fxml");
    }
    @FXML private void gotoGraph(MouseEvent event) throws IOException {
        Launcher.switchScene("graph-view.fxml");
    }
    @FXML private void gotoBST(MouseEvent event) throws IOException {
        Launcher.switchScene("bst-view.fxml");
    }

    private void switchScene(String fxmlName, MouseEvent event) throws IOException {
        URL url = getClass().getResource(fxmlName);
        if (url == null) {
            System.err.println("FXML not found: " + fxmlName);
            return;
        }
        FXMLLoader loader = new FXMLLoader(url);
        Parent root = loader.load();
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        Scene scene = new Scene(root);
        // Only include this if you actually have main.css in your package
        URL cssUrl = getClass().getResource("/org/example/VisuAlgorithm/styles/main.css");
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
        stage.setScene(scene);
    }

    // ---- Background DSA Animation ----
    @FXML Pane backgroundAnimationPane;
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

        // Top left
        dsaGroups.add(AnimationGroup.randTree(155, 90, 2));
        dsaGroups.add(AnimationGroup.randTree(240, 150, 3));
        dsaGroups.add(AnimationGroup.randGraph(80, 60, 3, 6, 36));
        dsaGroups.add(AnimationGroup.randGraph(150, 230, 4, 7, 38));
        // Top right
        dsaGroups.add(AnimationGroup.randGraph(1080, 110, 5, 8, 43));
        dsaGroups.add(AnimationGroup.randTree(950, 90, 2));
        dsaGroups.add(AnimationGroup.randTree(1150, 180, 2));
        dsaGroups.add(AnimationGroup.randGraph(1220, 120, 5, 7, 32));
        // Center top/mid
        dsaGroups.add(AnimationGroup.randTree(650, 105, 3));
        dsaGroups.add(AnimationGroup.randTree(430, 180, 2));
        dsaGroups.add(AnimationGroup.randGraph(770, 85, 4, 7, 34));
        dsaGroups.add(AnimationGroup.randGraph(680, 240, 6, 10, 55));
        // Left mid/bottom
        dsaGroups.add(AnimationGroup.randGraph(130, 380, 4, 7, 32));
        dsaGroups.add(AnimationGroup.randTree(165, 400, 2));
        // Center
        dsaGroups.add(AnimationGroup.randTree(630, 326, 3));
        dsaGroups.add(AnimationGroup.randGraph(650, 370, 6, 8, 50));
        // Right mid/bottom
        dsaGroups.add(AnimationGroup.randGraph(1080, 420, 4, 8, 39));
        dsaGroups.add(AnimationGroup.randTree(1210, 440, 2));
        // Lower area (stacks, queues, etc as before)
        dsaGroups.add(AnimationGroup.stack(300, 540, 5));
        dsaGroups.add(AnimationGroup.queue(1020, 510, 6));
        dsaGroups.add(AnimationGroup.queue(805, 570, 5));
        dsaGroups.add(AnimationGroup.stack(1230, 600, 3));

        for (AnimationGroup g : dsaGroups)
            backgroundAnimationPane.getChildren().addAll(g.allNodes);

        if (timeline != null) timeline.stop();
        timeline = new Timeline(new KeyFrame(Duration.millis(28), e -> animateAll()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void animateAll() {
        double t = System.currentTimeMillis()/500.0;
        for (AnimationGroup g : dsaGroups) g.animate(t);
    }

    // ---- Floating Shapes Implementation ----
    private static class AnimationGroup {
        List<Node> allNodes = new ArrayList<>();
        interface Animated { void run(double t); }
        Animated animate = t -> {};

        // Floating disconnected graph
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

        // Floating tree
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

        // Stack (floating)
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

        // Queue (floating)
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
//package org.example.VisuAlgorithm;
//
//import javafx.animation.KeyFrame;
//import javafx.animation.Timeline;
//import javafx.fxml.FXML;
//import javafx.scene.*;
//import javafx.scene.effect.DropShadow;
//import javafx.scene.input.MouseEvent;
//import javafx.scene.layout.Pane;
//import javafx.scene.paint.Color;
//import javafx.scene.shape.*;
//import javafx.util.Duration;
//
//import java.io.IOException;
//import java.util.*;
//
//public class HelloController {
//
//    // ── Navigation ────────────────────────────────────────────────────────────
//    @FXML private void gotoSorting(MouseEvent event)    throws IOException { Launcher.switchScene("sorting-view.fxml"); }
//    @FXML private void gotoArray(MouseEvent event)      throws IOException { Launcher.switchScene("array-view.fxml"); }
//    @FXML private void gotoLinkedList(MouseEvent event) throws IOException { Launcher.switchScene("linked-list-view.fxml"); }
//    @FXML private void gotoStack(MouseEvent event)      throws IOException { Launcher.switchScene("stack-view.fxml"); }
//    @FXML private void gotoQueue(MouseEvent event)      throws IOException { Launcher.switchScene("queue-view.fxml"); }
//    @FXML private void gotoGraph(MouseEvent event)      throws IOException { Launcher.switchScene("graph-view.fxml"); }
//    @FXML private void gotoBST(MouseEvent event)        throws IOException { Launcher.switchScene("bst-view.fxml"); }
//
//    // ── Background animation ──────────────────────────────────────────────────
//    @FXML Pane backgroundAnimationPane;
//    private final List<AnimationGroup> dsaGroups = new ArrayList<>();
//    private Timeline timeline;
//
//    @FXML
//    public void initialize() {
//        setupBackground();
//    }
//
//    private void setupBackground() {
//        if (backgroundAnimationPane == null) return;
//
//        // FIX 1: Stop the old timeline BEFORE clearing groups,
//        // so the old ticker never fires on a half-rebuilt state.
//        if (timeline != null) timeline.stop();
//
//        backgroundAnimationPane.getChildren().clear();
//        dsaGroups.clear();
//
//        // ── Four corners — small trees ────────────────────────────────────────
//        dsaGroups.add(AnimationGroup.tree(110,  90,  2, 0.55));   // top-left
//        dsaGroups.add(AnimationGroup.tree(890,  80,  2, 0.50));   // top-right
//        dsaGroups.add(AnimationGroup.tree( 90, 520,  2, 0.45));   // bottom-left
//        dsaGroups.add(AnimationGroup.tree(910, 510,  2, 0.48));   // bottom-right
//
//        // ── Left & right edges — sparse graph rings ───────────────────────────
//        dsaGroups.add(AnimationGroup.graph( 70, 300, 5, 80, 0.40));  // left mid
//        dsaGroups.add(AnimationGroup.graph(930, 310, 5, 80, 0.38));  // right mid
//
//        // ── Top edge — two small graphs ───────────────────────────────────────
//        dsaGroups.add(AnimationGroup.graph(300,  70, 4, 60, 0.35));  // top-left area
//        dsaGroups.add(AnimationGroup.graph(700,  75, 4, 60, 0.35));  // top-right area
//
//        // ── Bottom edge — two small graphs ───────────────────────────────────
//        dsaGroups.add(AnimationGroup.graph(280, 580, 4, 55, 0.32));  // bottom-left
//        dsaGroups.add(AnimationGroup.graph(720, 575, 4, 55, 0.32));  // bottom-right
//
//        // ── Accent trees — slightly inward from the mid-edges ─────────────────
//        dsaGroups.add(AnimationGroup.tree(200, 260, 2, 0.30));
//        dsaGroups.add(AnimationGroup.tree(800, 270, 2, 0.30));
//
//        for (AnimationGroup g : dsaGroups)
//            backgroundAnimationPane.getChildren().addAll(g.nodes);
//
//        // 40 ms tick → smooth but cheap
//        timeline = new Timeline(new KeyFrame(Duration.millis(40), e -> {
//            double t = System.currentTimeMillis() / 300.0;
//            for (AnimationGroup g : dsaGroups) g.tick(t);
//        }));
//        timeline.setCycleCount(Timeline.INDEFINITE);
//        timeline.play();
//    }
//
//    // ── AnimationGroup ────────────────────────────────────────────────────────
//
//    private static class AnimationGroup {
//
//        /** All JavaFX nodes that belong to this group (edges first, nodes on top). */
//        final List<Node> nodes = new ArrayList<>();
//
//        /** Per-tick update lambda. */
//        interface Ticker { void tick(double t); }
//        Ticker ticker = t -> {};
//
//        void tick(double t) { ticker.tick(t); }
//
//        // ── Palette ───────────────────────────────────────────────────────────
//        private static Color nodeColor(double alpha)  { return Color.web("#7dd3fc", alpha); }
//        private static Color strokeColor(double alpha){ return Color.web("#38bdf8", alpha); }
//        private static Color edgeColor(double alpha)  { return Color.web("#bae6fd", alpha * 0.45); }
//        private static Color glowColor()              { return Color.web("#0ea5e9", 0.25); }
//
//        // ── Tree factory ─────────────────────────────────────────────────────
//        static AnimationGroup tree(double cx, double cy, int layers, double alpha) {
//            AnimationGroup g = new AnimationGroup();
//
//            double nodeR   = 7.0;
//            double yStep   = 38.0;
//            double xSpread = 44.0;
//
//            List<Circle>   circles = new ArrayList<>();
//            List<Line>     edges   = new ArrayList<>();
//            List<double[]> base    = new ArrayList<>();
//
//            // root
//            Circle root = makeCircle(cx, cy, nodeR, alpha);
//            circles.add(root);
//            base.add(new double[]{cx, cy});
//
//            List<Circle> prevRow = new ArrayList<>();
//            prevRow.add(root);
//
//            for (int l = 1; l <= layers; l++) {
//                List<Circle> curRow = new ArrayList<>();
//                int count = 1 << l;
//                double half = count / 2.0;
//
//                for (int i = 0; i < count; i++) {
//                    double bx = cx + (i - half + 0.5) * xSpread * Math.pow(1.2, layers - l);
//                    double by = cy + l * yStep;
//                    Circle c = makeCircle(bx, by, nodeR * 0.85, alpha * 0.88);
//                    circles.add(c);
//                    base.add(new double[]{bx, by});
//                    curRow.add(c);
//
//                    // edge to parent — FIX 2: guard index before accessing prevRow
//                    int parentIdx = i / 2;
//                    if (parentIdx < prevRow.size()) {
//                        Circle parent = prevRow.get(parentIdx);
//                        Line edge = new Line();
//                        edge.setStroke(edgeColor(alpha));
//                        edge.setStrokeWidth(1.1);
//                        edge.setUserData(new Circle[]{c, parent});
//                        edges.add(edge);
//                    }
//                }
//                prevRow = curRow;
//            }
//
//            g.nodes.addAll(edges);
//            g.nodes.addAll(circles);
//
//            double freq = 0.08 + new Random().nextDouble() * 0.06;
//            double mag  = 8   + new Random().nextDouble() * 5;
//
//            g.ticker = t -> {
//                double dy = Math.sin(t * freq) * mag;
//                double dx = Math.cos(t * freq * 0.7) * mag * 0.5;
//                // FIX 3: guard both lists to prevent IndexOutOfBoundsException
//                int size = Math.min(circles.size(), base.size());
//                for (int i = 0; i < size; i++) {
//                    circles.get(i).setCenterX(base.get(i)[0] + dx);
//                    circles.get(i).setCenterY(base.get(i)[1] + dy);
//                }
//                for (Line ln : edges) syncEdge(ln);
//            };
//            return g;
//        }
//
//        // ── Graph factory ─────────────────────────────────────────────────────
//        static AnimationGroup graph(double cx, double cy, int n, double R, double alpha) {
//            AnimationGroup g = new AnimationGroup();
//
//            List<Circle>   circles = new ArrayList<>();
//            List<Line>     edges   = new ArrayList<>();
//            List<double[]> base    = new ArrayList<>();
//
//            double thetaOffset = new Random().nextDouble() * Math.PI * 2;
//
//            for (int i = 0; i < n; i++) {
//                double ang = thetaOffset + 2 * Math.PI * i / n;
//                double bx  = cx + Math.cos(ang) * R;
//                double by  = cy + Math.sin(ang) * R * 0.65;
//                Circle c   = makeCircle(bx, by, 6.5, alpha);
//                circles.add(c);
//                base.add(new double[]{bx, by});
//            }
//
//            Random rng = new Random(n * 31L + (long)(cx + cy));
//            for (int i = 0; i < n; i++) {
//                for (int j = i + 1; j < n; j++) {
//                    if (rng.nextDouble() < 0.40) {
//                        Line ln = new Line();
//                        ln.setStroke(edgeColor(alpha));
//                        ln.setStrokeWidth(0.9);
//                        ln.setUserData(new Circle[]{circles.get(i), circles.get(j)});
//                        edges.add(ln);
//                    }
//                }
//            }
//
//            g.nodes.addAll(edges);
//            g.nodes.addAll(circles);
//
//            double freq = 0.07 + new Random().nextDouble() * 0.06;
//            double mag  = 9   + new Random().nextDouble() * 6;
//
//            g.ticker = t -> {
//                // FIX 3 (same guard applied to graph ticker too)
//                int size = Math.min(circles.size(), base.size());
//                for (int i = 0; i < size; i++) {
//                    double phase = t * freq + i * 1.3;
//                    circles.get(i).setCenterX(base.get(i)[0] + Math.sin(phase)        * mag);
//                    circles.get(i).setCenterY(base.get(i)[1] + Math.cos(phase * 1.15) * mag * 0.55);
//                }
//                for (Line ln : edges) syncEdge(ln);
//            };
//            return g;
//        }
//
//        // ── Helpers ───────────────────────────────────────────────────────────
//
//        private static Circle makeCircle(double x, double y, double r, double alpha) {
//            Circle c = new Circle(x, y, r);
//            c.setFill(nodeColor(alpha * 0.55));
//            c.setStroke(strokeColor(alpha * 0.80));
//            c.setStrokeWidth(1.2);
//            c.setEffect(new DropShadow(10, glowColor()));
//            return c;
//        }
//
//        private static void syncEdge(Line l) {
//            Circle[] pair = (Circle[]) l.getUserData();
//            l.setStartX(pair[0].getCenterX());
//            l.setStartY(pair[0].getCenterY());
//            l.setEndX(pair[1].getCenterX());
//            l.setEndY(pair[1].getCenterY());
//        }
//    }
//}