package org.example.VisuAlgorithm;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.shape.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.util.*;

public class LinkedListController {

    // -------- UI --------
    @FXML private Pane canvas;

    @FXML private ToggleGroup modeGroup;
    @FXML private RadioButton rbSingly;
    @FXML private RadioButton rbDoubly;
    @FXML private RadioButton rbMulti;

    @FXML private Button backBtn;

    @FXML private TextField valueField;
    @FXML private TextField deleteIndexField;

    @FXML private TextField fromField;
    @FXML private TextField toField;
    @FXML private ChoiceBox<String> edgeOpChoice;

    @FXML private Label statusLabel;
    @FXML private Label headerStatusLabel;
    @FXML private Label modeHintLabel;

    // -------- Modes --------
    private enum Mode { SINGLY, DOUBLY, MULTI }
    private Mode mode = Mode.SINGLY;

    // -------- Model --------
    private static class Node {
        int value;
        Node(int v) { value = v; }
    }

    private final ArrayList<Node> nodes = new ArrayList<>();
    // directed adjacency: u -> set of v
    private final HashMap<Integer, HashSet<Integer>> out = new HashMap<>();

    // -------- Layout --------
    private final double startX = 80;
    private final double startY = 300;
    private final double gap = 140;
    private final double boxW = 80;
    private final double boxH = 50;

    @FXML
    public void initialize() {
        edgeOpChoice.getItems().addAll("+", "-", "clear");
        edgeOpChoice.setValue("+");

        setMode(Mode.SINGLY);
        redraw();
        setStatus("Ready.");
    }

    // ================= BACK =================
    @FXML
    private void onBack() {
        // 🔧 IMPORTANT:
        // Change "hello-view.fxml" to your real menu/home FXML (same as other modules).
        goTo("hello-view.fxml");
    }

    private void goTo(String fxmlName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlName));
            Parent root = loader.load();
            Stage stage = (Stage) canvas.getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (Exception e) {
            setStatus("Back/navigation failed: " + e.getMessage());
        }
    }

    // ================= MODE =================
    @FXML
    private void onModeChanged() {
        if (rbSingly.isSelected()) setMode(Mode.SINGLY);
        else if (rbDoubly.isSelected()) setMode(Mode.DOUBLY);
        else setMode(Mode.MULTI);
    }

    private void setMode(Mode m) {
        mode = m;

        if (mode == Mode.SINGLY) modeHintLabel.setText("Singly: each node max 1 outgoing next");
        else if (mode == Mode.DOUBLY) modeHintLabel.setText("Doubly: next + prev auto");
        else modeHintLabel.setText("Multi: unlimited outgoing links");

        normalizeEdgesForMode();
        redraw();
        setStatus("Mode set to " + mode);
    }

    private void normalizeEdgesForMode() {
        if (mode == Mode.MULTI) return;

        // enforce max 1 outgoing per node
        for (int u = 0; u < nodes.size(); u++) {
            HashSet<Integer> tos = out.get(u);
            if (tos == null || tos.isEmpty()) continue;

            int pick = Integer.MAX_VALUE;
            for (int v : tos) pick = Math.min(pick, v);

            HashSet<Integer> only = new HashSet<>();
            only.add(pick);
            out.put(u, only);
        }

        if (mode == Mode.SINGLY) {
            // remove obvious reverse edges (if both directions exist, remove v->u)
            for (int u = 0; u < nodes.size(); u++) {
                Integer v = getSingleNext(u);
                if (v != null) removeEdge(v, u);
            }
        } else if (mode == Mode.DOUBLY) {
            // ensure reverse edge exists for each next
            for (int u = 0; u < nodes.size(); u++) {
                Integer v = getSingleNext(u);
                if (v != null) addEdge(v, u);
            }
        }
    }

    private Integer getSingleNext(int u) {
        HashSet<Integer> tos = out.get(u);
        if (tos == null || tos.isEmpty()) return null;
        return tos.iterator().next();
    }

    // ================= NODE OPS =================
    @FXML
    private void onInsertHead() {
        Integer v = parseInt(valueField.getText());
        if (v == null) return;

        nodes.add(0, new Node(v));

        // shift edges: +1 all indices
        HashMap<Integer, HashSet<Integer>> newOut = new HashMap<>();
        for (int u : out.keySet()) {
            HashSet<Integer> shifted = new HashSet<>();
            for (int w : out.get(u)) shifted.add(w + 1);
            newOut.put(u + 1, shifted);
        }
        out.clear();
        out.putAll(newOut);
        out.put(0, new HashSet<>());

        // optional auto chain for singly/doubly
        if (nodes.size() >= 2 && mode != Mode.MULTI) {
            out.get(0).clear();
            out.get(0).add(1);
            if (mode == Mode.DOUBLY) addEdge(1, 0);
        }

        normalizeEdgesForMode();
        redraw();
        setStatus("Inserted head: " + v);
    }

    @FXML
    private void onInsertTail() {
        Integer v = parseInt(valueField.getText());
        if (v == null) return;

        int idx = nodes.size();
        nodes.add(new Node(v));
        out.put(idx, new HashSet<>());

        // optional auto chain for singly/doubly
        if (idx > 0 && mode != Mode.MULTI) {
            out.get(idx - 1).clear();
            out.get(idx - 1).add(idx);
            if (mode == Mode.DOUBLY) addEdge(idx, idx - 1);
        }

        normalizeEdgesForMode();
        redraw();
        setStatus("Inserted tail: " + v);
    }

    @FXML
    private void onDeleteIndex() {
        Integer idx = parseInt(deleteIndexField.getText());
        if (idx == null) return;

        if (idx < 0 || idx >= nodes.size()) {
            setStatus("Delete failed: index out of range");
            return;
        }

        nodes.remove((int) idx);

        // rebuild edges with index shift
        HashMap<Integer, HashSet<Integer>> newOut = new HashMap<>();
        for (int u : out.keySet()) {
            if (u == idx) continue;

            int newU = (u > idx) ? (u - 1) : u;

            HashSet<Integer> newTos = new HashSet<>();
            for (int v : out.get(u)) {
                if (v == idx) continue;
                int newV = (v > idx) ? (v - 1) : v;
                newTos.add(newV);
            }
            newOut.put(newU, newTos);
        }

        out.clear();
        out.putAll(newOut);

        for (int i = 0; i < nodes.size(); i++) out.putIfAbsent(i, new HashSet<>());

        normalizeEdgesForMode();
        redraw();
        setStatus("Deleted index: " + idx);
    }

    @FXML
    private void onClearAll() {
        nodes.clear();
        out.clear();
        redraw();
        setStatus("Cleared all nodes and edges.");
    }

    // ================= EDGE BUILDER =================
    @FXML
    private void onApplyEdge() {
        Integer u = parseInt(fromField.getText());
        if (u == null) return;

        if (u < 0 || u >= nodes.size()) {
            setStatus("Edge failed: from out of range");
            return;
        }

        String op = edgeOpChoice.getValue();
        if (op == null) op = "+";

        if (op.equals("clear")) {
            out.get(u).clear();
            if (mode == Mode.DOUBLY) {
                for (int i = 0; i < nodes.size(); i++) removeEdge(i, u);
            }
            normalizeEdgesForMode();
            redraw();
            setStatus("Cleared outgoing from " + u);
            return;
        }

        Integer v = parseInt(toField.getText());
        if (v == null) return;

        if (v < 0 || v >= nodes.size()) {
            setStatus("Edge failed: to out of range");
            return;
        }

        if (op.equals("+")) {
            applyAddEdge(u, v);
            setStatus("Added edge: " + u + " -> " + v);
        } else if (op.equals("-")) {
            applyRemoveEdge(u, v);
            setStatus("Removed edge: " + u + " -> " + v);
        } else {
            setStatus("Unknown operation");
        }

        redraw();
    }

    private void applyAddEdge(int u, int v) {
        out.putIfAbsent(u, new HashSet<>());

        if (mode == Mode.MULTI) {
            addEdge(u, v);
            return;
        }

        // singly/doubly: only one "next"
        if (mode == Mode.DOUBLY) {
            Integer old = getSingleNext(u);
            if (old != null) removeEdge(old, u); // remove old prev link
        }

        out.get(u).clear();
        out.get(u).add(v);

        if (mode == Mode.DOUBLY) addEdge(v, u);
    }

    private void applyRemoveEdge(int u, int v) {
        removeEdge(u, v);
        if (mode == Mode.DOUBLY) removeEdge(v, u);
        normalizeEdgesForMode();
    }

    private void addEdge(int u, int v) {
        out.putIfAbsent(u, new HashSet<>());
        out.get(u).add(v);
    }

    private void removeEdge(int u, int v) {
        if (!out.containsKey(u)) return;
        out.get(u).remove(v);
    }

    // ================= DRAWING =================
    private void redraw() {
        canvas.getChildren().clear();

        // Draw nodes
        for (int i = 0; i < nodes.size(); i++) {
            double x = startX + i * gap;
            double y = startY;

            Rectangle box = new Rectangle(x, y, boxW, boxH);
            box.setArcWidth(16);
            box.setArcHeight(16);

            Text val = new Text(x + 28, y + 32, String.valueOf(nodes.get(i).value));

            // small index label (remove if you want)
            Text idx = new Text(x + 4, y - 8, String.valueOf(i));
            idx.setOpacity(0.45);

            canvas.getChildren().addAll(box, val, idx);
        }

        // Draw edges
        for (int u = 0; u < nodes.size(); u++) {
            HashSet<Integer> tos = out.get(u);
            if (tos == null) continue;
            for (int v : tos) {
                if (v < 0 || v >= nodes.size()) continue;
                drawArrow(u, v);
            }
        }

        if (nodes.isEmpty()) setStatus("Empty. Insert nodes to start.");
    }

    private void drawArrow(int u, int v) {
        double fromX = startX + u * gap + boxW;
        double fromY = startY + boxH / 2.0;

        double toX = startX + v * gap;
        double toY = startY + boxH / 2.0;

        boolean adjacent = (Math.abs(v - u) == 1);

        if (adjacent) {
            Line line = new Line(fromX, fromY, toX, toY);
            Polygon head = arrowHead(fromX, fromY, toX, toY);
            canvas.getChildren().addAll(line, head);
        } else {
            QuadCurve curve = new QuadCurve();
            curve.setStartX(fromX);
            curve.setStartY(fromY);
            curve.setEndX(toX);
            curve.setEndY(toY);

            double midX = (fromX + toX) / 2.0;
            double lift = (v > u) ? -95 : 95; // forward up, backward down
            curve.setControlX(midX);
            curve.setControlY(fromY + lift);

            curve.setFill(null);

            Polygon head = arrowHead(curve.getControlX(), curve.getControlY(), toX, toY);
            canvas.getChildren().addAll(curve, head);
        }
    }

    private Polygon arrowHead(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len == 0) len = 1;

        dx /= len;
        dy /= len;

        double size = 10;
        double px = -dy;
        double py = dx;

        double ax = x2;
        double ay = y2;

        double bx = x2 - dx * size + px * (size / 2.0);
        double by = y2 - dy * size + py * (size / 2.0);

        double cx = x2 - dx * size - px * (size / 2.0);
        double cy = y2 - dy * size - py * (size / 2.0);

        Polygon p = new Polygon();
        p.getPoints().addAll(ax, ay, bx, by, cx, cy);
        return p;
    }

    // ================= HELPERS =================
    private Integer parseInt(String s) {
        try {
            if (s == null) return null;
            s = s.trim();
            if (s.isEmpty()) {
                setStatus("Input empty");
                return null;
            }
            return Integer.parseInt(s);
        } catch (Exception e) {
            setStatus("Invalid number: " + s);
            return null;
        }
    }

    private void setStatus(String msg) {
        if (statusLabel != null) statusLabel.setText(msg);
        if (headerStatusLabel != null) headerStatusLabel.setText(msg);
    }
}