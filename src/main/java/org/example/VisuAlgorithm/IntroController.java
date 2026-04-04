package org.example.VisuAlgorithm;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class IntroController {

    // ── FXML injections ──────────────────────────────────────────────────────
    @FXML private StackPane rootPane;
    @FXML private Pane      bgPane;
    @FXML private Pane      logoPane;
    @FXML private VBox      textGroup;
    @FXML private Label     titleLabel;
    @FXML private Label     subLabel;
    @FXML private Label     hintLabel;

    // =========================================================================
    //  LOGO GEOMETRY — all in scene space (1 366 × 768 window)
    // =========================================================================

    // ── V letter ──────────────────────────────────────────────────────────────
    private static final double N1X = 513, N1Y = 182;   // V top-left
    private static final double N3X = 653, N3Y = 182;   // V top-right
    private static final double N2X = 583, N2Y = 318;   // V bottom point  (focal)

    // ── A letter (perfectly symmetrical to the V) ─────────────────────────────
    private static final double N4X = 783, N4Y = 182;   // A apex
    private static final double N5X = 713, N5Y = 318;   // A bottom-left
    private static final double N6X = 853, N6Y = 318;   // A bottom-right

    // ── Array strip ───────────────────────────────────────────────────────────
    private static final int    ARR_N  = 8;
    private static final double ARR_X0 = 413;
    private static final double ARR_W  = 64;
    private static final double ARR_G  = 4;
    private static final double ARR_Y  = 422;
    private static final double ARR_H  = 38;

    // ── Node radii ────────────────────────────────────────────────────────────
    private static final double NR_T = 18;   // top-row nodes
    private static final double NR_B = 16;   // bottom-row nodes

    // ── Shape references ──────────────────────────────────────────────────────
    private Circle n1, n2, n3, n4, n5, n6;
    private Line   eV_L, eV_R;            // V legs
    private Line   eA_L, eA_R;            // A legs (crossbar removed)
    private Line   cn2, cn5, cn6;         // vertical connectors → array
    private final List<Rectangle> cells = new ArrayList<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        // 1. Create invisible anchors. This forces the internal coordinate grid
        // to be exactly 1366x768, even if the shapes don't touch the edges.
        Rectangle bgBounds = new Rectangle(0, 0, 1366, 768);
        bgBounds.setFill(Color.TRANSPARENT);
        bgPane.getChildren().add(bgBounds);

        Rectangle logoBounds = new Rectangle(0, 0, 1366, 768);
        logoBounds.setFill(Color.TRANSPARENT);
        logoPane.getChildren().add(logoBounds);

        // 2. Lock the Panes. This completely forbids JavaFX from stretching them.
        bgPane.setMinSize(1366, 768);
        bgPane.setMaxSize(1366, 768);

        logoPane.setMinSize(1366, 768);
        logoPane.setMaxSize(1366, 768);

        // 3. Push the text down so it clears the array strip
        textGroup.setTranslateY(260);

        buildBackground();
        buildLogo();
        playSequence();
    }

    // =========================================================================
    //  BACKGROUND — low-opacity floating CS shapes
    // =========================================================================

    private void buildBackground() {
        Random rng = new Random(42);   // fixed seed → deterministic layout

        // 1. Scattered graph-node circles
        for (int i = 0; i < 14; i++) {
            Circle c = new Circle(
                    rng.nextDouble() * 1366, rng.nextDouble() * 768,
                    3 + rng.nextDouble() * 5);
            c.setFill(Color.web("#00d4ff", 0.06));
            c.setStroke(Color.web("#00d4ff", 0.09));
            c.setStrokeWidth(1);
            bgPane.getChildren().add(c);
            floatShape(c, 4 + rng.nextDouble() * 5, rng);
        }

        // 2. Mini binary-tree fragments (3 nodes + 2 edges) — floated as a Group.
        for (int i = 0; i < 6; i++) {
            double cx     = 80  + rng.nextDouble() * 1200;
            double cy     = 80  + rng.nextDouble() * 580;
            double spread = 22  + rng.nextDouble() * 18;
            double vstep  = 20  + rng.nextDouble() * 12;

            Circle root   = bgCircle(cx,           cy,         5.5, "#00d4ff", 0.09, 0.13);
            Circle lChild = bgCircle(cx - spread,  cy + vstep, 4.0, "#00d4ff", 0.08, 0.11);
            Circle rChild = bgCircle(cx + spread,  cy + vstep, 4.0, "#00d4ff", 0.08, 0.11);
            Line   lEdge  = bgLine(cx, cy + 5.5, cx - spread, cy + vstep - 4, "#00d4ff", 0.07);
            Line   rEdge  = bgLine(cx, cy + 5.5, cx + spread, cy + vstep - 4, "#00d4ff", 0.07);

            Group tree = new Group(lEdge, rEdge, root, lChild, rChild);
            bgPane.getChildren().add(tree);
            floatShape(tree, 5 + rng.nextDouble() * 4, rng);
        }

        // 3. Mini linked-list chains (3 boxes + arrows) — floated as a Group
        for (int i = 0; i < 4; i++) {
            double startX = 60  + rng.nextDouble() * 1000;
            double startY = 50  + rng.nextDouble() * 650;
            double boxW = 20, boxH = 14, gap = 10;
            Group chain = new Group();

            for (int k = 0; k < 3; k++) {
                double bx = startX + k * (boxW + gap);
                Rectangle box = new Rectangle(bx, startY, boxW, boxH);
                box.setArcWidth(4); box.setArcHeight(4);
                box.setFill(Color.web("#38bdf8", 0.05));
                box.setStroke(Color.web("#38bdf8", 0.12));
                box.setStrokeWidth(1);
                chain.getChildren().add(box);

                if (k < 2) {
                    double ay = startY + boxH / 2.0;
                    double ax1 = bx + boxW, ax2 = bx + boxW + gap;
                    chain.getChildren().addAll(
                            bgLine(ax1,  ay,     ax2,     ay,     "#38bdf8", 0.10),
                            bgLine(ax2,  ay,     ax2 - 4, ay - 3, "#38bdf8", 0.10),
                            bgLine(ax2,  ay,     ax2 - 4, ay + 3, "#38bdf8", 0.10)
                    );
                }
            }
            bgPane.getChildren().add(chain);
            floatShape(chain, 4 + rng.nextDouble() * 4, rng);
        }

        // 4. Mini stack groups (3 stacked rectangles)
        for (int i = 0; i < 5; i++) {
            double rx = 80  + rng.nextDouble() * 1200;
            double ry = 60  + rng.nextDouble() * 600;
            for (int k = 0; k < 3; k++) {
                Rectangle r = new Rectangle(rx - 15, ry - k * 18, 30, 14);
                r.setArcWidth(5); r.setArcHeight(5);
                r.setFill(Color.web("#7c3aed", 0.055));
                r.setStroke(Color.web("#7c3aed", 0.10));
                r.setStrokeWidth(1);
                bgPane.getChildren().add(r);
                floatShape(r, 5 + rng.nextDouble() * 4, rng);
            }
        }

        // 5. Mini queue groups (4 horizontal rectangles)
        for (int i = 0; i < 4; i++) {
            double rx = 60  + rng.nextDouble() * 1100;
            double ry = 60  + rng.nextDouble() * 640;
            for (int k = 0; k < 4; k++) {
                Rectangle r = new Rectangle(rx + k * 22, ry, 19, 13);
                r.setArcWidth(4); r.setArcHeight(4);
                r.setFill(Color.web("#ff006e", 0.045));
                r.setStroke(Color.web("#ff006e", 0.09));
                r.setStrokeWidth(1);
                bgPane.getChildren().add(r);
                floatShape(r, 3.5 + rng.nextDouble() * 3.5, rng);
            }
        }

        // 6. Floating CS / code symbol text (monospace)
        String[] symbols = {
                "{}", "[]", "->", "//", "0x1F", "!=", "&&",
                "( )", "λ",  "∑",  "null", "O(n)", "::",
                "<<",  ">>", "int", "ptr",  "∅",    "log₂", "O(1)"
        };
        for (int i = 0; i < 14; i++) {
            Text t = new Text(
                    60  + rng.nextDouble() * 1240,
                    40  + rng.nextDouble() * 680,
                    symbols[rng.nextInt(symbols.length)]
            );
            t.setFont(Font.font("Courier New", 11 + rng.nextDouble() * 8));
            t.setFill(Color.web("#a78bfa", 0.085));
            bgPane.getChildren().add(t);
            floatShape(t, 4 + rng.nextDouble() * 5, rng);
        }
    }

    private Circle bgCircle(double cx, double cy, double r,
                            String hex, double fillA, double strokeA) {
        Circle c = new Circle(cx, cy, r);
        c.setFill(Color.web(hex, fillA));
        c.setStroke(Color.web(hex, strokeA));
        c.setStrokeWidth(1);
        return c;
    }

    private Line bgLine(double x1, double y1, double x2, double y2,
                        String hex, double alpha) {
        Line l = new Line(x1, y1, x2, y2);
        l.setStroke(Color.web(hex, alpha));
        l.setStrokeWidth(1);
        return l;
    }

    private void floatShape(Node shape, double durationSec, Random rng) {
        TranslateTransition tt = new TranslateTransition(Duration.seconds(durationSec), shape);
        tt.setByY(-(14 + rng.nextDouble() * 16));
        tt.setByX((rng.nextDouble() - 0.5) * 14);
        tt.setAutoReverse(true);
        tt.setCycleCount(Animation.INDEFINITE);
        tt.play();
    }

    // =========================================================================
    //  LOGO CONSTRUCTION
    // =========================================================================

    private void buildLogo() {

        // Edges — added first so they render behind nodes
        eV_L  = mkEdge(N1X, N1Y, N2X, N2Y, "#38bdf8");        // V left leg
        eV_R  = mkEdge(N3X, N3Y, N2X, N2Y, "#38bdf8");        // V right leg
        eA_L  = mkEdge(N4X, N4Y, N5X, N5Y, "#38bdf8");        // A left leg
        eA_R  = mkEdge(N4X, N4Y, N6X, N6Y, "#38bdf8");        // A right leg

        // Dashed vertical connectors: bottom node → array top
        cn2 = mkConnector(N2X, N2Y + NR_B + 1, N2X, ARR_Y);
        cn5 = mkConnector(N5X, N5Y + NR_B + 1, N5X, ARR_Y);
        cn6 = mkConnector(N6X, N6Y + NR_B + 1, N6X, ARR_Y);

        // Array cells
        for (int i = 0; i < ARR_N; i++) {
            Rectangle cell = new Rectangle(ARR_X0 + i * (ARR_W + ARR_G), ARR_Y, ARR_W, ARR_H);
            cell.setArcWidth(8); cell.setArcHeight(8);
            cell.setFill(Color.web("#081222", 0.92));
            cell.setStroke(Color.web("#00d4ff", 0.72));
            cell.setStrokeWidth(1.8);
            cell.setEffect(new DropShadow(10, Color.web("#00d4ff", 0.45)));
            cells.add(cell);
        }

        // Nodes  — n2 is the V focal point: largest glow, hero of the logo
        n1 = mkNode(N1X, N1Y, NR_T,     "#00d4ff", "#0891b2", 20); // V top-left
        n3 = mkNode(N3X, N3Y, NR_T,     "#00d4ff", "#0891b2", 20); // V top-right
        n2 = mkNode(N2X, N2Y, NR_B + 2, "#00d4ff", "#06b6d4", 30); // V point (hero)
        n4 = mkNode(N4X, N4Y, NR_T,     "#00d4ff", "#0891b2", 20); // A apex
        n5 = mkNode(N5X, N5Y, NR_B,     "#38bdf8", "#0284c7", 15); // A bottom-left
        n6 = mkNode(N6X, N6Y, NR_B,     "#38bdf8", "#0284c7", 15); // A bottom-right

        applyStartState();

        // Z-order: array (back) → connectors → edges → nodes (front)
        logoPane.getChildren().addAll(cells);
        logoPane.getChildren().addAll(cn2, cn5, cn6);
        logoPane.getChildren().addAll(eV_L, eV_R, eA_L, eA_R);
        logoPane.getChildren().addAll(n1, n3, n4, n2, n5, n6);
    }

    private void applyStartState() {
        for (Circle c : List.of(n1, n3, n4)) { c.setOpacity(0); c.setTranslateY(-280); }
        for (Circle c : List.of(n2, n5, n6)) { c.setOpacity(0); c.setTranslateY(-220); }
        for (Rectangle r : cells)             { r.setOpacity(0); r.setTranslateY(330);  }
        for (Line l : List.of(eV_L, eV_R, eA_L, eA_R, cn2, cn5, cn6))
            l.setOpacity(0);
        textGroup.setOpacity(0);
        textGroup.setScaleX(0.78);
        textGroup.setScaleY(0.78);
    }

    private Circle mkNode(double x, double y, double r,
                          String fillHex, String strokeHex, double glowR) {
        Circle c = new Circle(x, y, r);
        c.setFill(Color.web(fillHex, 0.88));
        c.setStroke(Color.web(strokeHex));
        c.setStrokeWidth(2.4);
        c.setEffect(new DropShadow(glowR, Color.web(fillHex)));
        return c;
    }

    private Line mkEdge(double x1, double y1, double x2, double y2, String hex) {
        Line l = new Line(x1, y1, x2, y2);
        l.setStroke(Color.web(hex, 0.74));
        l.setStrokeWidth(2.1);
        l.setEffect(new DropShadow(9, Color.web(hex, 0.38)));
        return l;
    }

    private Line mkConnector(double x1, double y1, double x2, double y2) {
        Line l = new Line(x1, y1, x2, y2);
        l.setStroke(Color.web("#00d4ff", 0.40));
        l.setStrokeWidth(1.5);
        l.getStrokeDashArray().addAll(5.0, 5.0);
        return l;
    }

    // =========================================================================
    //  ANIMATION SEQUENCE
    // =========================================================================

    private void playSequence() {

        // ─── Phase 1: array cells slide up (staggered left → right) ──────────
        ParallelTransition cellsPhase = new ParallelTransition();
        for (int i = 0; i < cells.size(); i++) {
            Duration d = Duration.millis(28 * i);
            FadeTransition      f = new FadeTransition(Duration.millis(360), cells.get(i));
            TranslateTransition s = new TranslateTransition(Duration.millis(440), cells.get(i));
            f.setToValue(1);   f.setDelay(d);
            s.setToY(0);       s.setDelay(d);  s.setInterpolator(Interpolator.EASE_OUT);
            cellsPhase.getChildren().addAll(f, s);
        }

        // ─── Phase 2: top nodes drop (n1, n3, n4 — same row, left→right) ─────
        ParallelTransition topPhase = new ParallelTransition();
        int ti = 0;
        for (Circle c : List.of(n1, n3, n4)) {
            Duration d = Duration.millis(65 * ti++);
            FadeTransition      f = new FadeTransition(Duration.millis(400), c);
            TranslateTransition t = new TranslateTransition(Duration.millis(520), c);
            f.setToValue(1); f.setDelay(d);
            t.setToY(0);     t.setDelay(d);  t.setInterpolator(Interpolator.EASE_OUT);
            topPhase.getChildren().addAll(f, t);
        }

        // ─── Phase 3: bottom nodes drop (n2, n5, n6) ─────────────────────────
        ParallelTransition botPhase = new ParallelTransition();
        int bi = 0;
        for (Circle c : List.of(n2, n5, n6)) {
            Duration d = Duration.millis(65 * bi++);
            FadeTransition      f = new FadeTransition(Duration.millis(380), c);
            TranslateTransition t = new TranslateTransition(Duration.millis(490), c);
            f.setToValue(1); f.setDelay(d);
            t.setToY(0);     t.setDelay(d);  t.setInterpolator(Interpolator.EASE_OUT);
            botPhase.getChildren().addAll(f, t);
        }

        // ─── Phase 4: edges draw via stroke-dash-offset ───────────────────────
        ParallelTransition edgesPhase = new ParallelTransition();
        int ei = 0;
        for (Line l : List.of(eV_L, eV_R, eA_L, eA_R)) {
            double   len   = Math.hypot(l.getEndX() - l.getStartX(), l.getEndY() - l.getStartY());
            Duration delay = Duration.millis(55 * ei++);

            l.getStrokeDashArray().setAll(len, len);
            l.setStrokeDashOffset(len);    // start invisible

            FadeTransition fade = new FadeTransition(Duration.millis(80), l);
            fade.setToValue(1); fade.setDelay(delay);

            Timeline draw = new Timeline(
                    new KeyFrame(delay,
                            new KeyValue(l.strokeDashOffsetProperty(), len)),
                    new KeyFrame(delay.add(Duration.millis(400)),
                            new KeyValue(l.strokeDashOffsetProperty(), 0, Interpolator.EASE_OUT))
            );
            edgesPhase.getChildren().addAll(fade, draw);
        }

        // ─── Phase 5: connectors + text reveal ───────────────────────────────
        ParallelTransition connPhase = new ParallelTransition();
        for (Line l : List.of(cn2, cn5, cn6)) {
            FadeTransition f = new FadeTransition(Duration.millis(320), l);
            f.setToValue(1);
            connPhase.getChildren().add(f);
        }
        FadeTransition  textFade  = new FadeTransition(Duration.millis(460), textGroup);
        ScaleTransition textScale = new ScaleTransition(Duration.millis(520), textGroup);
        textFade.setToValue(1);
        textScale.setToX(1); textScale.setToY(1); textScale.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition revealPhase = new ParallelTransition(
                connPhase, new ParallelTransition(textFade, textScale));

        // ─── Phase 6: n2 pulse (V focal) + hint blink ────────────────────────
        ScaleTransition pulse = new ScaleTransition(Duration.millis(290), n2);
        pulse.setToX(1.22); pulse.setToY(1.22);
        pulse.setAutoReverse(true); pulse.setCycleCount(2);

        FadeTransition hintBlink = new FadeTransition(Duration.millis(480), hintLabel);
        hintBlink.setFromValue(0.25); hintBlink.setToValue(1.0);
        hintBlink.setAutoReverse(true); hintBlink.setCycleCount(2);

        // ─── Phase 7: fade to dark → switch scene ────────────────────────────
        FadeTransition fadeOut = new FadeTransition(Duration.millis(650), rootPane);
        fadeOut.setFromValue(1.0); fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(ev -> switchToHome());

        // ─── Master sequence ──────────────────────────────────────────────────
        new SequentialTransition(
                cellsPhase,
                new PauseTransition(Duration.millis(50)),
                topPhase,
                new PauseTransition(Duration.millis(40)),
                botPhase,
                new PauseTransition(Duration.millis(50)),
                edgesPhase,
                revealPhase,
                new ParallelTransition(pulse, hintBlink),
                new PauseTransition(Duration.millis(200)),
                fadeOut
        ).play();
    }

    // =========================================================================
    //  SCENE SWITCH — avoids the white-flash artefact
    // =========================================================================

    // =========================================================================
    //  SCENE SWITCH — avoids the white-flash artefact
    // =========================================================================

    private void switchToHome() {
        try {
            URL fxmlUrl = getClass().getResource("hello-view.fxml");
            if (fxmlUrl == null) {
                Launcher.switchScene("hello-view.fxml");
                return;
            }

            Parent newRoot = new FXMLLoader(fxmlUrl).load();

            // FIX: Force the dark background immediately before CSS loads
            newRoot.setStyle("-fx-background-color: #000810;");

            // FIX: Set initial opacity to 0 so we can fade it in smoothly
            newRoot.setOpacity(0.0);

            // 1. Grab the existing scene instead of the Stage
            Scene currentScene = rootPane.getScene();

            if (currentScene != null) {
                // 2. Kill the white flash on the existing scene backdrop
                currentScene.setFill(Color.web("#000810"));

                // 3. Update the stylesheets for the new view
                currentScene.getStylesheets().clear();
                URL css = getClass().getResource("styles/main.css"); // Ensure this path is correct
                if (css != null) {
                    currentScene.getStylesheets().add(css.toExternalForm());
                }

                // 4. Swap out the UI contents safely
                currentScene.setRoot(newRoot);

                // 5. Fade the new scene in for a buttery smooth transition
                FadeTransition fadeIn = new FadeTransition(Duration.millis(500), newRoot);
                fadeIn.setToValue(1.0);
                fadeIn.play();

            } else {
                // Fallback if scene is somehow detached
                Launcher.switchScene("hello-view.fxml");
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            Launcher.switchScene("hello-view.fxml");  // fallback
        }
    }
}