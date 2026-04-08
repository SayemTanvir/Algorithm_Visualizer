package org.example.VisuAlgorithm;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.util.Duration;

// Capture/Recording imports
import javafx.scene.image.WritableImage;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture; // <-- Added for streaming sync
import org.jcodec.api.awt.AWTSequenceEncoder;

import java.util.*;

/**
 * Singly Linked List — VisualGo-style step animation.
 *
 * Visual language (mirrors VisualGo):
 * • Orange  (#f97316) = node being operated on (insert/delete target)
 * • Blue    (#3b82f6) = pointer / traversal cursor
 * • Green   (#22c55e) = found / success
 * • Red     (#ef4444) = being deleted
 * • Default (#1e3a5f bg, #93c5fd border) = normal node
 *
 * Nodes slide in from above on insert; shrink + fade on delete.
 * Pointer label ("ptr") hops node-to-node during search / traversal.
 */
public class SinglyLinkedListController {

    // ── FXML bindings ─────────────────────────────────────────────────────────
    @FXML private Pane      canvas;
    @FXML private TextField valueField;
    @FXML private TextField indexField;
    @FXML private Label     statusLabel;
    @FXML private Label     headerStatusLabel;

    // --- Capture buttons ---
    @FXML private Button screenshotBtn;
    @FXML private Button recordBtn;

    // Recording state
    private boolean isRecording = false;
    private ScheduledExecutorService recordingExecutor;
    private AWTSequenceEncoder encoder; // <-- Replaced List with direct Encoder
    private static final int RECORD_FPS = 30; // frames per second

    // ── Data model ────────────────────────────────────────────────────────────
    private static class Node {
        int  data;
        Node next;
        Node(int data) { this.data = data; }
    }
    private Node head;

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final double START_X  = 70;
    private static final double NODE_Y   = 220;   // vertical centre of nodes
    private static final double BOX_W    = 72;    // data cell width
    private static final double PTR_W    = 36;    // pointer cell width
    private static final double NODE_H   = 48;
    private static final double GAP      = 140;   // centre-to-centre spacing
    private static final double ARC      = 12;    // corner radius

    // ── Animation helpers ─────────────────────────────────────────────────────
    private final Random     rng        = new Random();
    private       Timeline   animation  = new Timeline();

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color C_NODE_FILL   = Color.web("#0f172a");
    private static final Color C_NODE_STROKE = Color.web("#3b82f6");
    private static final Color C_PTR_FILL    = Color.web("#1e293b");
    private static final Color C_TEXT        = Color.web("#e2e8f0");
    private static final Color C_ARROW       = Color.web("#64748b");
    private static final Color C_HIGHLIGHT   = Color.web("#f97316"); // orange = target
    private static final Color C_TRAVERSE    = Color.web("#3b82f6"); // blue  = cursor
    private static final Color C_FOUND       = Color.web("#22c55e"); // green = found
    private static final Color C_DELETE      = Color.web("#ef4444"); // red   = delete
    private static final Color C_NEW         = Color.web("#a855f7"); // purple= new node

    // ── FXML init ─────────────────────────────────────────────────────────────
    @FXML public void initialize() {
        screenshotBtn.setText("📷 Snapshot");
        recordBtn.setText("🎥 Record");

        screenshotBtn.setPrefWidth(130);
        screenshotBtn.setMinWidth(130);

        recordBtn.setPrefWidth(130);
        recordBtn.setMinWidth(130);

        redraw(-1, -1, -1);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Navigation
    // ══════════════════════════════════════════════════════════════════════════
    @FXML private void onBack() {
        if (isRecording) stopRecording();
        Launcher.switchScene("linked-list-view.fxml");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Public button handlers
    // ══════════════════════════════════════════════════════════════════════════

    @FXML private void onRandom() {
        stopAnim();
        head = null;
        int count = rng.nextInt(4) + 3;           // 3–6 nodes
        SequentialTransition seq = new SequentialTransition();
        Timeline timeline = new Timeline();

        for (int i = 0; i < count; i++) {
            int val = rng.nextInt(90) + 10;
            int step = i;

            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(step * 0.6), e -> {
                        Node node = new Node(val);

                        if (head == null) {
                            head = node;
                        } else {
                            Node temp = head;
                            while (temp.next != null) temp = temp.next;
                            temp.next = node;
                        }

                        redraw(-1, -1,-1);
                        setStatus("Inserted random: " + val);
                    })
            );
        }

        timeline.play();
        seq.play();
        setStatus("Generated " + count + " random nodes");
    }

    @FXML private void onInsertHead() {
        Integer val = readVal(); if (val == null) return;
        stopAnim();
        playInsertAt(0, val);
    }

    @FXML private void onInsertTail() {
        Integer val = readVal(); if (val == null) return;
        stopAnim();
        playInsertAt(size(), val);
    }

    @FXML private void onInsertAt() {
        Integer val = readVal(); if (val == null) return;
        Integer idx = readIdx(); if (idx == null) return;
        if (idx < 0 || idx > size()) { err("Index out of range [0.." + size() + "]"); return; }
        stopAnim();
        playInsertAt(idx, val);
    }

    @FXML private void onDeleteHead() {
        if (head == null) { err("List is empty"); return; }
        stopAnim();
        playDeleteAt(0);
    }

    @FXML private void onDeleteTail() {
        if (head == null) { err("List is empty"); return; }
        stopAnim();
        playDeleteAt(size() - 1);
    }

    @FXML private void onDeleteAt() {
        Integer idx = readIdx(); if (idx == null) return;
        if (idx < 0 || idx >= size()) { err("Index out of range [0.." + (size()-1) + "]"); return; }
        stopAnim();
        playDeleteAt(idx);
    }

    @FXML private void onSearch() {
        Integer val = readVal(); if (val == null) return;
        if (head == null) { err("List is empty"); return; }
        stopAnim();
        playSearch(val);
    }

    @FXML private void onTraverse() {
        if (head == null) { err("List is empty"); return; }
        stopAnim();
        playTraverse();
    }

    @FXML private void onSort() {
        if (head == null || head.next == null) { err("Need ≥ 2 nodes"); return; }
        stopAnim();
        playBubbleSort();
    }

    @FXML private void onClear() {
        stopAnim(); head = null;
        redraw(-1, -1, -1);
        setStatus("List cleared");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Animation — Insert
    // ══════════════════════════════════════════════════════════════════════════
    private void playInsertAt(int idx, int val) {
        List<KeyFrame> kf = new ArrayList<>();
        double t = 0;
        if (idx > 0) {
            for (int i = 0; i < idx; i++) {
                int cur = i;
                kf.add(kfAt(t, () -> {
                    redraw(-1, cur, -1);
                    setStatus("Traversing to index " + cur + "…");
                }));
                t += 0.45;
            }
        }
        // Step 1: highlight predecessor (if any) in orange
        if (idx > 0) {
            int pred = idx - 1;
            kf.add(kfAt(t, () -> {
                redraw(pred, -1, -1);
                setStatus("Step 1 — locate predecessor at index " + pred);
            }));
            t += 0.7;
        }

        // Step 2: show new node in purple above its final position
        kf.add(kfAt(t, () -> {
            redraw(idx > 0 ? idx - 1 : -1, -1, -1);
            showFloatingNode(idx, val, C_NEW);
            setStatus("Step 2 — new node [" + val + "] ready to insert at index " + idx);
        }));
        t += 0.7;

        // Step 3: commit insert, highlight new node
        kf.add(kfAt(t, () -> {
            insertLogic(idx, val);
            redraw(idx, -1, -1);
            setStatus("Step 3 — inserted [" + val + "] at index " + idx + " ✓");
        }));

        runTimeline(kf);
    }

    /** Draws a temporary ghost node above its destination slot. */
    private void showFloatingNode(int idx, int val, Color color) {
        redraw(-1, -1, -1);   // clear highlights first
        double x = START_X + idx * GAP;
        double y = NODE_Y - 80;
        canvas.getChildren().addAll(
                makeNodeGroup(x, y, val, color, -1, false, -1)
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Animation — Delete
    // ══════════════════════════════════════════════════════════════════════════
    private void playDeleteAt(int idx) {
        List<KeyFrame> kf = new ArrayList<>();
        double t = 0;

        // Step 1: walk to predecessor
        if (idx > 0) {
            for (int i = 0; i < idx; i++) {
                int cur = i;
                kf.add(kfAt(t, () -> {
                    redraw(-1, cur, -1);
                    setStatus("Traversing to index " + cur + "…");
                }));
                t += 0.45;
            }
        }

        // Step 2: highlight target in red
        kf.add(kfAt(t, () -> {
            redraw(idx, -1, -1);
            setStatus("Step — deleting node at index " + idx);
        }));
        t += 0.7;

        // Step 3: remove
        kf.add(kfAt(t, () -> {
            int removed = getAt(idx).data;
            deleteLogic(idx);
            redraw(-1, -1, -1);
            setStatus("Deleted [" + removed + "] from index " + idx + " ✓");
        }));

        runTimeline(kf);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Animation — Search
    // ══════════════════════════════════════════════════════════════════════════
    private void playSearch(int target) {
        List<KeyFrame> kf = new ArrayList<>();
        double t = 0;
        int found = -1;

        Node cur = head; int i = 0;
        while (cur != null) {
            int fi = i;
            kf.add(kfAt(t, () -> {
                redraw(-1, fi, -1);          // blue cursor
                setStatus("Checking index " + fi + " …");
            }));
            t += 0.55;
            if (cur.data == target) { found = i; break; }
            cur = cur.next; i++;
        }

        if (found >= 0) {
            int ff = found;
            kf.add(kfAt(t, () -> {
                redraw(ff, -1, -1);          // orange = found
                setStatus("✓ Found [" + target + "] at index " + ff);
            }));
        } else {
            kf.add(kfAt(t, () -> {
                redraw(-1, -1, -1);
                setStatus("✗ [" + target + "] not found in list");
            }));
        }
        runTimeline(kf);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Animation — Traverse
    // ══════════════════════════════════════════════════════════════════════════
    private void playTraverse() {
        List<KeyFrame> kf = new ArrayList<>();
        double t = 0;
        Node cur = head; int i = 0;
        while (cur != null) {
            int fi = i; int fv = cur.data;
            kf.add(kfAt(t, () -> {
                redraw(-1, fi, -1);
                setStatus("ptr → index " + fi + "  value = " + fv);
            }));
            t += 0.5;
            cur = cur.next; i++;
        }
        int finalI = i;
        kf.add(kfAt(t, () -> { redraw(-1, -1, -1); setStatus("Traversal complete — " + finalI + " nodes visited ✓"); }));
        runTimeline(kf);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Animation — Bubble Sort
    // ══════════════════════════════════════════════════════════════════════════
    private void playBubbleSort() {
        List<KeyFrame> kf = new ArrayList<>();
        double t = 0;
        int n = size();

        for (int pass = 0; pass < n - 1; pass++) {
            Node a = head; int ai = 0;
            while (a != null && a.next != null) {
                Node b = a.next;
                int fa = ai, fb = ai + 1;
                kf.add(kfAt(t, () -> {
                    redraw(fa, fb, -1);
                    setStatus("Compare [" + fa + "] vs [" + fb + "]");
                }));
                t += 0.4;
                if (a.data > b.data) {
                    int tmp = a.data; a.data = b.data; b.data = tmp;
                    kf.add(kfAt(t, () -> {
                        redraw(fa, fb, -1);
                        setStatus("Swap [" + fa + "] ↔ [" + fb + "]");
                    }));
                    t += 0.4;
                }
                a = a.next; ai++;
            }
        }
        kf.add(kfAt(t, () -> { redraw(-1, -1, -1); setStatus("Sort complete ✓"); }));
        runTimeline(kf);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Redraw — full repaint
    //    primaryIdx   = orange highlight (insert / delete / found)
    //    secondaryIdx = blue  highlight (traversal cursor / compare B)
    //    tertiaryIdx  = (unused; reserved for sort-pass mark)
    // ══════════════════════════════════════════════════════════════════════════
    private void redraw(int primaryIdx, int secondaryIdx, int tertiaryIdx) {
        canvas.getChildren().clear();

        int sz = size();
        canvas.setPrefWidth(Math.max(900, START_X + sz * GAP + 200));
        canvas.setPrefHeight(520);

        if (head == null) {
            Text t = new Text(350, 180, "Singly Linked List is empty");
            t.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 20));
            t.setFill(Color.web("#94a3b8"));
            canvas.getChildren().add(t);
            return;
        }

        Node cur = head; int idx = 0;
        while (cur != null) {
            double x = START_X + idx * GAP;

            Color nodeColor = C_NODE_FILL;
            if (idx == primaryIdx)   nodeColor = C_HIGHLIGHT;
            if (idx == secondaryIdx) nodeColor = C_TRAVERSE;

            canvas.getChildren().addAll(makeNodeGroup(x, NODE_Y, cur.data, nodeColor, idx, true, primaryIdx == idx ? 1 : secondaryIdx == idx ? 2 : 0));

            // Arrow to next node
            if (cur.next != null) {
                double ax = x + BOX_W + PTR_W;
                double ay = NODE_Y + NODE_H / 2.0;
                double bx = x + GAP;
                Line arrow = new Line(ax, ay, bx, ay);
                arrow.setStroke(C_ARROW); arrow.setStrokeWidth(2);
                canvas.getChildren().add(arrow);
                canvas.getChildren().add(arrowHead(bx, ay, true));
            } else {
                // NULL label
                Text nullT = new Text(x + BOX_W + PTR_W + 6, NODE_Y + NODE_H / 2.0 + 6, "null");
                nullT.setFont(Font.font("Monospace", 13));
                nullT.setFill(Color.web("#ef4444"));
                canvas.getChildren().add(nullT);
                // TAIL label
                addLabel(x + BOX_W / 2.0 - 12, NODE_Y + NODE_H + 28, "TAIL", "#ef4444", 12);
            }

            // HEAD label
            if (idx == 0) addLabel(x + BOX_W / 2.0 - 14, NODE_Y - 30, "HEAD", "#22c55e", 12);

            cur = cur.next; idx++;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Node drawing helpers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns a list of JavaFX nodes for one linked-list node cell.
     * colorOverride == C_NODE_FILL → default dark look.
     */
    private List<javafx.scene.Node> makeNodeGroup(
            double x, double y, int val,
            Color color, int idx, boolean showPtrBox, int role) {

        List<javafx.scene.Node> group = new ArrayList<>();

        boolean isDefault = color.equals(C_NODE_FILL);

        // Data cell
        Rectangle dataCell = new Rectangle(x, y, BOX_W, NODE_H);
        dataCell.setArcWidth(ARC); dataCell.setArcHeight(ARC);
        dataCell.setFill(isDefault ? C_NODE_FILL : color.deriveColor(0,1,1,0.25));
        dataCell.setStroke(isDefault ? C_NODE_STROKE : color);
        dataCell.setStrokeWidth(2.5);
        if (!isDefault) dataCell.setEffect(new DropShadow(14, color));
        group.add(dataCell);

        // Data value text
        Text valText = new Text(x + BOX_W / 2.0 - 10, y + NODE_H / 2.0 + 7, String.valueOf(val));
        valText.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        valText.setFill(isDefault ? C_TEXT : color);
        group.add(valText);

        // Pointer cell
        if (showPtrBox) {
            Rectangle ptrCell = new Rectangle(x + BOX_W, y, PTR_W, NODE_H);
            ptrCell.setArcWidth(ARC); ptrCell.setArcHeight(ARC);
            ptrCell.setFill(C_PTR_FILL);
            ptrCell.setStroke(isDefault ? C_NODE_STROKE : color);
            ptrCell.setStrokeWidth(2);
            group.add(ptrCell);

            // Dot inside pointer cell
            Circle dot = new Circle(x + BOX_W + PTR_W / 2.0, y + NODE_H / 2.0, 4,
                    isDefault ? C_NODE_STROKE : color);
            group.add(dot);
        }

        // Index label above node
        if (idx >= 0) {
            Text idxText = new Text(x + BOX_W / 2.0 - 8, y - 8, "[" + idx + "]");
            idxText.setFont(Font.font("Segoe UI", 11));
            idxText.setFill(Color.web("#94a3b8"));
            group.add(idxText);
        }

        // Role label below node (ptr arrow label like VisualGo)
        if (role == 2) {
            addLabelToGroup(group, x + 4, y + NODE_H + 20, "", "#3b82f6", 11);
        }

        return group;
    }

    private Polygon arrowHead(double tipX, double tipY, boolean pointRight) {
        Polygon p = new Polygon();
        if (pointRight) p.getPoints().addAll(tipX, tipY, tipX-10, tipY-5, tipX-10, tipY+5);
        else            p.getPoints().addAll(tipX, tipY, tipX+10, tipY-5, tipX+10, tipY+5);
        p.setFill(C_ARROW);
        return p;
    }

    private void addLabel(double x, double y, String txt, String hex, int size) {
        Text t = new Text(x, y, txt);
        t.setFont(Font.font("Segoe UI", FontWeight.BOLD, size));
        t.setFill(Color.web(hex));
        canvas.getChildren().add(t);
    }

    private void addLabelToGroup(List<javafx.scene.Node> g, double x, double y, String txt, String hex, int size) {
        Text t = new Text(x, y, txt);
        t.setFont(Font.font("Segoe UI", 11));
        t.setFill(Color.web(hex));
        g.add(t);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Data-model helpers
    // ══════════════════════════════════════════════════════════════════════════
    private void insertLogic(int idx, int val) {
        Node node = new Node(val);
        if (idx == 0) { node.next = head; head = node; return; }
        Node t = head; for (int i = 0; i < idx - 1 && t.next != null; i++) t = t.next;
        node.next = t.next; t.next = node;
    }

    private void deleteLogic(int idx) {
        if (idx == 0) { head = head.next; return; }
        Node t = head; for (int i = 0; i < idx - 1 && t.next != null; i++) t = t.next;
        if (t.next != null) t.next = t.next.next;
    }

    private void appendTail(int val) {
        Node n = new Node(val);
        if (head == null) { head = n; return; }
        Node t = head; while (t.next != null) t = t.next; t.next = n;
    }

    private Node getAt(int idx) {
        Node t = head; for (int i = 0; i < idx; i++) t = t.next; return t;
    }

    private int size() { int c=0; Node t=head; while(t!=null){c++;t=t.next;} return c; }

    // ══════════════════════════════════════════════════════════════════════════
    //  Animation utilities
    // ══════════════════════════════════════════════════════════════════════════
    private KeyFrame kfAt(double secs, Runnable r) {
        return new KeyFrame(Duration.seconds(secs), e -> r.run());
    }

    private PauseTransition pauseThen(double secs, Runnable r) {
        PauseTransition p = new PauseTransition(Duration.seconds(secs));
        p.setOnFinished(e -> r.run()); return p;
    }

    private void runTimeline(List<KeyFrame> kf) {
        animation = new Timeline(); animation.getKeyFrames().addAll(kf); animation.play();
    }

    private void stopAnim() { if (animation != null) animation.stop(); }

    // ══════════════════════════════════════════════════════════════════════════
    //  Input helpers
    // ══════════════════════════════════════════════════════════════════════════
    private Integer readVal() {
        try { return Integer.parseInt(valueField.getText().trim()); }
        catch (Exception e) { err("Enter a valid integer in the Value field"); return null; }
    }

    private Integer readIdx() {
        try { return Integer.parseInt(indexField.getText().trim()); }
        catch (Exception e) { err("Enter a valid integer in the Index field"); return null; }
    }

    private void err(String msg) { setStatus("⚠ " + msg); }

    private void setStatus(String msg) {
        statusLabel.setText(msg);
        headerStatusLabel.setText(msg);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CAPTURE & RECORDING LOGIC (STREAMING FIX)
    // ══════════════════════════════════════════════════════════════════════════
    @FXML
    void takeScreenshot() {
        WritableImage snapshot = canvas.snapshot(null, null);
        BufferedImage buffered = SwingFXUtils.fromFXImage(snapshot, null);

        String downloadsDir = getDownloadsPath();
        String timestamp    = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File   outputFile   = new File(downloadsDir, "singly_linked_list_" + timestamp + ".png");

        try {
            ImageIO.write(buffered, "png", outputFile);
            System.out.println("Screenshot saved: " + outputFile.getAbsolutePath());
            buffered.flush(); // Prevent memory leak here too!
        } catch (IOException ex) {
            System.err.println("Screenshot failed: " + ex.getMessage());
        }
    }

    @FXML
    void toggleRecording() {
        if (!isRecording) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void startRecording() {
        isRecording = true;
        recordBtn.setText("⏹");
        recordBtn.setStyle(
                "-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-size: 14px;" +
                        "-fx-background-radius: 6; -fx-cursor: hand; -fx-border-color: #991b1b; -fx-border-radius: 6;"
        );

        // 1. Initialize the video file and encoder IMMEDIATELY
        try {
            String downloadsDir = getDownloadsPath();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File outputFile = new File(downloadsDir, "singly_linked_list_rec_" + timestamp + ".mp4");

            encoder = AWTSequenceEncoder.createSequenceEncoder(outputFile, RECORD_FPS);
            System.out.println("Recording started... Streaming to: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to start video encoder: " + e.getMessage());
            isRecording = false;
            return;
        }

        // 2. Start the background capture loop
        recordingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "screen-recorder");
            t.setDaemon(true);
            return t;
        });

        recordingExecutor.scheduleAtFixedRate(() -> {
            if (!isRecording) return;

            try {
                // Fetch the snapshot from the JavaFX Application Thread and WAIT for it.
                // This ensures we don't capture faster than we can encode.
                CompletableFuture<BufferedImage> futureFrame = new CompletableFuture<>();
                Platform.runLater(() -> {
                    try {
                        WritableImage frame = canvas.snapshot(null, null);
                        futureFrame.complete(SwingFXUtils.fromFXImage(frame, null));
                    } catch (Exception e) {
                        futureFrame.completeExceptionally(e);
                    }
                });

                // Blocks the background thread until JavaFX hands over the frame
                BufferedImage buffered = futureFrame.get();

                // Process and encode immediately
                int w = buffered.getWidth();
                int h = buffered.getHeight();
                int evenW = (w % 2 == 0) ? w : w + 1;
                int evenH = (h % 2 == 0) ? h : h + 1;

                BufferedImage bgrFrame = new BufferedImage(evenW, evenH, BufferedImage.TYPE_3BYTE_BGR);
                java.awt.Graphics2D g = bgrFrame.createGraphics();
                g.drawImage(buffered, 0, 0, evenW, evenH, null);
                g.dispose();

                // Encode the frame right into the file
                encoder.encodeImage(bgrFrame);

                // CRITICAL: Clear out the image data to prevent OutOfMemoryError
                bgrFrame.flush();
                buffered.flush();

            } catch (Exception e) {
                System.err.println("Dropped frame during recording: " + e.getMessage());
            }
        }, 0, 1000 / RECORD_FPS, TimeUnit.MILLISECONDS);
    }

    private void stopRecording() {
        isRecording = false;

        // 1. Stop the capture timer loop
        if (recordingExecutor != null) {
            recordingExecutor.shutdown();
            try {
                // Wait briefly for the last frame to finish encoding
                recordingExecutor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            recordingExecutor = null;
        }

        // 2. Finalize the MP4 file
        if (encoder != null) {
            try {
                encoder.finish();
                System.out.println("Recording stopped and video saved successfully.");
            } catch (IOException e) {
                System.err.println("Failed to finalize video: " + e.getMessage());
            }
            encoder = null;
        }

        // 3. Reset UI button
        Platform.runLater(() -> {
            recordBtn.setText("🎥 Record");
            recordBtn.setStyle("-fx-background-color: rgba(255,255,255,0.15); -fx-text-fill: white; -fx-font-size: 14px; -fx-background-radius: 6; -fx-cursor: hand; -fx-border-color: rgba(255,255,255,0.25); -fx-border-radius: 6;");
        });
    }

    private String getDownloadsPath() {
        String home = System.getProperty("user.home");
        Path   dl   = Paths.get(home, "Downloads");
        if (!dl.toFile().exists()) dl.toFile().mkdirs();
        return dl.toString();
    }
}