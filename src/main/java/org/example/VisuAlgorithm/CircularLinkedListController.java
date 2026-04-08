package org.example.VisuAlgorithm;

import java.util.Random;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.QuadCurve;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import org.jcodec.api.awt.AWTSequenceEncoder;

public class CircularLinkedListController {

    @FXML private Pane canvas;
    @FXML private TextField valueField;
    @FXML private TextField indexField;
    @FXML private Label statusLabel;
    @FXML private Label headerStatusLabel;

    // --- Capture buttons ---
    @FXML private Button screenshotBtn;
    @FXML private Button recordBtn;

    // Recording state
    private boolean isRecording = false;
    private ScheduledExecutorService recordingExecutor;
    private AWTSequenceEncoder encoder;
    private static final int RECORD_FPS = 30; // frames per second

    private static class Node {
        int data; Node next;
        Node(int data){ this.data=data; }
    }
    private Node head;

    private final Random rng  = new Random();
    private Timeline animation = new Timeline();

    private static final double CENTER_X = 600;
    private static final double CENTER_Y = 380;
    private static final double RADIUS   = 200;
    private static final double NODE_R   = 38;
    private static final double ARROW_OFFSET = NODE_R + 14;

    private static final Color C_DEFAULT     = Color.web("#0f172a");
    private static final Color C_NODE_FILL   = Color.web("#15152c");
    private static final Color C_STROKE      = Color.web("#38bdf8");
    private static final Color C_TEXT        = Color.web("#ffffff");
    private static final Color C_HIGHLIGHT   = Color.web("#f97316");
    private static final Color C_TRAVERSE    = Color.web("#3b82f6");
    private static final Color C_FOUND       = Color.web("#22c55e");
    private static final Color C_DELETE      = Color.web("#ef4444");
    private static final Color C_NEW         = Color.web("#a855f7");

    @FXML public void initialize() {
        if (screenshotBtn != null) {
            screenshotBtn.setText("📷 Snapshot");
            screenshotBtn.setPrefWidth(130);
            screenshotBtn.setMinWidth(130);
        }
        if (recordBtn != null) {
            recordBtn.setText("🎥 Record");
            recordBtn.setPrefWidth(130);
            recordBtn.setMinWidth(130);
        }
        redraw(-1,-1, false);
    }

    @FXML private void onBack() {
        if (isRecording) stopRecording();
        stopAnim();
        Launcher.switchScene("linked-list-view.fxml");
    }

    @FXML private void onRandom() {
        stopAnim(); head=null; int n=rng.nextInt(4)+3; Timeline timeline = new Timeline();
        for (int i = 0; i < n; i++) {
            int value = rng.nextInt(90)+10; int step = i;
            timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(step * 0.4), e -> {
                Node node = new Node(value);
                if (head == null) { head = node; node.next = head; }
                else { Node tail = getTail(); tail.next = node; node.next = head; }
                redraw(-1, -1, false); setStatus("Inserted random: " + value);
            }));
        }
        timeline.play(); setStatus("Generated "+n+" random nodes");
    }

    @FXML private void onInsertHead(){ Integer v=readVal(); if(v==null)return; stopAnim(); playInsertAt(0,v); }
    @FXML private void onInsertTail(){ Integer v=readVal(); if(v==null)return; stopAnim(); playInsertAt(size(),v); }
    @FXML private void onInsertAt() {
        Integer v=readVal(); if(v==null)return; Integer i=readIdx(); if(i==null)return;
        if(i<0||i>size()){err("Index out of range [0.."+size()+"]");return;} stopAnim(); playInsertAt(i,v);
    }
    @FXML private void onDeleteHead(){ if(head==null){err("List is empty");return;} stopAnim(); playDeleteAt(0); }
    @FXML private void onDeleteTail(){ if(head==null){err("List is empty");return;} stopAnim(); playDeleteAt(size()-1); }
    @FXML private void onDeleteAt() {
        Integer i=readIdx(); if(i==null)return; if(i<0||i>=size()){err("Index out of range");return;} stopAnim(); playDeleteAt(i);
    }
    @FXML private void onSearch(){ Integer v=readVal(); if(v==null)return; if(head==null){err("List is empty");return;} stopAnim(); playSearch(v); }
    @FXML private void onTraverse(){ if(head==null){err("List is empty");return;} stopAnim(); playTraverse(); }
    @FXML private void onSort(){ if(head==null||head.next==head){err("Need ≥ 2 nodes");return;} stopAnim(); playBubbleSort(); }
    @FXML private void onClear(){ stopAnim(); head=null; redraw(-1,-1, false); setStatus("List cleared"); }

    private void playInsertAt(int idx, int val) {
        List<KeyFrame> kf = new ArrayList<>(); double t = 0; int n = size();
        if (idx > 0) {
            for (int i = 0; i < idx; i++) {
                int cur = i; kf.add(kfAt(t, () -> { redraw(-1, cur, false); setStatus("ptr → index " + cur); })); t += 0.45;
            }
        }
        if (n == 0) {
            kf.add(kfAt(t, () -> { redraw(-1, -1, false); ghostNode(val); setStatus("Step 1: Instantiate new node"); })); t += 0.9;
        } else {
            int predIdx = (idx - 1 + n) % n; int destIdx = idx % n;
            kf.add(kfAt(t, () -> { redraw(predIdx, -1, false); ghostNode(val); setStatus("Step 1: Instantiate new node"); })); t += 0.9;
            kf.add(kfAt(t, () -> {
                redraw(predIdx, -1, false); ghostNode(val); drawTempArrow(CENTER_X, CENTER_Y, getNX(destIdx, n), getNY(destIdx, n), C_HIGHLIGHT);
                setStatus("Step 2: Link new node to destination");
            })); t += 1.0;
            kf.add(kfAt(t, () -> {
                redraw(predIdx, -1, false); ghostNode(val); drawTempArrow(CENTER_X, CENTER_Y, getNX(destIdx, n), getNY(destIdx, n), C_HIGHLIGHT); drawTempArrow(getNX(predIdx, n), getNY(predIdx, n), CENTER_X, CENTER_Y, C_HIGHLIGHT);
                setStatus("Step 3: Link predecessor to new node");
            })); t += 1.0;
            kf.add(kfAt(t, () -> {
                redraw(predIdx, -1, false); ghostNode(val); drawTempArrow(CENTER_X, CENTER_Y, getNX(destIdx, n), getNY(destIdx, n), C_HIGHLIGHT); drawTempArrow(getNX(predIdx, n), getNY(predIdx, n), CENTER_X, CENTER_Y, C_HIGHLIGHT); drawCrossOut(getNX(predIdx, n), getNY(predIdx, n), getNX(destIdx, n), getNY(destIdx, n), n);
                setStatus("Step 4: Disconnect old direct link");
            })); t += 1.0;
        }
        kf.add(kfAt(t, () -> { insertLogic(idx, val); redraw(idx, -1, false); setStatus("Inserted ["+val+"] at index "+idx+" ✓"); })); runTimeline(kf);
    }

    private void playDeleteAt(int idx) {
        List<KeyFrame> kf = new ArrayList<>(); double t = 0; int n = size();
        for (int i = 0; i < idx; i++) {
            int cur = i; kf.add(kfAt(t, () -> { redraw(-1, cur, false); setStatus("ptr → index " + cur); })); t += 0.45;
        }
        kf.add(kfAt(t, () -> { redraw(idx, -1, false); setStatus("Step 1: Identify node to delete"); })); t += 0.9;
        if (n > 1) {
            int predIdx = (idx - 1 + n) % n; int nextIdx = (idx + 1) % n;
            double targX = getNX(idx, n), targY = getNY(idx, n), aTarg = getAngle(idx, n); double ctrlX = targX + 140 * Math.cos(aTarg), ctrlY = targY + 140 * Math.sin(aTarg);
            kf.add(kfAt(t, () -> {
                redraw(idx, -1, false); drawTempCurve(getNX(predIdx, n), getNY(predIdx, n), getNX(nextIdx, n), getNY(nextIdx, n), ctrlX, ctrlY, C_HIGHLIGHT);
                setStatus("Step 2: Link predecessor directly to next node");
            })); t += 1.0;
            kf.add(kfAt(t, () -> {
                redraw(idx, -1, false); drawTempCurve(getNX(predIdx, n), getNY(predIdx, n), getNX(nextIdx, n), getNY(nextIdx, n), ctrlX, ctrlY, C_HIGHLIGHT);
                drawCrossOut(getNX(predIdx, n), getNY(predIdx, n), targX, targY, n); drawCrossOut(targX, targY, getNX(nextIdx, n), getNY(nextIdx, n), n);
                setStatus("Step 3: Disconnect node from list");
            })); t += 1.0;
        }
        kf.add(kfAt(t, () -> { deleteLogic(idx); redraw(-1, -1, false); setStatus("Deleted node ✓"); })); runTimeline(kf);
    }

    private void playSearch(int target) {
        List<KeyFrame> kf = new ArrayList<>(); double t = 0; Node cur = head; int i = 0; int found = -1;
        do {
            int fi = i; kf.add(kfAt(t, () -> { redraw(-1, fi, false); setStatus("Check index "+fi+"…"); })); t += 0.5;
            if (cur.data == target) { found = i; break; } cur = cur.next; i++;
        } while (cur != head);
        if (found >= 0) { int ff = found; kf.add(kfAt(t, () -> { redraw(ff, -1, false); setStatus("✓ Found ["+target+"] at index "+ff); })); }
        else { kf.add(kfAt(t, () -> { redraw(-1, -1, false); setStatus("✗ ["+target+"] not found"); })); }
        runTimeline(kf);
    }

    private void playTraverse() {
        List<KeyFrame> kf = new ArrayList<>(); double t = 0; Node cur = head; int i = 0;
        do {
            int fi = i, fv = cur.data; kf.add(kfAt(t, () -> { redraw(-1, fi, false); setStatus("ptr → ["+fi+"] = "+fv); })); t += 0.5;
            cur = cur.next; i++;
        } while (cur != head);
        kf.add(kfAt(t, () -> { redraw(-1, 0, true); setStatus("Back to HEAD"); })); t += 0.6; runTimeline(kf);
    }

    private void playBubbleSort() {
        List<KeyFrame> kf = new ArrayList<>(); double t = 0; int n = size();
        for (int pass = 0; pass < n - 1; pass++) {
            Node a = head; int ai = 0;
            for (int j = 0; j < n - 1; j++) {
                Node b = a.next; int fa = ai, fb = (ai+1)%n;
                kf.add(kfAt(t, () -> { redraw(fa, fb, false); setStatus("Compare ["+fa+"] vs ["+fb+"]"); })); t += 0.38;
                if (a.data > b.data) {
                    int tmp = a.data; a.data = b.data; b.data = tmp;
                    kf.add(kfAt(t, () -> { redraw(fa, fb, false); setStatus("Swapped!"); })); t += 0.38;
                }
                a = a.next; ai++;
            }
        }
        kf.add(kfAt(t, () -> { redraw(-1, -1, false); setStatus("Sort complete ✓"); })); runTimeline(kf);
    }

    private double getAngle(int i, int size) { return 2 * Math.PI * i / size - Math.PI / 2; }
    private double getNX(int i, int size)    { return CENTER_X + RADIUS * Math.cos(getAngle(i, size)); }
    private double getNY(int i, int size)    { return CENTER_Y + RADIUS * Math.sin(getAngle(i, size)); }

    private void redraw(int primaryIndex, int secondaryIndex, boolean arcHighlight) {
        canvas.getChildren().clear(); int size = size();
        if (head == null) {
            Text t = new Text(350, 180, "Circular Linked List is empty"); t.setFont(Font.font("System", 22)); t.setFill(Color.web("#64748b")); canvas.getChildren().add(t); return;
        }
        if (size == 1) {
            double nx = getNX(0,1), ny = getNY(0,1); QuadCurve loop = new QuadCurve();
            loop.setStartX(nx + ARROW_OFFSET * 0.6); loop.setStartY(ny + ARROW_OFFSET * 0.6); loop.setControlX(nx + 100); loop.setControlY(ny + 150); loop.setEndX(nx - ARROW_OFFSET * 0.6); loop.setEndY(ny + ARROW_OFFSET * 0.6); loop.setFill(null); loop.setStroke(Color.web(arcHighlight ? "#3b82f6" : "#38bdf8", 0.8)); loop.setStrokeWidth(2.5);
            Polygon arrow = new Polygon(); double angle = Math.atan2(loop.getEndY() - loop.getControlY(), loop.getEndX() - loop.getControlX()); double len = 12;
            arrow.getPoints().addAll(loop.getEndX(), loop.getEndY(), loop.getEndX() - len * Math.cos(angle - Math.PI/6), loop.getEndY() - len * Math.sin(angle - Math.PI/6), loop.getEndX() - len * Math.cos(angle + Math.PI/6), loop.getEndY() - len * Math.sin(angle + Math.PI/6));
            arrow.setFill(Color.web(arcHighlight ? "#3b82f6" : "#38bdf8", 0.9)); canvas.getChildren().addAll(loop, arrow);
        } else {
            for (int i = 0; i < size; i++) {
                int next = (i + 1) % size; String color = (arcHighlight && i == size - 1) ? "#3b82f6" : "#38bdf8";
                drawArrowBetweenNodes(getNX(i,size), getNY(i,size), getNX(next,size), getNY(next,size), color);
            }
        }
        Node temp = head;
        for (int i = 0; i < size; i++) {
            double x = getNX(i, size), y = getNY(i, size); String fill = "#15152c";
            if (i == secondaryIndex) fill = "#3b82f6"; if (i == primaryIndex) fill = "#f97316";
            Circle c = new Circle(x, y, NODE_R); c.setFill(Color.web(fill)); c.setStroke(C_STROKE); c.setStrokeWidth(3); c.setEffect(new DropShadow(15, C_STROKE));
            String valStr = String.valueOf(temp.data); Text t = new Text(x - (valStr.length() > 1 ? 10 : 6), y + 7, valStr);
            t.setFont(Font.font("System", FontWeight.BOLD, 18)); t.setFill(Color.WHITE); canvas.getChildren().addAll(c, t);
            double labelDist = RADIUS + NODE_R + 24, lx = CENTER_X + labelDist * Math.cos(getAngle(i, size)), ly = CENTER_Y + labelDist * Math.sin(getAngle(i, size));
            Text idxText = new Text(lx - 8, ly + 5, "[" + i + "]"); idxText.setFont(Font.font("System", 13)); idxText.setFill(Color.web("#93c5fd")); canvas.getChildren().add(idxText);
            if (i == 0) { Text ht = new Text(lx - 20, ly - 14, "HEAD"); ht.setFont(Font.font("System", 14)); ht.setFill(Color.web("#2dd4bf")); canvas.getChildren().add(ht); }
            if (i == size - 1 || size == 1) { Text tt = new Text(lx - 16, ly + 24, "TAIL"); tt.setFont(Font.font("System", 14)); tt.setFill(Color.web("#f43f5e")); canvas.getChildren().add(tt); }
            temp = temp.next;
        }
    }

    private void ghostNode(int val) {
        Circle c = new Circle(CENTER_X, CENTER_Y, NODE_R); c.setFill(C_NEW); c.setStroke(C_STROKE); c.setStrokeWidth(3); c.setEffect(new DropShadow(15, C_STROKE));
        String valStr = String.valueOf(val); Text t = new Text(CENTER_X - (valStr.length() > 1 ? 10 : 6), CENTER_Y + 7, valStr);
        t.setFont(Font.font("System", FontWeight.BOLD, 18)); t.setFill(Color.WHITE); canvas.getChildren().addAll(c, t);
    }

    private void drawArrowBetweenNodes(double x1, double y1, double x2, double y2, String color) {
        double dx = x2 - x1, dy = y2 - y1, dist = Math.sqrt(dx * dx + dy * dy); if(dist < 1) return;
        double ux = dx / dist, uy = dy / dist, sx = x1 + ux * ARROW_OFFSET, sy = y1 + uy * ARROW_OFFSET, ex = x2 - ux * ARROW_OFFSET, ey = y2 - uy * ARROW_OFFSET;
        Line l = new Line(sx, sy, ex, ey); l.setStroke(Color.web(color)); l.setStrokeWidth(2.5);
        double angle = Math.atan2(dy, dx), len = 12; Polygon arrow = new Polygon();
        arrow.getPoints().addAll(ex, ey, ex - len * Math.cos(angle - Math.PI/6), ey - len * Math.sin(angle - Math.PI/6), ex - len * Math.cos(angle + Math.PI/6), ey - len * Math.sin(angle + Math.PI/6));
        arrow.setFill(Color.web(color)); canvas.getChildren().addAll(l, arrow);
    }

    private void drawTempArrow(double cx1, double cy1, double cx2, double cy2, Color c) {
        double dx = cx2 - cx1, dy = cy2 - cy1, dist = Math.sqrt(dx*dx + dy*dy); if(dist < 1) return;
        double ux = dx/dist, uy = dy/dist, sx = cx1 + ux * ARROW_OFFSET, sy = cy1 + uy * ARROW_OFFSET, ex = cx2 - ux * ARROW_OFFSET, ey = cy2 - uy * ARROW_OFFSET;
        Line l = new Line(sx, sy, ex, ey); l.setStroke(c); l.setStrokeWidth(2.5);
        double angle = Math.atan2(dy, dx), len = 12; Polygon arrow = new Polygon();
        arrow.getPoints().addAll(ex, ey, ex - len * Math.cos(angle - Math.PI/6), ey - len * Math.sin(angle - Math.PI/6), ex - len * Math.cos(angle + Math.PI/6), ey - len * Math.sin(angle + Math.PI/6));
        arrow.setFill(c); canvas.getChildren().addAll(l, arrow);
    }

    private void drawTempCurve(double cx1, double cy1, double cx2, double cy2, double ctrlX, double ctrlY, Color col) {
        double dx1 = ctrlX - cx1, dy1 = ctrlY - cy1, d1 = Math.sqrt(dx1*dx1 + dy1*dy1), sx = cx1 + (dx1/d1) * ARROW_OFFSET, sy = cy1 + (dy1/d1) * ARROW_OFFSET;
        double dx2 = cx2 - ctrlX, dy2 = cy2 - ctrlY, d2 = Math.sqrt(dx2*dx2 + dy2*dy2), ex = cx2 - (dx2/d2) * ARROW_OFFSET, ey = cy2 - (dy2/d2) * ARROW_OFFSET;
        QuadCurve q = new QuadCurve(sx, sy, ctrlX, ctrlY, ex, ey); q.setFill(null); q.setStroke(col); q.setStrokeWidth(2.5); q.getStrokeDashArray().addAll(6d, 6d);
        double angle = Math.atan2(dy2, dx2), len = 12; Polygon arrow = new Polygon();
        arrow.getPoints().addAll(ex, ey, ex - len * Math.cos(angle - Math.PI/6), ey - len * Math.sin(angle - Math.PI/6), ex - len * Math.cos(angle + Math.PI/6), ey - len * Math.sin(angle + Math.PI/6));
        arrow.setFill(col); canvas.getChildren().addAll(q, arrow);
    }

    private void drawCrossOut(double x1, double y1, double x2, double y2, int n) {
        double mx = (x1+x2)/2, my = (y1+y2)/2; if(n == 1) { mx = x1 + 40; my = y1 + 70; }
        Line l1 = new Line(mx-10, my-10, mx+10, my+10), l2 = new Line(mx-10, my+10, mx+10, my-10);
        l1.setStroke(C_DELETE); l1.setStrokeWidth(3.5); l2.setStroke(C_DELETE); l2.setStrokeWidth(3.5);
        canvas.getChildren().addAll(l1, l2);
    }

    private void insertLogic(int idx, int val){
        Node n = new Node(val); if(head == null){ head = n; n.next = head; return; }
        if(idx == 0){ Node tail = getTail(); n.next = head; tail.next = n; head = n; return; }
        Node t = head; for(int i=0; i<idx-1; i++) t = t.next; n.next = t.next; t.next = n;
    }

    private void deleteLogic(int idx){
        if(head == null) return; if(head.next == head){ head = null; return; }
        Node tail = getTail(); if(idx == 0){ head = head.next; tail.next = head; return; }
        Node t = head; for(int i=0; i<idx-1; i++) t = t.next; t.next = t.next.next;
        if(idx==size())tail.next=head;
    }

    private Node getTail(){ Node t = head; while(t.next != head) t = t.next; return t; }
    private int size(){ if(head==null) return 0; int c=0; Node t=head; do{c++; t=t.next;} while(t!=head); return c; }
    private Node getAt(int i){ Node t=head; for(int j=0; j<i; j++) t=t.next; return t; }
    private KeyFrame kfAt(double s, Runnable r){ return new KeyFrame(Duration.seconds(s), e -> r.run()); }
    private void runTimeline(List<KeyFrame> kf){ animation = new Timeline(); animation.getKeyFrames().addAll(kf); animation.play(); }
    private void stopAnim(){ if(animation != null) animation.stop(); }
    private Integer readVal(){ try{ return Integer.parseInt(valueField.getText().trim()); } catch(Exception e){ return null; } }
    private Integer readIdx(){ try{ return Integer.parseInt(indexField.getText().trim()); } catch(Exception e){ return null; } }
    private void err(String m){ setStatus("⚠ " + m); }
    private void setStatus(String m){ statusLabel.setText(m); headerStatusLabel.setText(m); }

    // ══════════════════════════════════════════════════════════════════════════
    //  CAPTURE & RECORDING LOGIC
    // ══════════════════════════════════════════════════════════════════════════
    @FXML
    void takeScreenshot() {
        WritableImage snapshot = canvas.snapshot(null, null);
        BufferedImage buffered = SwingFXUtils.fromFXImage(snapshot, null);

        String downloadsDir = getDownloadsPath();
        String timestamp    = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File   outputFile   = new File(downloadsDir, "circular_linked_list_" + timestamp + ".png");

        try {
            ImageIO.write(buffered, "png", outputFile);
            System.out.println("Screenshot saved: " + outputFile.getAbsolutePath());
            setStatus("Screenshot saved: " + outputFile.getName() + " ✓");
            buffered.flush();
        } catch (IOException ex) {
            System.err.println("Screenshot failed: " + ex.getMessage());
            err("Failed to save screenshot.");
        }
    }

    @FXML
    void toggleRecording() {
        if (!isRecording) startRecording();
        else stopRecording();
    }

    private void startRecording() {
        isRecording = true;
        recordBtn.setText("⏹");
        recordBtn.setStyle(
                "-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-size: 14px;" +
                        "-fx-background-radius: 6; -fx-cursor: hand; -fx-border-color: #991b1b; -fx-border-radius: 6;"
        );

        try {
            String downloadsDir = getDownloadsPath();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File outputFile = new File(downloadsDir, "circular_linked_list_rec_" + timestamp + ".mp4");

            encoder = AWTSequenceEncoder.createSequenceEncoder(outputFile, RECORD_FPS);
            System.out.println("Recording started... Streaming to: " + outputFile.getAbsolutePath());
            setStatus("Recording started...");
        } catch (IOException e) {
            System.err.println("Failed to start video encoder: " + e.getMessage());
            err("Failed to start recording.");
            isRecording = false;
            return;
        }

        recordingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "screen-recorder");
            t.setDaemon(true);
            return t;
        });

        recordingExecutor.scheduleAtFixedRate(() -> {
            if (!isRecording) return;

            try {
                CompletableFuture<BufferedImage> futureFrame = new CompletableFuture<>();
                Platform.runLater(() -> {
                    try {
                        WritableImage frame = canvas.snapshot(null, null);
                        futureFrame.complete(SwingFXUtils.fromFXImage(frame, null));
                    } catch (Exception e) {
                        futureFrame.completeExceptionally(e);
                    }
                });

                BufferedImage buffered = futureFrame.get();

                int w = buffered.getWidth();
                int h = buffered.getHeight();
                int evenW = (w % 2 == 0) ? w : w + 1;
                int evenH = (h % 2 == 0) ? h : h + 1;

                BufferedImage bgrFrame = new BufferedImage(evenW, evenH, BufferedImage.TYPE_3BYTE_BGR);
                java.awt.Graphics2D g = bgrFrame.createGraphics();
                g.drawImage(buffered, 0, 0, evenW, evenH, null);
                g.dispose();

                encoder.encodeImage(bgrFrame);

                bgrFrame.flush();
                buffered.flush();

            } catch (Exception e) {
                System.err.println("Dropped frame during recording: " + e.getMessage());
            }
        }, 0, 1000 / RECORD_FPS, TimeUnit.MILLISECONDS);
    }

    private void stopRecording() {
        isRecording = false;

        if (recordingExecutor != null) {
            recordingExecutor.shutdown();
            try {
                recordingExecutor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            recordingExecutor = null;
        }

        if (encoder != null) {
            try {
                encoder.finish();
                System.out.println("Recording stopped and video saved successfully.");
                setStatus("Recording stopped and saved! ✓");
            } catch (IOException e) {
                System.err.println("Failed to finalize video: " + e.getMessage());
                err("Failed to save recording.");
            }
            encoder = null;
        }

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