package org.example.VisuAlgorithm;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import javafx.util.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * IntroController — animated splash screen for VisuAlgorithm.
 *
 * <h3>Animation sequence (~4 s total)</h3>
 * <ol>
 *   <li>Array cells slide up from below with stagger   (~600 ms)</li>
 *   <li>Top-row nodes drop from above with stagger     (~560 ms)</li>
 *   <li>Mid-row nodes drop with stagger                (~530 ms)</li>
 *   <li>W-pattern edges "draw" themselves (dash offset)  (~400 ms)</li>
 *   <li>Vertical connectors + title text fade in        (~500 ms)</li>
 *   <li>Hero-node (n2) scale-pulse                      (~560 ms)</li>
 *   <li>Pause → fade out entire root → switch scene     (~800 ms)</li>
 * </ol>
 *
 * <h3>Logo geometry</h3>
 * Tuned for a 1 366 × 768 window.
 * <pre>
 *   n1 ─────── n2 ─────── n3      ← top row  (y = 185)
 *    ╲   ╲   ╱   ╲   ╱   ╱
 *     n4    n5    n6              ← mid row  (y = 315)
 *      │    │    │
 *  ┌──┬──┬──┬──┬──┬──┬──┬──┐    ← 8-cell array (y = 415)
 *  └──┴──┴──┴──┴──┴──┴──┴──┘
 * </pre>
 * Edges (W pattern): n1→n4, n2→n4, n2→n6, n3→n6
 */
public class IntroController {

    // ── FXML injections ─────────────────────────────────────────────────────
    @FXML private StackPane rootPane;   // full-screen container
    @FXML private Pane      bgPane;     // low-opacity background shapes
    @FXML private Pane      logoPane;   // logo shapes (added programmatically)
    @FXML private VBox      textGroup;  // app name + tagline
    @FXML private Label     titleLabel;
    @FXML private Label     subLabel;
    @FXML private Label     hintLabel;

    // ── Logo geometry (all positions in scene / pane coordinates) ───────────

    // Top-row node centres
    private static final double N1X = 490,  N1Y = 185;
    private static final double N2X = 683,  N2Y = 185;   // hero node (slightly larger)
    private static final double N3X = 876,  N3Y = 185;

    // Mid-row node centres  (form W together with top row)
    private static final double N4X = 568,  N4Y = 315;
    private static final double N5X = 683,  N5Y = 315;   // gold accent — no edge
    private static final double N6X = 798,  N6Y = 315;

    // 8-cell array strip
    private static final int    ARR_N  = 8;
    private static final double ARR_X0 = 415;             // left edge of cell 0
    private static final double ARR_W  = 64;              // cell width
    private static final double ARR_G  = 4;               // gap between cells
    private static final double ARR_Y  = 415;             // top of strip
    private static final double ARR_H  = 38;              // cell height

    // Node radii
    private static final double NR_TOP = 18;              // top row
    private static final double NR_MID = 15;              // mid row

    // ── Shape references (needed for animation & z-ordering) ────────────────
    private Circle    n1, n2, n3, n4, n5, n6;
    private Line      e1, e2, e3, e4;            // four W-pattern edges
    private Line      cn4, cn5, cn6;             // vertical node→array connectors
    private final List<Rectangle> cells = new ArrayList<>();

    // ── Lifecycle ────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        buildBackground();
        buildLogo();
        playSequence();
    }

    // =========================================================================
    //  1.  BACKGROUND  — low-opacity floating DSA shapes
    // =========================================================================

    private void buildBackground() {
        Random rng = new Random(42);  // fixed seed → deterministic layout

        // Tiny graph nodes (circles)
        for (int i = 0; i < 16; i++) {
            double x = rng.nextDouble() * 1366;
            double y = rng.nextDouble() * 768;
            Circle c = new Circle(x, y, 3 + rng.nextDouble() * 5);
            c.setFill(Color.web("#00d4ff", 0.06));
            c.setStroke(Color.web("#00d4ff", 0.09));
            c.setStrokeWidth(1);
            bgPane.getChildren().add(c);
            floatShape(c, 4 + rng.nextDouble() * 5, rng);
        }

        // Mini binary-tree outlines (triangles)
        for (int i = 0; i < 6; i++) {
            double tx = 80  + rng.nextDouble() * 1200;
            double ty = 60  + rng.nextDouble() * 620;
            Polygon tri = new Polygon(tx, ty - 22, tx - 18, ty + 13, tx + 18, ty + 13);
            tri.setFill(Color.TRANSPARENT);
            tri.setStroke(Color.web("#00d4ff", 0.07));
            tri.setStrokeWidth(1.4);
            bgPane.getChildren().add(tri);
            floatShape(tri, 5 + rng.nextDouble() * 4, rng);
        }

        // Mini stack groups (3 stacked rectangles)
        for (int i = 0; i < 5; i++) {
            double rx = 80  + rng.nextDouble() * 1200;
            double ry = 60  + rng.nextDouble() * 600;
            for (int k = 0; k < 3; k++) {
                Rectangle r = new Rectangle(rx - 15, ry - k * 18, 30, 14);
                r.setArcWidth(5);  r.setArcHeight(5);
                r.setFill(Color.web("#7c3aed", 0.055));
                r.setStroke(Color.web("#7c3aed", 0.10));
                r.setStrokeWidth(1);
                bgPane.getChildren().add(r);
                floatShape(r, 5 + rng.nextDouble() * 4, rng);
            }
        }

        // Mini queue groups (4 horizontal rectangles)
        for (int i = 0; i < 4; i++) {
            double rx = 60  + rng.nextDouble() * 1100;
            double ry = 60  + rng.nextDouble() * 640;
            for (int k = 0; k < 4; k++) {
                Rectangle r = new Rectangle(rx + k * 22, ry, 19, 13);
                r.setArcWidth(4);  r.setArcHeight(4);
                r.setFill(Color.web("#ff006e", 0.045));
                r.setStroke(Color.web("#ff006e", 0.09));
                r.setStrokeWidth(1);
                bgPane.getChildren().add(r);
                floatShape(r, 3.5 + rng.nextDouble() * 3.5, rng);
            }
        }
    }

    /**
     * Applies an infinite, auto-reversing TranslateTransition to a background
     * shape so it slowly "floats" up and sideways.
     */
    private void floatShape(Node shape, double durationSec, Random rng) {
        double byY = -(15 + rng.nextDouble() * 18);
        double byX = (rng.nextDouble() - 0.5) * 14;
        TranslateTransition tt = new TranslateTransition(Duration.seconds(durationSec), shape);
        tt.setByY(byY);
        tt.setByX(byX);
        tt.setAutoReverse(true);
        tt.setCycleCount(Animation.INDEFINITE);
        tt.play();
    }

    // =========================================================================
    //  2.  LOGO  — nodes, edges, connectors, array strip
    // =========================================================================

    private void buildLogo() {

        // ── Edges — rendered BEHIND nodes, so added to pane first ────────────
        //    W pattern: n1→n4, n2→n4, n2→n6, n3→n6
        e1 = mkEdge(N1X, N1Y, N4X, N4Y);
        e2 = mkEdge(N2X, N2Y, N4X, N4Y);
        e3 = mkEdge(N2X, N2Y, N6X, N6Y);
        e4 = mkEdge(N3X, N3Y, N6X, N6Y);

        // ── Vertical dashed connectors: mid-node bottom → array top ──────────
        //    (n5 sits in the middle — its connector is the visual centrepiece)
        cn4 = mkConnector(N4X, N4Y + NR_MID + 1, N4X, ARR_Y);
        cn5 = mkConnector(N5X, N5Y + NR_MID + 1, N5X, ARR_Y);
        cn6 = mkConnector(N6X, N6Y + NR_MID + 1, N6X, ARR_Y);

        // ── Array cells ───────────────────────────────────────────────────────
        for (int i = 0; i < ARR_N; i++) {
            double x = ARR_X0 + i * (ARR_W + ARR_G);
            Rectangle cell = new Rectangle(x, ARR_Y, ARR_W, ARR_H);
            cell.setArcWidth(8);
            cell.setArcHeight(8);
            cell.setFill(Color.web("#081222", 0.92));
            cell.setStroke(Color.web("#00d4ff", 0.72));
            cell.setStrokeWidth(1.8);
            cell.setEffect(new DropShadow(10, Color.web("#00d4ff", 0.45)));
            cells.add(cell);
        }

        // ── Nodes ─────────────────────────────────────────────────────────────
        //    n2 is the "hero" node: slightly larger, brighter glow
        n1 = mkNode(N1X, N1Y, NR_TOP,     "#00d4ff", "#0891b2", 20);
        n2 = mkNode(N2X, N2Y, NR_TOP + 3, "#00d4ff", "#06b6d4", 34); // hero
        n3 = mkNode(N3X, N3Y, NR_TOP,     "#00d4ff", "#0891b2", 20);
        n4 = mkNode(N4X, N4Y, NR_MID,     "#38bdf8", "#0284c7", 14);
        n5 = mkNode(N5X, N5Y, NR_MID,     "#fbbf24", "#d97706", 14); // gold accent
        n6 = mkNode(N6X, N6Y, NR_MID,     "#38bdf8", "#0284c7", 14);

        // ── Set every shape to its START (off-screen / invisible) state ───────
        applyStartState();

        // ── Add to pane in z-order: array → connectors → edges → nodes ────────
        logoPane.getChildren().addAll(cells);
        logoPane.getChildren().addAll(cn4, cn5, cn6);
        logoPane.getChildren().addAll(e1, e2, e3, e4);
        logoPane.getChildren().addAll(n1, n2, n3, n4, n5, n6);
    }

    /** Moves every logo element to its pre-animation (invisible, off-screen) position. */
    private void applyStartState() {
        // Top-row nodes: arrive from well above the scene
        for (Circle c : List.of(n1, n2, n3)) {
            c.setOpacity(0);
            c.setTranslateY(-280);
        }
        // Mid-row nodes: arrive from slightly above the scene
        for (Circle c : List.of(n4, n5, n6)) {
            c.setOpacity(0);
            c.setTranslateY(-220);
        }
        // Array cells: rise from below the scene
        for (Rectangle r : cells) {
            r.setOpacity(0);
            r.setTranslateY(330);
        }
        // All edges and connectors hidden
        for (Line l : List.of(e1, e2, e3, e4, cn4, cn5, cn6)) {
            l.setOpacity(0);
        }
        // Text group: invisible and slightly shrunk
        textGroup.setOpacity(0);
        textGroup.setScaleX(0.78);
        textGroup.setScaleY(0.78);
    }

    // ── Shape factory helpers ─────────────────────────────────────────────────

    /** Creates a glowing node circle. */
    private Circle mkNode(double cx, double cy, double r,
                          String fillHex, String strokeHex, double glowRadius) {
        Circle c = new Circle(cx, cy, r);
        c.setFill(Color.web(fillHex, 0.88));
        c.setStroke(Color.web(strokeHex));
        c.setStrokeWidth(2.4);
        c.setEffect(new DropShadow(glowRadius, Color.web(fillHex)));
        return c;
    }

    /** Creates a glowing W-edge line. */
    private Line mkEdge(double x1, double y1, double x2, double y2) {
        Line l = new Line(x1, y1, x2, y2);
        l.setStroke(Color.web("#38bdf8", 0.74));
        l.setStrokeWidth(2.1);
        l.setEffect(new DropShadow(9, Color.web("#00d4ff", 0.38)));
        return l;
    }

    /** Creates a dashed vertical connector between a mid-node and the array top. */
    private Line mkConnector(double x1, double y1, double x2, double y2) {
        Line l = new Line(x1, y1, x2, y2);
        l.setStroke(Color.web("#00d4ff", 0.42));
        l.setStrokeWidth(1.5);
        l.getStrokeDashArray().addAll(5.0, 5.0);
        return l;
    }

    // =========================================================================
    //  3.  ANIMATION SEQUENCE
    // =========================================================================

    private void playSequence() {

        // ─── Phase 1 : Array cells slide up (staggered) ──────────────────────
        ParallelTransition cellsPhase = new ParallelTransition();
        for (int i = 0; i < cells.size(); i++) {
            Rectangle cell  = cells.get(i);
            Duration  delay = Duration.millis(28 * i);   // 28 ms stagger per cell

            FadeTransition fade = new FadeTransition(Duration.millis(360), cell);
            fade.setToValue(1);
            fade.setDelay(delay);

            TranslateTransition slide = new TranslateTransition(Duration.millis(440), cell);
            slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);
            slide.setDelay(delay);

            cellsPhase.getChildren().addAll(fade, slide);
        }
        // Duration of cellsPhase ≈ 28 × 7 + 440 = 636 ms

        // ─── Phase 2 : Top-row nodes drop (left → centre → right) ────────────
        ParallelTransition topPhase = new ParallelTransition();
        int ti = 0;
        for (Circle c : List.of(n1, n2, n3)) {
            Duration delay = Duration.millis(65 * ti++);

            FadeTransition fade = new FadeTransition(Duration.millis(400), c);
            fade.setToValue(1);
            fade.setDelay(delay);

            TranslateTransition drop = new TranslateTransition(Duration.millis(520), c);
            drop.setToY(0);
            drop.setInterpolator(Interpolator.EASE_OUT);
            drop.setDelay(delay);

            topPhase.getChildren().addAll(fade, drop);
        }
        // Duration ≈ 65 × 2 + 520 = 650 ms

        // ─── Phase 3 : Mid-row nodes drop (left → gold → right) ──────────────
        ParallelTransition midPhase = new ParallelTransition();
        int mi = 0;
        for (Circle c : List.of(n4, n5, n6)) {
            Duration delay = Duration.millis(65 * mi++);

            FadeTransition fade = new FadeTransition(Duration.millis(380), c);
            fade.setToValue(1);
            fade.setDelay(delay);

            TranslateTransition drop = new TranslateTransition(Duration.millis(490), c);
            drop.setToY(0);
            drop.setInterpolator(Interpolator.EASE_OUT);
            drop.setDelay(delay);

            midPhase.getChildren().addAll(fade, drop);
        }
        // Duration ≈ 65 × 2 + 490 = 620 ms

        // ─── Phase 4 : Edges "draw" themselves (stroke-dash offset trick) ─────
        //
        //  How it works:
        //    1. Set dashArray = [lineLength, lineLength]  → one dash that fills the line,
        //       followed by one gap of the same length (i.e. the line is invisible).
        //    2. Set dashOffset = lineLength                → shift the pattern so the
        //       gap occupies the visible portion (line appears empty).
        //    3. Animate dashOffset → 0                    → dash slides in, "drawing"
        //       the line from start to end.
        //
        ParallelTransition edgesPhase = new ParallelTransition();
        int ei = 0;
        for (Line l : List.of(e1, e2, e3, e4)) {
            double lineLen = Math.hypot(
                    l.getEndX() - l.getStartX(),
                    l.getEndY() - l.getStartY());

            l.getStrokeDashArray().setAll(lineLen, lineLen);
            l.setStrokeDashOffset(lineLen);              // start fully invisible

            Duration delay = Duration.millis(55 * ei++);

            // Make the line visible just before drawing starts
            FadeTransition fadeEdge = new FadeTransition(Duration.millis(80), l);
            fadeEdge.setToValue(1);
            fadeEdge.setDelay(delay);

            // Animate the offset: lineLen → 0  (line "draws" itself)
            Timeline drawLine = new Timeline(
                new KeyFrame(delay,
                             new KeyValue(l.strokeDashOffsetProperty(), lineLen)),
                new KeyFrame(delay.add(Duration.millis(400)),
                             new KeyValue(l.strokeDashOffsetProperty(), 0,
                                          Interpolator.EASE_OUT))
            );

            edgesPhase.getChildren().addAll(fadeEdge, drawLine);
        }
        // Duration ≈ 55 × 3 + 400 = 565 ms (all edges draw in parallel with stagger)

        // ─── Phase 5 : Connectors fade in (simultaneously) ───────────────────
        ParallelTransition connPhase = new ParallelTransition();
        for (Line l : List.of(cn4, cn5, cn6)) {
            FadeTransition f = new FadeTransition(Duration.millis(320), l);
            f.setToValue(1);
            connPhase.getChildren().add(f);
        }

        // ─── Phase 6 : Text group fades in and scales up ─────────────────────
        FadeTransition  textFade  = new FadeTransition(Duration.millis(460), textGroup);
        textFade.setToValue(1);

        ScaleTransition textScale = new ScaleTransition(Duration.millis(520), textGroup);
        textScale.setToX(1.0);
        textScale.setToY(1.0);
        textScale.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition textPhase = new ParallelTransition(textFade, textScale);

        // ─── Phase 5+6 combined (connectors and text appear together) ─────────
        ParallelTransition revealPhase = new ParallelTransition(connPhase, textPhase);

        // ─── Phase 7 : Hero-node (n2) "lands" with a scale pulse ─────────────
        //    Forward + autoReverse with cycleCount=2 → one bounce: 1.0 → 1.18 → 1.0
        ScaleTransition pulse = new ScaleTransition(Duration.millis(280), n2);
        pulse.setFromX(1.0);   pulse.setFromY(1.0);
        pulse.setToX(1.18);    pulse.setToY(1.18);
        pulse.setInterpolator(Interpolator.EASE_BOTH);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(2);    // total = 280 × 2 = 560 ms

        // ─── Phase 8 : Hint-label opacity pulse ───────────────────────────────
        FadeTransition hintPulse = new FadeTransition(Duration.millis(550), hintLabel);
        hintPulse.setFromValue(0.35);
        hintPulse.setToValue(1.0);
        hintPulse.setAutoReverse(true);
        hintPulse.setCycleCount(2);   // 550 × 2 = 1 100 ms  (runs in parallel with pulse above)

        ParallelTransition holdPhase = new ParallelTransition(pulse, hintPulse);

        // ─── Phase 9 : Fade entire screen to black, then switch scene ─────────
        FadeTransition fadeOut = new FadeTransition(Duration.millis(650), rootPane);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(ev -> Launcher.switchScene("hello-view.fxml"));

        // ─── Master sequential timeline ───────────────────────────────────────
        //
        //  Approximate cumulative times:
        //    0.00 s  cells start sliding up
        //    0.64 s  cells done  →  top nodes start dropping
        //    0.69 s  (50 ms pause)
        //    1.34 s  top nodes done  →  mid nodes start dropping
        //    1.38 s  (40 ms pause)
        //    2.00 s  mid nodes done  →  edges draw
        //    2.05 s  (50 ms pause)
        //    2.62 s  edges done  →  connectors + text fade in
        //    3.14 s  reveal done  →  pulse + hint blink (parallel)
        //    4.25 s  hold done   →  200 ms pause → fade out begins
        //    5.10 s  fade out complete → scene switches
        //
        SequentialTransition master = new SequentialTransition(
            cellsPhase,
            new PauseTransition(Duration.millis(50)),
            topPhase,
            new PauseTransition(Duration.millis(40)),
            midPhase,
            new PauseTransition(Duration.millis(50)),
            edgesPhase,
            revealPhase,
            holdPhase,
            new PauseTransition(Duration.millis(200)),
            fadeOut
        );

        master.play();
    }
}
