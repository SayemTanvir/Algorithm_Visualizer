package org.example.VisuAlgorithm;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.util.Duration;

// Capture/Recording imports
import javafx.scene.image.WritableImage;
import javafx.scene.SnapshotParameters;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;
import org.jcodec.api.awt.AWTSequenceEncoder;

public class DoublyLinkedListController {

    @FXML private Pane      canvas;
    @FXML private TextField valueField;
    @FXML private TextField indexField;
    @FXML private Label     statusLabel;
    @FXML private Label     headerStatusLabel;

    // --- Capture buttons ---
    @FXML private Button screenshotBtn;
    @FXML private Button recordBtn;

    // Recording state
    private volatile boolean isRecording = false;
    private volatile boolean isCapturing = false; // Anti-flood flag
    private ScheduledExecutorService recordingExecutor;
    private AWTSequenceEncoder encoder;
    private BlockingQueue<BufferedImage> frameQueue;
    private static final int RECORD_FPS = 30; // frames per second

    private static class Node {
        int  data; Node prev, next;
        Node(int data) { this.data = data; }
    }
    private Node head;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final double START_X = 60;
    private static final double NODE_Y  = 230;
    private static final double PREV_W  = 32;
    private static final double DATA_W  = 64;
    private static final double NEXT_W  = 32;
    private static final double NODE_H  = 50;
    private static final double TOTAL_W = PREV_W + DATA_W + NEXT_W;   // 128
    private static final double GAP     = 180;
    private static final double ARC     = 10;

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color C_DEFAULT     = Color.web("#0f172a");
    private static final Color C_NODE_FILL   = Color.web("#0f172a");
    private static final Color C_STROKE      = Color.web("#38bdf8");
    private static final Color C_PTR_FILL    = Color.web("#1e293b");
    private static final Color C_TEXT        = Color.web("#e2e8f0");
    private static final Color C_FWD         = Color.web("#38bdf8");
    private static final Color C_BWD         = Color.web("#a78bfa");
    private static final Color C_HIGHLIGHT   = Color.web("#f97316");
    private static final Color C_TRAVERSE    = Color.web("#3b82f6");
    private static final Color C_FOUND       = Color.web("#22c55e");
    private static final Color C_DELETE      = Color.web("#ef4444");
    private static final Color C_NEW         = Color.web("#a855f7");

    private final Random   rng   = new Random();
    private       Timeline anim  = new Timeline();

    @FXML public void initialize() {
        screenshotBtn.setText("📷 Snapshot");
        recordBtn.setText("🎥 Record");
        screenshotBtn.setPrefWidth(130);
        screenshotBtn.setMinWidth(130);
        recordBtn.setPrefWidth(130);
        recordBtn.setMinWidth(130);

        redraw(-1, -1);
    }

    @FXML private void onBack() {
        if (isRecording) stopRecording();
        stopAnim();
        Launcher.switchScene("linked-list-view.fxml");
    }

    @FXML private void onRandom() {
        stopAnim(); head = null;
        int count = rng.nextInt(4) + 3;
        Timeline timeline = new Timeline();
        for (int i = 0; i < count; i++) {
            int v = rng.nextInt(90) + 10;
            int step = i;
            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(step * 0.4), e -> {
                        Node node = new Node(v);
                        if (head == null) head = node;
                        else {
                            Node temp = head;
                            while (temp.next != null) temp = temp.next;
                            temp.next = node; node.prev = temp;
                        }
                        redraw(-1, -1);
                        setStatus("Inserted random: " + v);
                    })
            );
        }
        timeline.play(); setStatus("Generated " + count + " random nodes");
    }

    @FXML private void onInsertHead() { Integer v = readVal(); if (v==null) return; stopAnim(); playInsertAt(0, v); }
    @FXML private void onInsertTail() { Integer v = readVal(); if (v==null) return; stopAnim(); playInsertAt(size(), v); }
    @FXML private void onInsertAt() {
        Integer v = readVal(); if (v==null) return;
        Integer i = readIdx(); if (i==null) return;
        if (i<0||i>size()){err("Index out of range [0.."+size()+"]");return;}
        stopAnim(); playInsertAt(i, v);
    }
    @FXML private void onDeleteHead() { if (head==null){err("List is empty");return;} stopAnim(); playDeleteAt(0); }
    @FXML private void onDeleteTail() { if (head==null){err("List is empty");return;} stopAnim(); playDeleteAt(size()-1); }
    @FXML private void onDeleteAt() {
        Integer i = readIdx(); if (i==null) return;
        if (i<0||i>=size()){err("Index out of range");return;} stopAnim(); playDeleteAt(i);
    }
    @FXML private void onSearch() { Integer v = readVal(); if (v==null) return; if (head==null){err("List is empty");return;} stopAnim(); playSearch(v); }
    @FXML private void onTraverse() { if (head==null){err("List is empty");return;} stopAnim(); playTraverse(); }
    @FXML private void onSort() { if (head==null||head.next==null){err("Need ≥ 2 nodes");return;} stopAnim(); playBubbleSort(); }
    @FXML private void onClear() { stopAnim(); head=null; redraw(-1,-1); setStatus("List cleared"); }

    private void playInsertAt(int idx, int val) {
        List<KeyFrame> kf = new ArrayList<>(); double t = 0;
        if (idx > 0) {
            for (int i = 0; i < idx; i++) {
                int cur = i;
                kf.add(kfAt(t, () -> { redraw(-1, cur); setStatus("Traversing to find predecessor..."); }));
                t += 0.45;
            }
        }
        if (idx == 0) {
            kf.add(kfAt(t, () -> { redraw(-1,-1); ghostNode(idx, val); setStatus("Step 1: Instantiate new node"); })); t += 0.9;
            if (head != null) {
                kf.add(kfAt(t, () -> {
                    redraw(-1,-1); ghostNode(idx, val);
                    drawTempArrow(START_X + TOTAL_W, NODE_Y - 80 + 14, START_X, NODE_Y + 14, C_HIGHLIGHT);
                    drawTempArrow(START_X, NODE_Y + NODE_H - 14, START_X + TOTAL_W, NODE_Y - 80 + NODE_H - 14, C_HIGHLIGHT);
                    setStatus("Step 2: Link new node's prev/next to current HEAD");
                })); t += 1.0;
            }
        } else {
            int p = idx - 1;
            kf.add(kfAt(t, () -> { redraw(p,-1); ghostNode(idx, val); setStatus("Step 1: Instantiate new node"); })); t += 0.9;
            kf.add(kfAt(t, () -> {
                redraw(p,-1); ghostNode(idx, val);
                if (idx < size()) {
                    drawTempArrow(START_X + idx*GAP + TOTAL_W, NODE_Y - 80 + 14, START_X + idx*GAP, NODE_Y + 14, C_HIGHLIGHT);
                    drawTempArrow(START_X + idx*GAP, NODE_Y + NODE_H - 14, START_X + idx*GAP + TOTAL_W, NODE_Y - 80 + NODE_H - 14, C_HIGHLIGHT);
                } else drawTempArrow(START_X + idx*GAP + TOTAL_W, NODE_Y - 80 + 14, START_X + idx*GAP + TOTAL_W + 30, NODE_Y - 80 + 14, C_HIGHLIGHT);
                setStatus("Step 2: Link new node to its next node");
            })); t += 1.0;
            kf.add(kfAt(t, () -> {
                redraw(p,-1); ghostNode(idx, val);
                if (idx < size()) {
                    drawTempArrow(START_X + idx*GAP + TOTAL_W, NODE_Y - 80 + 14, START_X + idx*GAP, NODE_Y + 14, C_HIGHLIGHT);
                    drawTempArrow(START_X + idx*GAP, NODE_Y + NODE_H - 14, START_X + idx*GAP + TOTAL_W, NODE_Y - 80 + NODE_H - 14, C_HIGHLIGHT);
                } else drawTempArrow(START_X + idx*GAP + TOTAL_W, NODE_Y - 80 + 14, START_X + idx*GAP + TOTAL_W + 30, NODE_Y - 80 + 14, C_HIGHLIGHT);
                drawTempArrow(START_X + p*GAP + TOTAL_W, NODE_Y + 14, START_X + idx*GAP, NODE_Y - 80 + 14, C_HIGHLIGHT);
                drawTempArrow(START_X + idx*GAP, NODE_Y - 80 + NODE_H - 14, START_X + p*GAP + TOTAL_W, NODE_Y + NODE_H - 14, C_HIGHLIGHT);
                setStatus("Step 3: Link predecessor to new node");
            })); t += 1.0;
            kf.add(kfAt(t, () -> {
                redraw(p,-1); ghostNode(idx, val);
                if (idx < size()) {
                    drawTempArrow(START_X + idx*GAP + TOTAL_W, NODE_Y - 80 + 14, START_X + idx*GAP, NODE_Y + 14, C_HIGHLIGHT);
                    drawTempArrow(START_X + idx*GAP, NODE_Y + NODE_H - 14, START_X + idx*GAP + TOTAL_W, NODE_Y - 80 + NODE_H - 14, C_HIGHLIGHT);
                } else drawTempArrow(START_X + idx*GAP + TOTAL_W, NODE_Y - 80 + 14, START_X + idx*GAP + TOTAL_W + 30, NODE_Y - 80 + 14, C_HIGHLIGHT);
                drawTempArrow(START_X + p*GAP + TOTAL_W, NODE_Y + 14, START_X + idx*GAP, NODE_Y - 80 + 14, C_HIGHLIGHT);
                drawTempArrow(START_X + idx*GAP, NODE_Y - 80 + NODE_H - 14, START_X + p*GAP + TOTAL_W, NODE_Y + NODE_H - 14, C_HIGHLIGHT);
                if (idx < size()) {
                    double crossX = START_X + p*GAP + TOTAL_W + (GAP - TOTAL_W)/2.0;
                    drawCrossOut(crossX, NODE_Y + 14);
                    drawCrossOut(crossX, NODE_Y + NODE_H - 14);
                }
                setStatus("Step 4: Disconnect old direct links");
            })); t += 1.0;
        }
        kf.add(kfAt(t, () -> { insertLogic(idx, val); redraw(idx, -1); setStatus("Insertion complete ✓"); }));
        run(kf);
    }

    private void playDeleteAt(int idx) {
        List<KeyFrame> kf = new ArrayList<>(); double t = 0;
        if(idx!=size()-1) {
            for (int i = 0; i < idx; i++) {
                int cur = i;
                kf.add(kfAt(t, () -> {
                    redraw(-1, cur);
                    setStatus("Traversing to target...");
                }));
                t += 0.45;

            }
        }
        if (idx == 0) {
            kf.add(kfAt(t, () -> { redraw(0,-1); setStatus("Step 1: Identify HEAD to delete"); })); t += 0.9;
            if (size() > 1) {
                kf.add(kfAt(t, () -> {
                    redraw(0,-1);
                    double crossX = START_X + TOTAL_W + (GAP - TOTAL_W)/2.0;
                    drawCrossOut(crossX, NODE_Y + 14);
                    drawCrossOut(crossX, NODE_Y + NODE_H - 14);
                    setStatus("Step 2: Disconnect HEAD from next node");
                })); t += 0.9;
            }
        } else {
            int p = idx - 1;
            kf.add(kfAt(t, () -> { redraw(idx,-1); setStatus("Step 1: Identify node to delete"); })); t += 0.9;
            kf.add(kfAt(t, () -> {
                redraw(idx,-1);
                if (idx < size() - 1) {
                    drawTempCurve(START_X + p*GAP + TOTAL_W, NODE_Y + 14, START_X + (idx+1)*GAP, NODE_Y + 14, NODE_Y - 50, C_HIGHLIGHT);
                    drawTempCurve(START_X + (idx+1)*GAP, NODE_Y + NODE_H - 14, START_X + p*GAP + TOTAL_W, NODE_Y + NODE_H - 14, NODE_Y + NODE_H + 50, C_HIGHLIGHT);
                } else {
                    drawTempArrow(START_X + p*GAP + TOTAL_W, NODE_Y + 14, START_X + p*GAP + TOTAL_W + 40, NODE_Y - 20, C_HIGHLIGHT);
                    addLabel(START_X + p*GAP + TOTAL_W + 45, NODE_Y - 15, "null", "#ef4444", 13);
                }
                setStatus("Step 2: Link predecessor directly to next node");
            })); t += 1.0;
            kf.add(kfAt(t, () -> {
                redraw(idx,-1);
                if (idx < size() - 1) {
                    drawTempCurve(START_X + p*GAP + TOTAL_W, NODE_Y + 14, START_X + (idx+1)*GAP, NODE_Y + 14, NODE_Y - 50, C_HIGHLIGHT);
                    drawTempCurve(START_X + (idx+1)*GAP, NODE_Y + NODE_H - 14, START_X + p*GAP + TOTAL_W, NODE_Y + NODE_H - 14, NODE_Y + NODE_H + 50, C_HIGHLIGHT);
                } else {
                    drawTempArrow(START_X + p*GAP + TOTAL_W, NODE_Y + 14, START_X + p*GAP + TOTAL_W + 40, NODE_Y - 20, C_HIGHLIGHT);
                    addLabel(START_X + p*GAP + TOTAL_W + 45, NODE_Y - 15, "null", "#ef4444", 13);
                }
                double crossInX = START_X + p*GAP + TOTAL_W + (GAP - TOTAL_W)/2.0;
                drawCrossOut(crossInX, NODE_Y + 14);
                drawCrossOut(crossInX, NODE_Y + NODE_H - 14);
                if (idx < size() - 1) {
                    double crossOutX = START_X + idx*GAP + TOTAL_W + (GAP - TOTAL_W)/2.0;
                    drawCrossOut(crossOutX, NODE_Y + 14);
                    drawCrossOut(crossOutX, NODE_Y + NODE_H - 14);
                }
                setStatus("Step 3: Disconnect node from list");
            })); t += 1.0;
        }
        kf.add(kfAt(t, () -> {
            int removed = getAt(idx).data; deleteLogic(idx); redraw(-1,-1);
            setStatus("Node [" + removed + "] successfully deleted ✓");
        }));
        run(kf);
    }

    private void playSearch(int target) {
        List<KeyFrame> kf = new ArrayList<>();  double t=0;
        Node cur=head; int i=0; int found=-1;
        while (cur!=null) {
            int fi=i;
            kf.add(kfAt(t, () -> { redraw(-1,fi); setStatus("Checking index "+fi+"…"); }));  t+=0.55;
            if (cur.data==target){found=i;break;}
            cur=cur.next; i++;
        }
        if (found>=0){ int ff=found; kf.add(kfAt(t, () -> { redraw(ff,-1); setStatus("✓ Found ["+target+"] at index "+ff); }));
        } else { kf.add(kfAt(t, () -> { redraw(-1,-1); setStatus("✗ ["+target+"] not found"); })); }
        run(kf);
    }

    private void playTraverse() {
        List<KeyFrame> kf = new ArrayList<>();  double t=0;
        Node cur=head; int i=0;
        while(cur!=null){ int fi=i; int fv=cur.data;
            kf.add(kfAt(t,()->{redraw(-1,fi);setStatus("ptr → ["+fi+"] = "+fv);})); t+=0.5;
            cur=cur.next; i++;
        }
        int finalI = i; kf.add(kfAt(t,()->{redraw(-1,-1);setStatus("Traversal complete — "+ finalI +" nodes ✓");})); run(kf);
    }

    private void playBubbleSort() {
        List<KeyFrame> kf = new ArrayList<>();  double t=0; int n=size();
        for(int pass=0;pass<n-1;pass++){
            Node a=head; int ai=0;
            while(a!=null&&a.next!=null){
                Node b=a.next; int fa=ai,fb=ai+1;
                kf.add(kfAt(t,()->{redraw(fa,fb);setStatus("Compare ["+fa+"] vs ["+fb+"]");})); t+=0.4;
                if(a.data>b.data){
                    int tmp=a.data;a.data=b.data;b.data=tmp;
                    kf.add(kfAt(t,()->{redraw(fa,fb);setStatus("Swap ["+fa+"] ↔ ["+fb+"]");})); t+=0.4;
                }
                a=a.next; ai++;
            }
        }
        kf.add(kfAt(t,()->{redraw(-1,-1);setStatus("Sort complete ✓");})); run(kf);
    }

    private void redraw(int primaryIdx, int secondaryIdx) {
        canvas.getChildren().clear(); int sz=size(); canvas.setPrefWidth(Math.max(900, START_X+sz*GAP+200)); canvas.setPrefHeight(560);
        if (head==null) {
            Text t=new Text(350,180,"Doubly Linked List is empty"); t.setFont(Font.font("System", FontWeight.NORMAL, 20));
            t.setFill(Color.web("#94a3b8")); canvas.getChildren().add(t); return;
        }
        Node cur=head; int idx=0;
        while(cur!=null){
            double x=START_X+idx*GAP;
            Color col = (idx==primaryIdx)?C_HIGHLIGHT:(idx==secondaryIdx)?C_TRAVERSE:C_DEFAULT;
            drawNode(x, NODE_Y, cur.data, col, idx);
            if(cur.next!=null){
                double ax=x+TOTAL_W, bx=x+GAP, ay=NODE_Y+14;
                drawArrow(ax,ay,bx,ay,C_FWD,true);
                double by2=NODE_Y+NODE_H-14; drawArrow(bx,by2,ax,by2,C_BWD,false);
            } else {
                Text n=new Text(x+TOTAL_W+6,NODE_Y+NODE_H/2.0+6,"null"); n.setFont(Font.font("Monospace",14)); n.setFill(C_DELETE); canvas.getChildren().add(n);
                addLabel(x+DATA_W/2.0+PREV_W-14,NODE_Y+NODE_H+28,"TAIL","#ef4444",12);
            }
            if(idx==0){
                Text n=new Text(START_X-46,NODE_Y+NODE_H/2.0+6,"null"); n.setFont(Font.font("Monospace",14)); n.setFill(C_DELETE); canvas.getChildren().add(n);
                addLabel(x+PREV_W+DATA_W/2.0-14,NODE_Y-30,"HEAD","#22c55e",12);
            }
            cur=cur.next; idx++;
        }
    }

    private void ghostNode(int idx, int val) {
        double x=START_X+idx*GAP, y=NODE_Y-80; drawNode(x,y,val,C_NEW,idx); addLabel(x - 10, y - 10, "NEW NODE", "#a855f7", 12);
    }

    private void drawNode(double x, double y, int val, Color color, int idx) {
        boolean isDefault = color.equals(C_DEFAULT);
        Rectangle pv=new Rectangle(x,y,PREV_W,NODE_H); style(pv, C_PTR_FILL, isDefault?C_STROKE:color);
        Rectangle dc=new Rectangle(x+PREV_W,y,DATA_W,NODE_H); style(dc, isDefault?C_NODE_FILL:color.deriveColor(0,1,1,0.25), isDefault?C_STROKE:color);
        if(!isDefault) dc.setEffect(new DropShadow(14,color));
        Rectangle nx=new Rectangle(x+PREV_W+DATA_W,y,NEXT_W,NODE_H); style(nx, C_PTR_FILL, isDefault?C_STROKE:color);
        Circle dp=new Circle(x+PREV_W/2.0,y+NODE_H/2.0,4, isDefault?C_STROKE:color);
        Text tv=new Text(x+PREV_W+DATA_W/2.0-10,y+NODE_H/2.0+7,String.valueOf(val)); tv.setFont(Font.font("System",FontWeight.BOLD,18)); tv.setFill(isDefault?C_TEXT:color);
        Circle dn=new Circle(x+PREV_W+DATA_W+NEXT_W/2.0,y+NODE_H/2.0,4, isDefault?C_STROKE:color);
        Text idxT=new Text(x+PREV_W+DATA_W/2.0-8,y-10,"["+idx+"]"); idxT.setFont(Font.font("System", 13)); idxT.setFill(Color.web("#93c5fd"));
        canvas.getChildren().addAll(pv,dc,nx,dp,tv,dn,idxT);
    }

    private void style(Rectangle r, Color fill, Color stroke){ r.setArcWidth(ARC); r.setArcHeight(ARC); r.setFill(fill); r.setStroke(stroke); r.setStrokeWidth(2.5); }
    private void drawArrow(double x1,double y1,double x2,double y2,Color color,boolean rightHead){
        Line ln=new Line(x1,y1,x2,y2); ln.setStroke(color); ln.setStrokeWidth(2.5); canvas.getChildren().add(ln);
        Polygon p=new Polygon(); if(rightHead) p.getPoints().addAll(x2,y2,x2-10,y2-5,x2-10,y2+5); else p.getPoints().addAll(x2,y2,x2+10,y2-5,x2+10,y2+5); p.setFill(color); canvas.getChildren().add(p); ln.toBack(); p.toBack();
    }
    private void drawTempArrow(double sx, double sy, double ex, double ey, Color color) {
        Line line = new Line(sx, sy, ex, ey); line.setStroke(color); line.setStrokeWidth(2.5);
        double dx = ex - sx, dy = ey - sy, angle = Math.atan2(dy, dx), len = 11;
        Polygon arrow = new Polygon(); arrow.getPoints().addAll(ex, ey, ex - len * Math.cos(angle - Math.PI/6), ey - len * Math.sin(angle - Math.PI/6), ex - len * Math.cos(angle + Math.PI/6), ey - len * Math.sin(angle + Math.PI/6));
        arrow.setFill(color); canvas.getChildren().addAll(line, arrow);
    }
    private void drawTempCurve(double sx, double sy, double ex, double ey, double cy, Color color) {
        QuadCurve qc = new QuadCurve(sx, sy, (sx+ex)/2, cy, ex, ey); qc.setFill(null); qc.setStroke(color); qc.setStrokeWidth(2.5); qc.getStrokeDashArray().addAll(6d, 6d);
        double dx = ex - (sx+ex)/2, dy = ey - cy, angle = Math.atan2(dy, dx), len = 11;
        Polygon arrow = new Polygon(); arrow.getPoints().addAll(ex, ey, ex - len * Math.cos(angle - Math.PI/6), ey - len * Math.sin(angle - Math.PI/6), ex - len * Math.cos(angle + Math.PI/6), ey - len * Math.sin(angle + Math.PI/6));
        arrow.setFill(color); canvas.getChildren().addAll(qc, arrow);
    }
    private void drawCrossOut(double x, double y) {
        Line l1 = new Line(x - 10, y - 10, x + 10, y + 10), l2 = new Line(x - 10, y + 10, x + 10, y - 10);
        l1.setStroke(C_DELETE); l1.setStrokeWidth(3.5); l2.setStroke(C_DELETE); l2.setStrokeWidth(3.5);
        canvas.getChildren().addAll(l1, l2);
    }
    private void addLabel(double x,double y,String txt,String hex,int size){ Text t=new Text(x,y,txt); t.setFont(Font.font("System",FontWeight.BOLD,size)); t.setFill(Color.web(hex)); canvas.getChildren().add(t); }

    private void insertLogic(int idx, int val){
        Node n=new Node(val);
        if(idx==0){n.next=head;if(head!=null)head.prev=n;head=n;return;}
        Node t=head; for(int i=0;i<idx-1&&t.next!=null;i++)t=t.next;
        n.next=t.next; n.prev=t;
        if(t.next!=null)t.next.prev=n; t.next=n;
    }
    private void deleteLogic(int idx){
        if(idx==0){head=head.next;if(head!=null)head.prev=null;return;}
        Node t=head; for(int i=0;i<idx-1&&t.next!=null;i++)t=t.next;
        Node del=t.next; if(del==null)return;
        t.next=del.next; if(del.next!=null)del.next.prev=t;
    }
    private Node getAt(int idx){ Node t=head; for(int i=0;i<idx;i++)t=t.next; return t; }
    private int size(){ int c=0; Node t=head; while(t!=null){c++;t=t.next;} return c; }
    private KeyFrame kfAt(double s,Runnable r){return new KeyFrame(Duration.seconds(s),e->r.run());}
    private void run(List<KeyFrame> kf){anim=new Timeline();anim.getKeyFrames().addAll(kf);anim.play();}
    private void stopAnim(){if(anim!=null)anim.stop();}
    private Integer readVal(){try{return Integer.parseInt(valueField.getText().trim());}catch(Exception e){err("Enter a valid integer in Value");return null;}}
    private Integer readIdx(){try{return Integer.parseInt(indexField.getText().trim());}catch(Exception e){err("Enter a valid integer in Index");return null;}}
    private void err(String m){setStatus("⚠ "+m);}
    private void setStatus(String m){statusLabel.setText(m);headerStatusLabel.setText(m);}

    // ══════════════════════════════════════════════════════════════════════════
    //  CAPTURE & RECORDING LOGIC (LOCKED RESOLUTION & WHITE BG FIX)
    // ══════════════════════════════════════════════════════════════════════════
    @FXML
    void takeScreenshot() {
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.WHITE); // Set to white to match your pane

        WritableImage snapshot = canvas.snapshot(params, null);
        BufferedImage buffered = SwingFXUtils.fromFXImage(snapshot, null);

        String downloadsDir = getDownloadsPath();
        String timestamp    = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File   outputFile   = new File(downloadsDir, "doubly_linked_list_" + timestamp + ".png");

        try {
            ImageIO.write(buffered, "png", outputFile);
            System.out.println("Screenshot saved: " + outputFile.getAbsolutePath());
            setStatus("Screenshot saved! ✓");
            buffered.flush();
        } catch (IOException ex) {
            System.err.println("Screenshot failed: " + ex.getMessage());
            err("Failed to save screenshot.");
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
        isCapturing = false;
        recordBtn.setText("⏹");
        recordBtn.setStyle(
                "-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-size: 14px;" +
                        "-fx-background-radius: 6; -fx-cursor: hand; -fx-border-color: #991b1b; -fx-border-radius: 6;"
        );

        // LOCK RESOLUTION based on the starting size of the canvas
        SnapshotParameters initParams = new SnapshotParameters();
        initParams.setFill(Color.WHITE); // White background
        WritableImage initSnap = canvas.snapshot(initParams, null);

        final int lockedW = ((int) initSnap.getWidth() % 2 == 0) ? (int) initSnap.getWidth() : (int) initSnap.getWidth() + 1;
        final int lockedH = ((int) initSnap.getHeight() % 2 == 0) ? (int) initSnap.getHeight() : (int) initSnap.getHeight() + 1;

        try {
            String downloadsDir = getDownloadsPath();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File outputFile = new File(downloadsDir, "doubly_linked_list_rec_" + timestamp + ".mp4");

            encoder = AWTSequenceEncoder.createSequenceEncoder(outputFile, RECORD_FPS);
            System.out.println("Recording started... Streaming to: " + outputFile.getAbsolutePath() + " at " + lockedW + "x" + lockedH);
            setStatus("Recording started...");
        } catch (IOException e) {
            System.err.println("Failed to start video encoder: " + e.getMessage());
            err("Failed to start recording.");
            isRecording = false;
            return;
        }

        frameQueue = new ArrayBlockingQueue<>(30);

        recordingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "screen-recorder");
            t.setDaemon(true);
            return t;
        });

        Thread encoderThread = new Thread(() -> {
            try {
                while (isRecording || !frameQueue.isEmpty()) {
                    BufferedImage frame = frameQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (frame != null) {

                        BufferedImage bgrFrame = new BufferedImage(lockedW, lockedH, BufferedImage.TYPE_3BYTE_BGR);
                        Graphics2D g = bgrFrame.createGraphics();

                        g.setColor(java.awt.Color.WHITE); // Fill extra space with white
                        g.fillRect(0, 0, lockedW, lockedH);

                        g.drawImage(frame, 0, 0, null); // Draw into locked boundary
                        g.dispose();

                        encoder.encodeImage(bgrFrame);

                        bgrFrame.flush();
                        frame.flush();
                    }
                }
                encoder.finish();
                System.out.println("Video saved successfully.");
                Platform.runLater(() -> setStatus("Recording saved! ✓"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        encoderThread.setDaemon(true);
        encoderThread.start();

        recordingExecutor.scheduleAtFixedRate(() -> {
            if (!isRecording || isCapturing) return;
            isCapturing = true;

            Platform.runLater(() -> {
                try {
                    SnapshotParameters params = new SnapshotParameters();
                    params.setFill(Color.WHITE); // Set padding to white

                    WritableImage fxFrame = canvas.snapshot(params, null);
                    BufferedImage buffered = SwingFXUtils.fromFXImage(fxFrame, null);

                    if (!frameQueue.offer(buffered)) {
                        buffered.flush();
                    }
                } catch (Exception e) {
                    System.err.println("Capture error: " + e.getMessage());
                } finally {
                    isCapturing = false;
                }
            });
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