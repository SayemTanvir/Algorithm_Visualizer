//package org.example.VisuAlgorithm;
//
//import javafx.animation.*;
//import javafx.fxml.FXML;
//import javafx.scene.control.*;
//import javafx.scene.effect.DropShadow;
//import javafx.scene.effect.Glow;
//import javafx.scene.layout.Pane;
//import javafx.scene.paint.Color;
//import javafx.scene.shape.*;
//import javafx.scene.text.*;
//import javafx.util.Duration;
//
//import java.util.*;
//
///**
// * Singly Linked List — VisualGo-style step animation.
// *
// * Visual language (mirrors VisualGo):
// *   • Orange  (#f97316) = node being operated on (insert/delete target)
// *   • Blue    (#3b82f6) = pointer / traversal cursor
// *   • Green   (#22c55e) = found / success
// *   • Red     (#ef4444) = being deleted
// *   • Default (#1e3a5f bg, #93c5fd border) = normal node
// *
// * Nodes slide in from above on insert; shrink + fade on delete.
// * Pointer label ("ptr") hops node-to-node during search / traversal.
// */
//public class SinglyLinkedListController {
//
//    // ── FXML bindings ─────────────────────────────────────────────────────────
//    @FXML private Pane      canvas;
//    @FXML private TextField valueField;
//    @FXML private TextField indexField;
//    @FXML private Label     statusLabel;
//    @FXML private Label     headerStatusLabel;
//
//    // ── Data model ────────────────────────────────────────────────────────────
//    private static class Node {
//        int  data;
//        Node next;
//        Node(int data) { this.data = data; }
//    }
//    private Node head;
//
//    // ── Layout constants ──────────────────────────────────────────────────────
//    private static final double START_X  = 70;
//    private static final double NODE_Y   = 220;   // vertical centre of nodes
//    private static final double BOX_W    = 72;    // data cell width
//    private static final double PTR_W    = 36;    // pointer cell width
//    private static final double NODE_H   = 48;
//    private static final double GAP      = 140;   // centre-to-centre spacing
//    private static final double ARC      = 12;    // corner radius
//
//    // ── Animation helpers ─────────────────────────────────────────────────────
//    private final Random     rng        = new Random();
//    private       Timeline   animation  = new Timeline();
//
//    // ── Colours ───────────────────────────────────────────────────────────────
//    private static final Color C_NODE_FILL   = Color.web("#0f172a");
//    private static final Color C_NODE_STROKE = Color.web("#3b82f6");
//    private static final Color C_PTR_FILL    = Color.web("#1e293b");
//    private static final Color C_TEXT        = Color.web("#e2e8f0");
//    private static final Color C_ARROW       = Color.web("#64748b");
//    private static final Color C_HIGHLIGHT   = Color.web("#f97316"); // orange = target
//    private static final Color C_TRAVERSE    = Color.web("#3b82f6"); // blue  = cursor
//    private static final Color C_FOUND       = Color.web("#22c55e"); // green = found
//    private static final Color C_DELETE      = Color.web("#ef4444"); // red   = delete
//    private static final Color C_NEW         = Color.web("#a855f7"); // purple= new node
//
//    // ── FXML init ─────────────────────────────────────────────────────────────
//    @FXML public void initialize() { redraw(-1, -1, -1); }
//
//    // ══════════════════════════════════════════════════════════════════════════
//    //  Navigation
//    // ══════════════════════════════════════════════════════════════════════════
//    @FXML private void onBack() { Launcher.switchScene("linked-list-view.fxml"); }
//
//    // ══════════════════════════════════════════════════════════════════════════
//    //  Public button handlers
//    // ══════════════════════════════════════════════════════════════════════════
//
//    @FXML private void onRandom() {
//        stopAnim();
//        head = null;
//        int count = rng.nextInt(4) + 3;           // 3–6 nodes
//        SequentialTransition seq = new SequentialTransition();
//        Timeline timeline = new Timeline();
//
//        for (int i = 0; i < count; i++) {
//            int val = rng.nextInt(90) + 10;
//            int step = i;
//
//            timeline.getKeyFrames().add(
//                    new KeyFrame(Duration.seconds(step * 0.6), e -> {
//                        Node node = new Node(val);
//
//                        if (head == null) {
//                            head = node;
//                        } else {
//                            Node temp = head;
//                            while (temp.next != null) temp = temp.next;
//                            temp.next = node;
//                        }
//
//                        redraw(-1, -1,-1);
//                        setStatus("Inserted random: " + val);
//                    })
//            );
//        }
//
//        timeline.play();
////        for (int i = 0; i < count; i++) {
////            int val = rng.nextInt(90) + 10;
//////            seq.getChildren().add(pauseThen(0.05, () -> {
//////                appendTail(val);
//////                redraw(-1, -1, -1);
//////            }));
////            playInsertAt(i,val);
////        }
//        seq.play();
//        setStatus("Generated " + count + " random nodes");
//    }
//
//    @FXML private void onInsertHead() {
//        Integer val = readVal(); if (val == null) return;
//        stopAnim();
//        playInsertAt(0, val);
//    }
//
//    @FXML private void onInsertTail() {
//        Integer val = readVal(); if (val == null) return;
//        stopAnim();
//        playInsertAt(size(), val);
//    }
//
//    @FXML private void onInsertAt() {
//        Integer val = readVal(); if (val == null) return;
//        Integer idx = readIdx(); if (idx == null) return;
//        if (idx < 0 || idx > size()) { err("Index out of range [0.." + size() + "]"); return; }
//        stopAnim();
//        playInsertAt(idx, val);
//    }
//
//    @FXML private void onDeleteHead() {
//        if (head == null) { err("List is empty"); return; }
//        stopAnim();
//        playDeleteAt(0);
//    }
//
//    @FXML private void onDeleteTail() {
//        if (head == null) { err("List is empty"); return; }
//        stopAnim();
//        playDeleteAt(size() - 1);
//    }
//
//    @FXML private void onDeleteAt() {
//        Integer idx = readIdx(); if (idx == null) return;
//        if (idx < 0 || idx >= size()) { err("Index out of range [0.." + (size()-1) + "]"); return; }
//        stopAnim();
//        playDeleteAt(idx);
//    }
//
//    @FXML private void onSearch() {
//        Integer val = readVal(); if (val == null) return;
//        if (head == null) { err("List is empty"); return; }
//        stopAnim();
//        playSearch(val);
//    }
//
//    @FXML private void onTraverse() {
//        if (head == null) { err("List is empty"); return; }
//        stopAnim();
//        playTraverse();
//    }
//
//    @FXML private void onSort() {
//        if (head == null || head.next == null) { err("Need ≥ 2 nodes"); return; }
//        stopAnim();
//        playBubbleSort();
//    }
//
//    @FXML private void onClear() {
//        stopAnim(); head = null;
//        redraw(-1, -1, -1);
//        setStatus("List cleared");
//    }
//
//    // ══════════════════════════════════════════════════════════════════════════
//    //  Animation — Insert
//    // ══════════════════════════════════════════════════════════════════════════
//    private void playInsertAt(int idx, int val) {
//        List<KeyFrame> kf = new ArrayList<>();
//        double t = 0;
//        if (idx > 0) {
//            for (int i = 0; i < idx; i++) {
//                int cur = i;
//                kf.add(kfAt(t, () -> {
//                    redraw(-1, cur, -1);
//                    setStatus("Traversing to index " + cur + "…");
//                }));
//                t += 0.45;
//            }
//        }
//        // Step 1: highlight predecessor (if any) in orange
//        if (idx > 0) {
//            int pred = idx - 1;
//            kf.add(kfAt(t, () -> {
//                redraw(pred, -1, -1);
//                setStatus("Step 1 — locate predecessor at index " + pred);
//            }));
//            t += 0.7;
//        }
//
//        // Step 2: show new node in purple above its final position
//        kf.add(kfAt(t, () -> {
//            redraw(idx > 0 ? idx - 1 : -1, -1, -1);
//            showFloatingNode(idx, val, C_NEW);
//            setStatus("Step 2 — new node [" + val + "] ready to insert at index " + idx);
//        }));
//        t += 0.7;
//
//        // Step 3: commit insert, highlight new node
//        kf.add(kfAt(t, () -> {
//            insertLogic(idx, val);
//            redraw(idx, -1, -1);
//            setStatus("Step 3 — inserted [" + val + "] at index " + idx + " ✓");
//        }));
//
//        runTimeline(kf);
//    }
//
//    /** Draws a temporary ghost node above its destination slot. */
//    private void showFloatingNode(int idx, int val, Color color) {
//        redraw(-1, -1, -1);   // clear highlights first
//        double x = START_X + idx * GAP;
//        double y = NODE_Y - 80;
//        canvas.getChildren().addAll(
//                makeNodeGroup(x, y, val, color, -1, false, -1)
//        );
//    }
//
//    // ══════════════════════════════════════════════════════════════════════════
//    //  Animation — Delete
//    // ══════════════════════════════════════════════════════════════════════════
//    private void playDeleteAt(int idx) {
//        List<KeyFrame> kf = new ArrayList<>();
//        double t = 0;
//
//        // Step 1: walk to predecessor
//        if (idx > 0) {
//            for (int i = 0; i < idx; i++) {
//                int cur = i;
//                kf.add(kfAt(t, () -> {
//                    redraw(-1, cur, -1);
//                    setStatus("Traversing to index " + cur + "…");
//                }));
//                t += 0.45;
//            }
//        }
//
//        // Step 2: highlight target in red
//        kf.add(kfAt(t, () -> {
//            redraw(idx, -1, -1);
//            setStatus("Step — deleting node at index " + idx);
//        }));
//        t += 0.7;
//
//        // Step 3: remove
//        kf.add(kfAt(t, () -> {
//            int removed = getAt(idx).data;
//            deleteLogic(idx);
//            redraw(-1, -1, -1);
//            setStatus("Deleted [" + removed + "] from index " + idx + " ✓");
//        }));
//
//        runTimeline(kf);
//    }
//
//    // ══════════════════════════════════════════════════════════════════════════
//    //  Animation — Search
//    // ══════════════════════════════════════════════════════════════════════════
//    private void playSearch(int target) {
//        List<KeyFrame> kf = new ArrayList<>();
//        double t = 0;
//        int found = -1;
//
//        Node cur = head; int i = 0;
//        while (cur != null) {
//            int fi = i;
//            kf.add(kfAt(t, () -> {
//                redraw(-1, fi, -1);          // blue cursor
//                setStatus("Checking index " + fi + " …");
//            }));
//            t += 0.55;
//            if (cur.data == target) { found = i; break; }
//            cur = cur.next; i++;
//        }
//
//        if (found >= 0) {
//            int ff = found;
//            kf.add(kfAt(t, () -> {
//                redraw(ff, -1, -1);          // orange = found
//                setStatus("✓ Found [" + target + "] at index " + ff);
//            }));
//        } else {
//            kf.add(kfAt(t, () -> {
//                redraw(-1, -1, -1);
//                setStatus("✗ [" + target + "] not found in list");
//            }));
//        }
//        runTimeline(kf);
//    }
//
//    // ══════════════════════════════════════════════════════════════════════════
//    //  Animation — Traverse
//    // ══════════════════════════════════════════════════════════════════════════
//    private void playTraverse() {
//        List<KeyFrame> kf = new ArrayList<>();
//        double t = 0;
//        Node cur = head; int i = 0;
//        while (cur != null) {
//            int fi = i; int fv = cur.data;
//            kf.add(kfAt(t, () -> {
//                redraw(-1, fi, -1);
//                setStatus("ptr → index " + fi + "  value = " + fv);
//            }));
//            t += 0.5;
//            cur = cur.next; i++;
//        }
//        int finalI = i;
//        kf.add(kfAt(t, () -> { redraw(-1, -1, -1); setStatus("Traversal complete — " + finalI + " nodes visited ✓"); }));
//        runTimeline(kf);
//    }
//
//    // ══════════════════════════════════════════════════════════════════════════
//    //  Animation — Bubble Sort
//    // ══════════════════════════════════════════════════════════════════════════
//    private void playBubbleSort() {
//        List<KeyFrame> kf = new ArrayList<>();
//        double t = 0;
//        int n = size();
//
//        for (int pass = 0; pass < n - 1; pass++) {
//            Node a = head; int ai = 0;
//            while (a != null && a.next != null) {
//                Node b = a.next;
//                int fa = ai, fb = ai + 1;
//                kf.add(kfAt(t, () -> {
//                    redraw(fa, fb, -1);
//                    setStatus("Compare [" + fa + "] vs [" + fb + "]");
//                }));
//                t += 0.4;
//                if (a.data > b.data) {
//                    int tmp = a.data; a.data = b.data; b.data = tmp;
//                    kf.add(kfAt(t, () -> {
//                        redraw(fa, fb, -1);
//                        setStatus("Swap [" + fa + "] ↔ [" + fb + "]");
//                    }));
//                    t += 0.4;
//                }
//                a = a.next; ai++;
//            }
//        }
//        kf.add(kfAt(t, () -> { redraw(-1, -1, -1); setStatus("Sort complete ✓"); }));
//        runTimeline(kf);
//    }
//
//    // ══════════════════════════════════════════════════════════════════════════
//    //  Redraw — full repaint
//    //    primaryIdx   = orange highlight (insert / delete / found)
//    //    secondaryIdx = blue  highlight (traversal cursor / compare B)
//    //    tertiaryIdx  = (unused; reserved for sort-pass mark)
//    // ══════════════════════════════════════════════════════════════════════════
//    private void redraw(int primaryIdx, int secondaryIdx, int tertiaryIdx) {
//        canvas.getChildren().clear();
//
//        int sz = size();
//        canvas.setPrefWidth(Math.max(900, START_X + sz * GAP + 200));
//        canvas.setPrefHeight(520);
//
//        if (head == null) {
//            Text t = new Text(350, 180, "Singly Linked List is empty");
//            t.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 20));
//            t.setFill(Color.web("#94a3b8"));
//            canvas.getChildren().add(t);
//            return;
//        }
//
//        Node cur = head; int idx = 0;
//        while (cur != null) {
//            double x = START_X + idx * GAP;
//
//            Color nodeColor = C_NODE_FILL;
//            if (idx == primaryIdx)   nodeColor = C_HIGHLIGHT;
//            if (idx == secondaryIdx) nodeColor = C_TRAVERSE;
//
//            canvas.getChildren().addAll(makeNodeGroup(x, NODE_Y, cur.data, nodeColor, idx, true, primaryIdx == idx ? 1 : secondaryIdx == idx ? 2 : 0));
//
//            // Arrow to next node
//            if (cur.next != null) {
//                double ax = x + BOX_W + PTR_W;
//                double ay = NODE_Y + NODE_H / 2.0;
//                double bx = x + GAP;
//                Line arrow = new Line(ax, ay, bx, ay);
//                arrow.setStroke(C_ARROW); arrow.setStrokeWidth(2);
//                canvas.getChildren().add(arrow);
//                canvas.getChildren().add(arrowHead(bx, ay, true));
//            } else {
//                // NULL label
//                Text nullT = new Text(x + BOX_W + PTR_W + 6, NODE_Y + NODE_H / 2.0 + 6, "null");
//                nullT.setFont(Font.font("Monospace", 13));
//                nullT.setFill(Color.web("#ef4444"));
//                canvas.getChildren().add(nullT);
//                // TAIL label
//                addLabel(x + BOX_W / 2.0 - 12, NODE_Y + NODE_H + 28, "TAIL", "#ef4444", 12);
//            }
//
//            // HEAD label
//            if (idx == 0) addLabel(x + BOX_W / 2.0 - 14, NODE_Y - 30, "HEAD", "#22c55e", 12);
//
//            cur = cur.next; idx++;
//        }
//    }
//
//    // ══════════════════════════════════════════════════════════════════════════
//    //  Node drawing helpers
//    // ══════════════════════════════════════════════════════════════════════════
//
//    /**
//     * Returns a list of JavaFX nodes for one linked-list node cell.
//     * colorOverride == C_NODE_FILL → default dark look.
//     */
//    private List<javafx.scene.Node> makeNodeGroup(
//            double x, double y, int val,
//            Color color, int idx, boolean showPtrBox, int role) {
//
//        List<javafx.scene.Node> group = new ArrayList<>();
//
//        boolean isDefault = color.equals(C_NODE_FILL);
//
//        // Data cell
//        Rectangle dataCell = new Rectangle(x, y, BOX_W, NODE_H);
//        dataCell.setArcWidth(ARC); dataCell.setArcHeight(ARC);
//        dataCell.setFill(isDefault ? C_NODE_FILL : color.deriveColor(0,1,1,0.25));
//        dataCell.setStroke(isDefault ? C_NODE_STROKE : color);
//        dataCell.setStrokeWidth(2.5);
//        if (!isDefault) dataCell.setEffect(new DropShadow(14, color));
//        group.add(dataCell);
//
//        // Data value text
//        Text valText = new Text(x + BOX_W / 2.0 - 10, y + NODE_H / 2.0 + 7, String.valueOf(val));
//        valText.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
//        valText.setFill(isDefault ? C_TEXT : color);
//        group.add(valText);
//
//        // Pointer cell
//        if (showPtrBox) {
//            Rectangle ptrCell = new Rectangle(x + BOX_W, y, PTR_W, NODE_H);
//            ptrCell.setArcWidth(ARC); ptrCell.setArcHeight(ARC);
//            ptrCell.setFill(C_PTR_FILL);
//            ptrCell.setStroke(isDefault ? C_NODE_STROKE : color);
//            ptrCell.setStrokeWidth(2);
//            group.add(ptrCell);
//
//            // Dot inside pointer cell
//            Circle dot = new Circle(x + BOX_W + PTR_W / 2.0, y + NODE_H / 2.0, 4,
//                    isDefault ? C_NODE_STROKE : color);
//            group.add(dot);
//        }
//
//        // Index label above node
//        if (idx >= 0) {
//            Text idxText = new Text(x + BOX_W / 2.0 - 8, y - 8, "[" + idx + "]");
//            idxText.setFont(Font.font("Segoe UI", 11));
//            idxText.setFill(Color.web("#94a3b8"));
//            group.add(idxText);
//        }
//
//        // Role label below node (ptr arrow label like VisualGo)
//        if (role == 2) {
//            addLabelToGroup(group, x + 4, y + NODE_H + 20, "", "#3b82f6", 11);
//        }
//
//        return group;
//    }
//
//    private Polygon arrowHead(double tipX, double tipY, boolean pointRight) {
//        Polygon p = new Polygon();
//        if (pointRight) p.getPoints().addAll(tipX, tipY, tipX-10, tipY-5, tipX-10, tipY+5);
//        else            p.getPoints().addAll(tipX, tipY, tipX+10, tipY-5, tipX+10, tipY+5);
//        p.setFill(C_ARROW);
//        return p;
//    }
//
//    private void addLabel(double x, double y, String txt, String hex, int size) {
//        Text t = new Text(x, y, txt);
//        t.setFont(Font.font("Segoe UI", FontWeight.BOLD, size));
//        t.setFill(Color.web(hex));
//        canvas.getChildren().add(t);
//    }
//
//    private void addLabelToGroup(List<javafx.scene.Node> g, double x, double y, String txt, String hex, int size) {
//        Text t = new Text(x, y, txt);
//        t.setFont(Font.font("Segoe UI", 11));
//        t.setFill(Color.web(hex));
//        g.add(t);
//    }
//
//    // ══════════════════════════════════════════════════════════════════════════
//    //  Data-model helpers
//    // ══════════════════════════════════════════════════════════════════════════
//    private void insertLogic(int idx, int val) {
//        Node node = new Node(val);
//        if (idx == 0) { node.next = head; head = node; return; }
//        Node t = head; for (int i = 0; i < idx - 1 && t.next != null; i++) t = t.next;
//        node.next = t.next; t.next = node;
//    }
//
//    private void deleteLogic(int idx) {
//        if (idx == 0) { head = head.next; return; }
//        Node t = head; for (int i = 0; i < idx - 1 && t.next != null; i++) t = t.next;
//        if (t.next != null) t.next = t.next.next;
//    }
//
//    private void appendTail(int val) {
//        Node n = new Node(val);
//        if (head == null) { head = n; return; }
//        Node t = head; while (t.next != null) t = t.next; t.next = n;
//    }
//
//    private Node getAt(int idx) {
//        Node t = head; for (int i = 0; i < idx; i++) t = t.next; return t;
//    }
//
//    private int size() { int c=0; Node t=head; while(t!=null){c++;t=t.next;} return c; }
//
//    // ══════════════════════════════════════════════════════════════════════════
//    //  Animation utilities
//    // ══════════════════════════════════════════════════════════════════════════
//    private KeyFrame kfAt(double secs, Runnable r) {
//        return new KeyFrame(Duration.seconds(secs), e -> r.run());
//    }
//
//    private PauseTransition pauseThen(double secs, Runnable r) {
//        PauseTransition p = new PauseTransition(Duration.seconds(secs));
//        p.setOnFinished(e -> r.run()); return p;
//    }
//
//    private void runTimeline(List<KeyFrame> kf) {
//        animation = new Timeline(); animation.getKeyFrames().addAll(kf); animation.play();
//    }
//
//    private void stopAnim() { if (animation != null) animation.stop(); }
//
//    // ══════════════════════════════════════════════════════════════════════════
//    //  Input helpers
//    // ══════════════════════════════════════════════════════════════════════════
//    private Integer readVal() {
//        try { return Integer.parseInt(valueField.getText().trim()); }
//        catch (Exception e) { err("Enter a valid integer in the Value field"); return null; }
//    }
//
//    private Integer readIdx() {
//        try { return Integer.parseInt(indexField.getText().trim()); }
//        catch (Exception e) { err("Enter a valid integer in the Index field"); return null; }
//    }
//
//    private void err(String msg) { setStatus("⚠ " + msg); }
//
//    private void setStatus(String msg) {
//        statusLabel.setText(msg);
//        headerStatusLabel.setText(msg);
//    }
//}
package org.example.VisuAlgorithm;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.util.Duration;

import java.util.*;

/**
 * Singly Linked List — VisualGo-style step animation.
 *
 * Visual language:
 * • Orange  (#f97316) = node being operated on (insert/delete target)
 * • Blue    (#3b82f6) = pointer / traversal cursor
 * • Green   (#22c55e) = found / success
 * • Red     (#ef4444) = being deleted / crossed out
 * • Purple  (#a855f7) = new node entering memory
 */
public class SinglyLinkedListController {

    // ── FXML bindings ─────────────────────────────────────────────────────────
    @FXML private Pane      canvas;
    @FXML private TextField valueField;
    @FXML private TextField indexField;
    @FXML private Label     statusLabel;
    @FXML private Label     headerStatusLabel;

    // ── Data model ────────────────────────────────────────────────────────────
    private static class Node {
        int  data;
        Node next;
        Node(int data) { this.data = data; }
    }
    private Node head;

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final double START_X  = 70;
    private static final double NODE_Y   = 220;
    private static final double BOX_W    = 72;
    private static final double PTR_W    = 36;
    private static final double NODE_H   = 48;
    private static final double GAP      = 140;
    private static final double ARC      = 12;

    // ── Animation helpers ─────────────────────────────────────────────────────
    private final Random     rng        = new Random();
    private       Timeline   animation  = new Timeline();

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color C_NODE_FILL   = Color.web("#0f172a");
    private static final Color C_NODE_STROKE = Color.web("#3b82f6");
    private static final Color C_PTR_FILL    = Color.web("#1e293b");
    private static final Color C_TEXT        = Color.web("#e2e8f0");
    private static final Color C_ARROW       = Color.web("#64748b");
    private static final Color C_HIGHLIGHT   = Color.web("#f97316"); // orange
    private static final Color C_TRAVERSE    = Color.web("#3b82f6"); // blue
    private static final Color C_FOUND       = Color.web("#22c55e"); // green
    private static final Color C_DELETE      = Color.web("#ef4444"); // red
    private static final Color C_NEW         = Color.web("#a855f7"); // purple

    @FXML public void initialize() { redraw(-1, -1, -1); }

    @FXML private void onBack() { stopAnim(); Launcher.switchScene("linked-list-view.fxml"); }

    // ══════════════════════════════════════════════════════════════════════════
    //  Button handlers
    // ══════════════════════════════════════════════════════════════════════════

    @FXML private void onRandom() {
        stopAnim();
        head = null;
        int count = rng.nextInt(4) + 3;
        SequentialTransition seq = new SequentialTransition();
        Timeline timeline = new Timeline();

        for (int i = 0; i < count; i++) {
            int val = rng.nextInt(90) + 10;
            int step = i;
            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(step * 0.4), e -> {
                        Node node = new Node(val);
                        if (head == null) head = node;
                        else {
                            Node temp = head;
                            while (temp.next != null) temp = temp.next;
                            temp.next = node;
                        }
                        redraw(-1, -1, -1);
                        setStatus("Inserted random: " + val);
                    })
            );
        }
        timeline.play();
        setStatus("Generated " + count + " random nodes");
    }

    @FXML private void onInsertHead() {
        Integer val = readVal(); if (val == null) return;
        stopAnim(); playInsertAt(0, val);
    }

    @FXML private void onInsertTail() {
        Integer val = readVal(); if (val == null) return;
        stopAnim(); playInsertAt(size(), val);
    }

    @FXML private void onInsertAt() {
        Integer val = readVal(); if (val == null) return;
        Integer idx = readIdx(); if (idx == null) return;
        if (idx < 0 || idx > size()) { err("Index out of range [0.." + size() + "]"); return; }
        stopAnim(); playInsertAt(idx, val);
    }

    @FXML private void onDeleteHead() {
        if (head == null) { err("List is empty"); return; }
        stopAnim(); playDeleteAt(0);
    }

    @FXML private void onDeleteTail() {
        if (head == null) { err("List is empty"); return; }
        stopAnim(); playDeleteAt(size() - 1);
    }

    @FXML private void onDeleteAt() {
        Integer idx = readIdx(); if (idx == null) return;
        if (idx < 0 || idx >= size()) { err("Index out of range [0.." + (size()-1) + "]"); return; }
        stopAnim(); playDeleteAt(idx);
    }

    @FXML private void onSearch() {
        Integer val = readVal(); if (val == null) return;
        if (head == null) { err("List is empty"); return; }
        stopAnim(); playSearch(val);
    }

    @FXML private void onTraverse() {
        if (head == null) { err("List is empty"); return; }
        stopAnim(); playTraverse();
    }

    @FXML private void onSort() {
        if (head == null || head.next == null) { err("Need ≥ 2 nodes"); return; }
        stopAnim(); playBubbleSort();
    }

    @FXML private void onClear() {
        stopAnim(); head = null;
        redraw(-1, -1, -1);
        setStatus("List cleared");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Animation — Insert Step-by-Step
    // ══════════════════════════════════════════════════════════════════════════
    private void playInsertAt(int idx, int val) {
        List<KeyFrame> kf = new ArrayList<>();
        double t = 0;

        // Traverse to Predecessor
        if (idx > 0) {
            for (int i = 0; i < idx; i++) {
                int cur = i;
                kf.add(kfAt(t, () -> {
                    redraw(-1, cur, -1);
                    setStatus("Traversing to find predecessor...");
                }));
                t += 0.45;
            }
        }

        if (idx == 0) {
            // STEP: Create Node
            kf.add(kfAt(t, () -> {
                redraw(-1,-1,-1); showFloatingNode(idx, val, C_NEW);
                setStatus("Step 1: Instantiate new node");
            })); t += 0.9;

            // STEP: Link new node to current head
            if(head != null) {
                kf.add(kfAt(t, () -> {
                    redraw(-1,-1,-1); showFloatingNode(idx, val, C_NEW);
                    drawTempArrow(START_X + BOX_W + PTR_W/2, NODE_Y - 80 + NODE_H, START_X + BOX_W/2, NODE_Y, C_HIGHLIGHT);
                    setStatus("Step 2: Link new node to current HEAD");
                })); t += 0.9;
            }
        } else {
            int p = idx - 1;

            // STEP 1: Create Node
            kf.add(kfAt(t, () -> {
                redraw(p,-1,-1); showFloatingNode(idx, val, C_NEW);
                setStatus("Step 1: Instantiate new node");
            })); t += 0.9;

            // STEP 2: Link new node to destination
            kf.add(kfAt(t, () -> {
                redraw(p,-1,-1); showFloatingNode(idx, val, C_NEW);
                if (idx < size()) { // link to current node
                    drawTempArrow(START_X + idx*GAP + BOX_W + PTR_W/2, NODE_Y - 80 + NODE_H, START_X + idx*GAP + BOX_W/2, NODE_Y, C_HIGHLIGHT);
                } else { // link to null
                    drawTempArrow(START_X + idx*GAP + BOX_W + PTR_W, NODE_Y - 80 + NODE_H/2, START_X + idx*GAP + BOX_W + PTR_W + 30, NODE_Y - 80 + NODE_H/2, C_HIGHLIGHT);
                }
                setStatus("Step 2: Link new node to its next node");
            })); t += 0.9;

            // STEP 3: Link predecessor to new node
            kf.add(kfAt(t, () -> {
                redraw(p,-1,-1); showFloatingNode(idx, val, C_NEW);
                if (idx < size()) drawTempArrow(START_X + idx*GAP + BOX_W + PTR_W/2, NODE_Y - 80 + NODE_H, START_X + idx*GAP + BOX_W/2, NODE_Y, C_HIGHLIGHT);
                else drawTempArrow(START_X + idx*GAP + BOX_W + PTR_W, NODE_Y - 80 + NODE_H/2, START_X + idx*GAP + BOX_W + PTR_W + 30, NODE_Y - 80 + NODE_H/2, C_HIGHLIGHT);

                drawTempArrow(START_X + p*GAP + BOX_W + PTR_W, NODE_Y + NODE_H/2, START_X + idx*GAP, NODE_Y - 80 + NODE_H/2, C_HIGHLIGHT);
                setStatus("Step 3: Link predecessor to new node");
            })); t += 0.9;

            // STEP 4: Disconnect old link
            kf.add(kfAt(t, () -> {
                redraw(p,-1,-1); showFloatingNode(idx, val, C_NEW);
                if (idx < size()) drawTempArrow(START_X + idx*GAP + BOX_W + PTR_W/2, NODE_Y - 80 + NODE_H, START_X + idx*GAP + BOX_W/2, NODE_Y, C_HIGHLIGHT);
                else drawTempArrow(START_X + idx*GAP + BOX_W + PTR_W, NODE_Y - 80 + NODE_H/2, START_X + idx*GAP + BOX_W + PTR_W + 30, NODE_Y - 80 + NODE_H/2, C_HIGHLIGHT);
                drawTempArrow(START_X + p*GAP + BOX_W + PTR_W, NODE_Y + NODE_H/2, START_X + idx*GAP, NODE_Y - 80 + NODE_H/2, C_HIGHLIGHT);

                // Cross out the old arrow
                drawCrossOut(START_X + p*GAP + BOX_W + PTR_W + 16, NODE_Y + NODE_H/2);
                setStatus("Step 4: Disconnect old link");
            })); t += 0.9;
        }

        // STEP: Final Commit
        kf.add(kfAt(t, () -> {
            insertLogic(idx, val);
            redraw(idx, -1, -1);
            setStatus("Insertion complete ✓");
        }));

        runTimeline(kf);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Animation — Delete Step-by-Step
    // ══════════════════════════════════════════════════════════════════════════
    private void playDeleteAt(int idx) {
        List<KeyFrame> kf = new ArrayList<>();
        double t = 0;

        // Traverse to Predecessor
        if (idx > 0) {
            for (int i = 0; i < idx; i++) {
                int cur = i;
                kf.add(kfAt(t, () -> {
                    redraw(-1, cur, -1);
                    setStatus("Traversing to target...");
                }));
                t += 0.45;
            }
        }

        if (idx == 0) {
            // STEP 1: Highlight
            kf.add(kfAt(t, () -> { redraw(0,-1,-1); setStatus("Step 1: Identify HEAD to delete"); })); t += 0.9;

            // STEP 2: Disconnect
            if (size() > 1) {
                kf.add(kfAt(t, () -> {
                    redraw(0,-1,-1);
                    drawCrossOut(START_X + BOX_W + PTR_W + 16, NODE_Y + NODE_H/2);
                    setStatus("Step 2: Disconnect HEAD");
                })); t += 0.9;
            }
        } else {
            int p = idx - 1;

            // STEP 1: Highlight target
            kf.add(kfAt(t, () -> {
                redraw(idx,-1,-1);
                setStatus("Step 1: Identify node to delete");
            })); t += 0.9;

            // STEP 2: Bypass Arrow
            kf.add(kfAt(t, () -> {
                redraw(idx,-1,-1);
                if (idx < size() - 1) {
                    drawTempCurve(START_X + p*GAP + BOX_W + PTR_W - 10, NODE_Y + NODE_H,
                            START_X + (idx+1)*GAP + 10, NODE_Y + NODE_H,
                            NODE_Y + NODE_H + 60, C_HIGHLIGHT);
                } else {
                    // Bypass to null
                    drawTempArrow(START_X + p*GAP + BOX_W + PTR_W, NODE_Y + NODE_H,
                            START_X + p*GAP + BOX_W + PTR_W + 40, NODE_Y + NODE_H + 30, C_HIGHLIGHT);
                    addLabel(START_X + p*GAP + BOX_W + PTR_W + 45, NODE_Y + NODE_H + 35, "null", "#ef4444", 13);
                }
                setStatus("Step 2: Link predecessor directly to next node");
            })); t += 1.0;

            // STEP 3: Disconnect Old Arrows
            kf.add(kfAt(t, () -> {
                redraw(idx,-1,-1);
                if (idx < size() - 1) drawTempCurve(START_X + p*GAP + BOX_W + PTR_W - 10, NODE_Y + NODE_H, START_X + (idx+1)*GAP + 10, NODE_Y + NODE_H, NODE_Y + NODE_H + 60, C_HIGHLIGHT);
                else {
                    drawTempArrow(START_X + p*GAP + BOX_W + PTR_W, NODE_Y + NODE_H, START_X + p*GAP + BOX_W + PTR_W + 40, NODE_Y + NODE_H + 30, C_HIGHLIGHT);
                    addLabel(START_X + p*GAP + BOX_W + PTR_W + 45, NODE_Y + NODE_H + 35, "null", "#ef4444", 13);
                }

                drawCrossOut(START_X + p*GAP + BOX_W + PTR_W + 16, NODE_Y + NODE_H/2); // Incoming cross
                if(idx < size() - 1) drawCrossOut(START_X + idx*GAP + BOX_W + PTR_W + 16, NODE_Y + NODE_H/2); // Outgoing cross
                setStatus("Step 3: Disconnect node from list");
            })); t += 1.0;
        }

        // STEP: Final Commit
        kf.add(kfAt(t, () -> {
            int removed = getAt(idx).data;
            deleteLogic(idx);
            redraw(-1, -1, -1);
            setStatus("Node [" + removed + "] successfully deleted ✓");
        }));

        runTimeline(kf);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Animation — Search & Traverse
    // ══════════════════════════════════════════════════════════════════════════
    private void playSearch(int target) {
        List<KeyFrame> kf = new ArrayList<>();
        double t = 0; int found = -1;
        Node cur = head; int i = 0;
        while (cur != null) {
            int fi = i;
            kf.add(kfAt(t, () -> { redraw(-1, fi, -1); setStatus("Checking index " + fi + " …"); }));
            t += 0.55;
            if (cur.data == target) { found = i; break; }
            cur = cur.next; i++;
        }
        if (found >= 0) {
            int ff = found;
            kf.add(kfAt(t, () -> { redraw(ff, -1, -1); setStatus("✓ Found [" + target + "] at index " + ff); }));
        } else {
            kf.add(kfAt(t, () -> { redraw(-1, -1, -1); setStatus("✗ [" + target + "] not found in list"); }));
        }
        runTimeline(kf);
    }

    private void playTraverse() {
        List<KeyFrame> kf = new ArrayList<>();
        double t = 0;
        Node cur = head; int i = 0;
        while (cur != null) {
            int fi = i; int fv = cur.data;
            kf.add(kfAt(t, () -> { redraw(-1, fi, -1); setStatus("ptr → index " + fi + "  value = " + fv); }));
            t += 0.5;
            cur = cur.next; i++;
        }
        int finalI = i;
        kf.add(kfAt(t, () -> { redraw(-1, -1, -1); setStatus("Traversal complete — " + finalI + " nodes visited ✓"); }));
        runTimeline(kf);
    }

    private void playBubbleSort() {
        List<KeyFrame> kf = new ArrayList<>();
        double t = 0; int n = size();
        for (int pass = 0; pass < n - 1; pass++) {
            Node a = head; int ai = 0;
            while (a != null && a.next != null) {
                Node b = a.next;
                int fa = ai, fb = ai + 1;
                kf.add(kfAt(t, () -> { redraw(fa, fb, -1); setStatus("Compare [" + fa + "] vs [" + fb + "]"); })); t += 0.4;
                if (a.data > b.data) {
                    int tmp = a.data; a.data = b.data; b.data = tmp;
                    kf.add(kfAt(t, () -> { redraw(fa, fb, -1); setStatus("Swap [" + fa + "] ↔ [" + fb + "]"); })); t += 0.4;
                }
                a = a.next; ai++;
            }
        }
        kf.add(kfAt(t, () -> { redraw(-1, -1, -1); setStatus("Sort complete ✓"); }));
        runTimeline(kf);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Drawing Methods
    // ══════════════════════════════════════════════════════════════════════════
    private void redraw(int primaryIdx, int secondaryIdx, int tertiaryIdx) {
        canvas.getChildren().clear();

        int sz = size();
        canvas.setPrefWidth(Math.max(900, START_X + sz * GAP + 200));
        canvas.setPrefHeight(520);

        if (head == null) {
            Text t = new Text(350, 180, "Singly Linked List is empty");
            t.setFont(Font.font("System", FontWeight.NORMAL, 20));
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

            if (cur.next != null) {
                double ax = x + BOX_W + PTR_W, ay = NODE_Y + NODE_H / 2.0, bx = x + GAP;
                Line arrow = new Line(ax, ay, bx, ay);
                arrow.setStroke(C_ARROW); arrow.setStrokeWidth(2.5);
                canvas.getChildren().addAll(arrow, arrowHead(bx, ay, true, C_ARROW));
            } else {
                Text nullT = new Text(x + BOX_W + PTR_W + 6, NODE_Y + NODE_H / 2.0 + 6, "null");
                nullT.setFont(Font.font("Monospace", 14)); nullT.setFill(C_DELETE);
                canvas.getChildren().add(nullT);
                addLabel(x + BOX_W / 2.0 - 12, NODE_Y + NODE_H + 28, "TAIL", "#ef4444", 12);
            }

            if (idx == 0) addLabel(x + BOX_W / 2.0 - 14, NODE_Y - 30, "HEAD", "#22c55e", 12);

            cur = cur.next; idx++;
        }
    }

    private void showFloatingNode(int idx, int val, Color color) {
        double x = START_X + idx * GAP;
        double y = NODE_Y - 80;
        canvas.getChildren().addAll(makeNodeGroup(x, y, val, color, -1, true, -1));
        addLabel(x - 20, y - 10, "NEW NODE", "#a855f7", 12);
    }

    private List<javafx.scene.Node> makeNodeGroup(double x, double y, int val, Color color, int idx, boolean showPtrBox, int role) {
        List<javafx.scene.Node> group = new ArrayList<>();
        boolean isDefault = color.equals(C_NODE_FILL);

        Rectangle dataCell = new Rectangle(x, y, BOX_W, NODE_H);
        dataCell.setArcWidth(ARC); dataCell.setArcHeight(ARC);
        dataCell.setFill(isDefault ? C_NODE_FILL : color.deriveColor(0,1,1,0.25));
        dataCell.setStroke(isDefault ? C_NODE_STROKE : color);
        dataCell.setStrokeWidth(2.5);
        if (!isDefault) dataCell.setEffect(new DropShadow(14, color));
        group.add(dataCell);

        Text valText = new Text(x + BOX_W / 2.0 - 10, y + NODE_H / 2.0 + 7, String.valueOf(val));
        valText.setFont(Font.font("System", FontWeight.BOLD, 18));
        valText.setFill(isDefault ? C_TEXT : color);
        group.add(valText);

        if (showPtrBox) {
            Rectangle ptrCell = new Rectangle(x + BOX_W, y, PTR_W, NODE_H);
            ptrCell.setArcWidth(ARC); ptrCell.setArcHeight(ARC);
            ptrCell.setFill(C_PTR_FILL);
            ptrCell.setStroke(isDefault ? C_NODE_STROKE : color);
            ptrCell.setStrokeWidth(2.5);
            group.add(ptrCell);

            Circle dot = new Circle(x + BOX_W + PTR_W / 2.0, y + NODE_H / 2.0, 4, isDefault ? C_NODE_STROKE : color);
            group.add(dot);
        }

        if (idx >= 0) {
            Text idxText = new Text(x + BOX_W / 2.0 - 8, y - 10, "[" + idx + "]");
            idxText.setFont(Font.font("System", 13)); idxText.setFill(Color.web("#93c5fd"));
            group.add(idxText);
        }
        return group;
    }

    // ── Dynamic Custom Graphics Helpers ──

    private void drawTempArrow(double sx, double sy, double ex, double ey, Color color) {
        Line line = new Line(sx, sy, ex, ey);
        line.setStroke(color); line.setStrokeWidth(2.5);

        double dx = ex - sx, dy = ey - sy;
        double angle = Math.atan2(dy, dx);
        double len = 11;
        Polygon arrow = new Polygon();
        arrow.getPoints().addAll(
                ex, ey,
                ex - len * Math.cos(angle - Math.PI/6), ey - len * Math.sin(angle - Math.PI/6),
                ex - len * Math.cos(angle + Math.PI/6), ey - len * Math.sin(angle + Math.PI/6)
        );
        arrow.setFill(color);
        canvas.getChildren().addAll(line, arrow);
    }

    private void drawTempCurve(double sx, double sy, double ex, double ey, double cy, Color color) {
        QuadCurve qc = new QuadCurve(sx, sy, (sx+ex)/2, cy, ex, ey);
        qc.setFill(null); qc.setStroke(color); qc.setStrokeWidth(2.5);
        qc.getStrokeDashArray().addAll(6d, 6d);

        double dx = ex - (sx+ex)/2, dy = ey - cy;
        double angle = Math.atan2(dy, dx);
        double len = 11;
        Polygon arrow = new Polygon();
        arrow.getPoints().addAll(
                ex, ey,
                ex - len * Math.cos(angle - Math.PI/6), ey - len * Math.sin(angle - Math.PI/6),
                ex - len * Math.cos(angle + Math.PI/6), ey - len * Math.sin(angle + Math.PI/6)
        );
        arrow.setFill(color);
        canvas.getChildren().addAll(qc, arrow);
    }

    private void drawCrossOut(double x, double y) {
        Line l1 = new Line(x - 10, y - 10, x + 10, y + 10);
        Line l2 = new Line(x - 10, y + 10, x + 10, y - 10);
        l1.setStroke(C_DELETE); l1.setStrokeWidth(3.5);
        l2.setStroke(C_DELETE); l2.setStrokeWidth(3.5);
        canvas.getChildren().addAll(l1, l2);
    }

    private Polygon arrowHead(double tipX, double tipY, boolean pointRight, Color c) {
        Polygon p = new Polygon();
        if (pointRight) p.getPoints().addAll(tipX, tipY, tipX-10, tipY-5, tipX-10, tipY+5);
        else            p.getPoints().addAll(tipX, tipY, tipX+10, tipY-5, tipX+10, tipY+5);
        p.setFill(c);
        return p;
    }

    private void addLabel(double x, double y, String txt, String hex, int size) {
        Text t = new Text(x, y, txt);
        t.setFont(Font.font("System", FontWeight.BOLD, size));
        t.setFill(Color.web(hex));
        canvas.getChildren().add(t);
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

    private Node getAt(int idx) {
        Node t = head; for (int i = 0; i < idx; i++) t = t.next; return t;
    }

    private int size() { int c=0; Node t=head; while(t!=null){c++;t=t.next;} return c; }

    // ══════════════════════════════════════════════════════════════════════════
    //  Animation utilities
    // ══════════════════════════════════════════════════════════════════════════
    private KeyFrame kfAt(double secs, Runnable r) { return new KeyFrame(Duration.seconds(secs), e -> r.run()); }
    private void runTimeline(List<KeyFrame> kf) { animation = new Timeline(); animation.getKeyFrames().addAll(kf); animation.play(); }
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
    private void setStatus(String msg) { statusLabel.setText(msg); headerStatusLabel.setText(msg); }
}