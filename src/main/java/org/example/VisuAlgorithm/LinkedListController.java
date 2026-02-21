package org.example.VisuAlgorithm;

import javafx.scene.shape.Line;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.*;

/**
 * Visual Linked List:
 * - Nodes show only value (double supports int/float/double)
 * - Edges drawn as arrows on edgeLayer (curved)
 * - Singly: →, Doubly: → and ←
 * - Far edges/cycles: higher curve
 */
public class LinkedListController {

    // ========= UI =========
    @FXML private HBox listBox;
    @FXML private Pane edgeLayer;
    @FXML private Label statusLabel;

    @FXML private TextField valueField;
    @FXML private TextField indexField;
    @FXML private TextField searchField;

    @FXML private TextField fromField;
    @FXML private TextField toField;
    @FXML private ChoiceBox<String> edgeTypeChoice;

    @FXML private Slider speedSlider;
    @FXML private ScrollPane controlsPane;
    @FXML private Button backBtn;

    @FXML private RadioButton singlyBtn;
    @FXML private RadioButton doublyBtn;
    @FXML private ToggleGroup modeGroup;

    // ========= STATE =========
    private boolean busy = false;
    private boolean doublyMode = false;

    // ========= DATA MODEL =========
    private static class NodeModel {
        double val;
        int next = -1;
        int prev = -1; // used in doubly mode
        NodeModel(double v) { val = v; }
    }

    private final ArrayList<NodeModel> nodes = new ArrayList<>();
    private int head = -1;

    // ========= INIT =========
    @FXML
    public void initialize() {
        modeGroup = new ToggleGroup();
        singlyBtn.setToggleGroup(modeGroup);
        doublyBtn.setToggleGroup(modeGroup);
        singlyBtn.setSelected(true);
        doublyMode = false;

        edgeTypeChoice.getItems().setAll("→", "↔");
        edgeTypeChoice.setValue("→");

        redrawAll();

        // important: redraw edges after layout happens
        Platform.runLater(this::drawEdges);
    }

    // ========= NAV =========
    @FXML
    void backHome() throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource("/org/example/VisuAlgorithm/hello-view.fxml"));
        Stage stage = (Stage) listBox.getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.show();
    }

    // ========= MODE =========
    @FXML
    void switchMode() {
        if (busy) {
            flashStatus("Busy... wait animation finish", true);
            if (doublyMode) doublyBtn.setSelected(true); else singlyBtn.setSelected(true);
            return;
        }
        doublyMode = doublyBtn.isSelected();
        if (doublyMode) rebuildPrevFromNext();
        else for (NodeModel n : nodes) n.prev = -1;

        redrawAll();
        Platform.runLater(this::drawEdges);
        flashStatus("Mode: " + (doublyMode ? "Doubly" : "Singly"), false);
    }

    // ========= BUSY LOCK =========
    private void setBusyUI(boolean isBusy) {
        if (controlsPane != null) controlsPane.setDisable(isBusy);
        if (backBtn != null) backBtn.setDisable(false);
        if (speedSlider != null) speedSlider.setDisable(false);
    }

    private void playSeq(SequentialTransition seq) {
        busy = true;
        setBusyUI(true);
        seq.setOnFinished(e -> {
            busy = false;
            setBusyUI(false);
            Platform.runLater(this::drawEdges);
        });
        seq.play();
    }

    private boolean guardBusy() {
        if (busy) { flashStatus("Busy... wait animation finish", true); return true; }
        return false;
    }

    // ========= HELPERS =========
    private double speed() { return speedSlider == null ? 1.0 : Math.max(0.2, speedSlider.getValue()); }

    private PauseTransition step(double baseSeconds, Runnable r) {
        PauseTransition p = new PauseTransition(Duration.seconds(baseSeconds / speed()));
        p.setOnFinished(e -> r.run());
        return p;
    }

    private Integer parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return null; }
    }
    private Double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return null; }
    }

    private void flashStatus(String msg, boolean error) {
        statusLabel.setText(msg);
        statusLabel.setStyle(error ? "-fx-text-fill:#ff6b6b;" : "-fx-text-fill:#51c4d3;");
        PauseTransition p = new PauseTransition(Duration.seconds(0.55));
        p.setOnFinished(e -> statusLabel.setStyle("-fx-text-fill:#51c4d3;"));
        p.play();
    }

    private void showInfoPopup(String title, String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle(title);
            a.setHeaderText(title);
            a.setContentText(msg);
            a.show();
        });
    }

    private String fmt(double x) {
        if (Double.isNaN(x) || Double.isInfinite(x)) return String.valueOf(x);
        long L = (long) x;
        if (Math.abs(x - L) < 1e-9) return String.valueOf(L);
        String s = String.format(Locale.US, "%.6f", x);
        while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) {
            if (s.endsWith(".")) { s = s.substring(0, s.length() - 1); break; }
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    // ========= NODE VIEW =========
    private StackPane makeNodeCell(double value) {
        Rectangle bg = new Rectangle(90, 70);
        bg.setArcWidth(16);
        bg.setArcHeight(16);
        bg.setStyle("-fx-fill:#0f3460; -fx-stroke:white; -fx-stroke-width:1;");

        Text t = new Text(fmt(value));
        t.setStyle("-fx-fill:white; -fx-font-size:18; -fx-font-weight:bold;");

        StackPane cell = new StackPane(bg, t);
        cell.setUserData(bg);
        return cell;
    }

    private void redrawNodes() {
        listBox.getChildren().clear();
        for (NodeModel n : nodes) listBox.getChildren().add(makeNodeCell(n.val));
    }

    private void redrawAll() {
        redrawNodes();
        clearHighlights();
        drawEdges();
        statusLabel.setText(nodes.isEmpty() ? "Create nodes to start" : ("nodes=" + nodes.size() + "  head=" + (head == -1 ? "null" : head)));
        statusLabel.setStyle("-fx-text-fill:#51c4d3;");
    }

    private void clearHighlights() {
        for (var node : listBox.getChildren()) {
            Rectangle r = (Rectangle) node.getUserData();
            if (r != null) r.setStyle(r.getStyle().replaceAll("-fx-fill:[^;]+;", "-fx-fill:#0f3460;"));
        }
    }

    private void highlightIndex(int idx, String hex) {
        if (idx < 0 || idx >= listBox.getChildren().size()) return;
        Rectangle r = (Rectangle) listBox.getChildren().get(idx).getUserData();
        if (r == null) return;
        r.setStyle(r.getStyle().replaceAll("-fx-fill:[^;]+;", "-fx-fill:" + hex + ";"));
    }

    // ========= EDGE DRAWING =========
    private Bounds nodeBoundsInEdgeLayer(int idx) {
        if (idx < 0 || idx >= listBox.getChildren().size()) return null;
        var node = listBox.getChildren().get(idx);
        Bounds bScene = node.localToScene(node.getBoundsInLocal());
        return edgeLayer.sceneToLocal(bScene);
    }

    private void drawEdges() {
        if (edgeLayer == null) return;
        edgeLayer.getChildren().clear();

        if (listBox.getChildren().isEmpty()) return;

        for (int from = 0; from < nodes.size(); from++) {
            int to = nodes.get(from).next;
            if (to < 0 || to >= nodes.size()) continue;

            // ✅ adjacent = straight
            if (to == from + 1) addStraightArrow(from, to, 0);
            else addCurvedArrow(from, to, 0);

            // ✅ doubly: show backward edge (offset so it doesn't overlap)
            if (doublyMode) {
                if (nodes.get(to).prev == from) {
                    // if backward is adjacent (from == to-1) => straight, else curved
                    if (from == to - 1) addStraightArrow(to, from, 14);
                    else addCurvedArrow(to, from, 18);
                }
            }
        }
    }

    /**
     * Draw a nice curved arrow from node A to node B.
     * offsetPx: curve vertical offset (for double arrows)
     */
    private void addCurvedArrow(int from, int to, double offsetPx) {
        Bounds a = nodeBoundsInEdgeLayer(from);
        Bounds b = nodeBoundsInEdgeLayer(to);
        if (a == null || b == null) return;

        double x1 = a.getMaxX();
        double y1 = a.getMinY() + a.getHeight() / 2.0;

        double x2 = b.getMinX();
        double y2 = b.getMinY() + b.getHeight() / 2.0;

        // if arrow goes backward (cycle or to-left), swap anchor to look nicer
        boolean backward = x2 < x1;
        if (backward) {
            x1 = a.getMinX();
            x2 = b.getMaxX();
        }

        double dx = Math.abs(x2 - x1);

        // curve height increases with distance (better for far edges/cycles)
        double curve = Math.min(140, 25 + dx * 0.35) + offsetPx;
        if (backward) curve += 25; // give more height for backward/cycle edges

        CubicCurve c = new CubicCurve();
        c.setStartX(x1);
        c.setStartY(y1);
        c.setEndX(x2);
        c.setEndY(y2);

        // control points for smooth curve (go upward)
        c.setControlX1(x1 + (x2 - x1) * 0.33);
        c.setControlY1(y1 - curve);
        c.setControlX2(x1 + (x2 - x1) * 0.66);
        c.setControlY2(y2 - curve);

        c.setFill(null);
        c.setStyle("-fx-stroke: rgba(255,255,255,0.85); -fx-stroke-width: 2.2;");

        // arrow head at end
        Polygon head = arrowHead(c.getEndX(), c.getEndY(), c.getControlX2(), c.getControlY2());

        edgeLayer.getChildren().addAll(c, head);
    }

    // straight arrow between adjacent nodes
    private void addStraightArrow(int from, int to, double yOffset) {
        Bounds a = nodeBoundsInEdgeLayer(from);
        Bounds b = nodeBoundsInEdgeLayer(to);
        if (a == null || b == null) return;

        // start at right middle of "from" and end at left middle of "to"
        double x1 = a.getMaxX();
        double y1 = a.getMinY() + a.getHeight() / 2.0 + yOffset;

        double x2 = b.getMinX();
        double y2 = b.getMinY() + b.getHeight() / 2.0 + yOffset;

        Line line = new Line(x1, y1, x2, y2);
        line.setStyle("-fx-stroke: rgba(255,255,255,0.85); -fx-stroke-width: 2.2;");

        Polygon head = arrowHeadLine(x2, y2, x1, y1);

        edgeLayer.getChildren().addAll(line, head);
    }

    // arrow head for straight line using start->end direction
    private Polygon arrowHeadLine(double ex, double ey, double sx, double sy) {
        double angle = Math.atan2(ey - sy, ex - sx);

        double len = 12;
        double w = 7;

        double xA = ex;
        double yA = ey;

        double xB = ex - len * Math.cos(angle) + w * Math.sin(angle);
        double yB = ey - len * Math.sin(angle) - w * Math.cos(angle);

        double xC = ex - len * Math.cos(angle) - w * Math.sin(angle);
        double yC = ey - len * Math.sin(angle) + w * Math.cos(angle);

        Polygon p = new Polygon(xA, yA, xB, yB, xC, yC);
        p.setStyle("-fx-fill: rgba(255,255,255,0.90);");
        return p;
    }
    /**
     * Create arrow head triangle at (ex,ey).
     * Direction estimated using last control point (cx,cy) -> end point.
     */
    private Polygon arrowHead(double ex, double ey, double cx, double cy) {
        double angle = Math.atan2(ey - cy, ex - cx);

        double len = 12;
        double w = 7;

        double xA = ex;
        double yA = ey;

        double xB = ex - len * Math.cos(angle) + w * Math.sin(angle);
        double yB = ey - len * Math.sin(angle) - w * Math.cos(angle);

        double xC = ex - len * Math.cos(angle) - w * Math.sin(angle);
        double yC = ey - len * Math.sin(angle) + w * Math.cos(angle);

        Polygon p = new Polygon(xA, yA, xB, yB, xC, yC);
        p.setStyle("-fx-fill: rgba(255,255,255,0.90);");
        return p;
    }

    // ========= LIST LOGIC HELPERS =========
    private void rebuildPrevFromNext() {
        for (NodeModel n : nodes) n.prev = -1;
        for (int i = 0; i < nodes.size(); i++) {
            int nx = nodes.get(i).next;
            if (nx >= 0 && nx < nodes.size()) nodes.get(nx).prev = i;
        }
    }

    private List<Integer> linearOrderFromHead(int maxSteps) {
        ArrayList<Integer> order = new ArrayList<>();
        int cur = head;
        int steps = 0;
        HashSet<Integer> seen = new HashSet<>();
        while (cur != -1 && steps < maxSteps) {
            if (!seen.add(cur)) break;
            order.add(cur);
            cur = nodes.get(cur).next;
            steps++;
        }
        return order;
    }

    // ========= BUTTON OPS =========
    @FXML
    void clear() {
        if (guardBusy()) return;
        nodes.clear();
        head = -1;
        redrawAll();
        flashStatus("Cleared ✅", false);
    }

    @FXML
    void insertHead() {
        if (guardBusy()) return;

        Double v = parseDouble(valueField.getText());
        if (v == null) { flashStatus("Invalid value (int/float/double)", true); return; }

        SequentialTransition seq = new SequentialTransition();
        seq.getChildren().add(step(0.12, () -> flashStatus("Insert HEAD: " + fmt(v), false)));

        seq.getChildren().add(step(0.18, () -> {
            NodeModel n = new NodeModel(v);
            nodes.add(n);
            int newIdx = nodes.size() - 1;

            n.next = head;
            head = newIdx;

            if (doublyMode) rebuildPrevFromNext();

            redrawAll();
            highlightIndex(head, "#2ecc71");
        }));

        playSeq(seq);
    }

    @FXML
    void insertTail() {
        if (guardBusy()) return;

        Double v = parseDouble(valueField.getText());
        if (v == null) { flashStatus("Invalid value (int/float/double)", true); return; }

        SequentialTransition seq = new SequentialTransition();
        seq.getChildren().add(step(0.12, () -> flashStatus("Insert TAIL: " + fmt(v), false)));

        seq.getChildren().add(step(0.18, () -> {
            nodes.add(new NodeModel(v));
            int newIdx = nodes.size() - 1;

            if (head == -1) {
                head = newIdx;
            } else {
                // find tail by following next from head (cap)
                List<Integer> order = linearOrderFromHead(nodes.size() + 10);
                int tail = order.get(order.size() - 1);
                nodes.get(tail).next = newIdx;
            }

            if (doublyMode) rebuildPrevFromNext();

            redrawAll();
            highlightIndex(newIdx, "#2ecc71");
        }));

        playSeq(seq);
    }

    @FXML
    void insertAt() {
        if (guardBusy()) return;

        Integer idx = parseInt(indexField.getText());
        if (idx == null || idx < 0) { flashStatus("Invalid index", true); return; }

        Double v = parseDouble(valueField.getText());
        if (v == null) { flashStatus("Invalid value (int/float/double)", true); return; }

        if (idx == 0) { insertHead(); return; }

        List<Integer> order = linearOrderFromHead(nodes.size() + 10);
        if (order.isEmpty()) { flashStatus("List empty (use insert head/tail)", true); return; }
        if (idx >= order.size()) { insertTail(); return; }

        SequentialTransition seq = new SequentialTransition();
        seq.getChildren().add(step(0.12, () -> flashStatus("Insert at linear index " + idx, false)));

        // animate traversal
        for (int i = 0; i < idx; i++) {
            int hi = order.get(i);
            seq.getChildren().add(step(0.16, () -> {
                redrawAll();
                clearHighlights();
                highlightIndex(hi, "#f1c40f");
            }));
        }

        seq.getChildren().add(step(0.18, () -> {
            nodes.add(new NodeModel(v));
            int newIdx = nodes.size() - 1;

            int prevNode = order.get(idx - 1);
            int nextNode = nodes.get(prevNode).next;

            nodes.get(newIdx).next = nextNode;
            nodes.get(prevNode).next = newIdx;

            if (doublyMode) rebuildPrevFromNext();

            redrawAll();
            highlightIndex(newIdx, "#2ecc71");
        }));

        playSeq(seq);
    }

    @FXML
    void deleteAt() {
        if (guardBusy()) return;

        Integer idx = parseInt(indexField.getText());
        if (idx == null || idx < 0) { flashStatus("Invalid index", true); return; }
        if (head == -1) { flashStatus("List is empty", true); return; }

        List<Integer> order = linearOrderFromHead(nodes.size() + 10);
        if (idx >= order.size()) { flashStatus("Index out of range", true); return; }

        int target = order.get(idx);

        SequentialTransition seq = new SequentialTransition();
        for (int i = 0; i <= idx; i++) {
            int hi = order.get(i);
            seq.getChildren().add(step(0.16, () -> {
                redrawAll();
                clearHighlights();
                highlightIndex(hi, "#f1c40f");
            }));
        }

        seq.getChildren().add(step(0.16, () -> {
            redrawAll();
            clearHighlights();
            highlightIndex(target, "#e74c3c");
        }));

        seq.getChildren().add(step(0.20, () -> {
            if (target == head) {
                head = nodes.get(head).next;
            } else {
                int prev = order.get(idx - 1);
                nodes.get(prev).next = nodes.get(target).next;
            }

            // detach (keep indices stable)
            nodes.get(target).next = -1;
            nodes.get(target).prev = -1;

            if (doublyMode) rebuildPrevFromNext();

            redrawAll();
            flashStatus("Deleted (detached) ✅", false);
        }));

        playSeq(seq);
    }

    @FXML
    void search() {
        if (guardBusy()) return;

        Double target = parseDouble(searchField.getText());
        if (target == null) { flashStatus("Invalid search value", true); return; }
        if (head == -1) { flashStatus("List is empty", true); return; }

        List<Integer> order = linearOrderFromHead(nodes.size() + 20);

        SequentialTransition seq = new SequentialTransition();
        final boolean[] found = {false};

        for (int nodeIdx : order) {
            double val = nodes.get(nodeIdx).val;
            seq.getChildren().add(step(0.16, () -> {
                redrawAll();
                clearHighlights();
                highlightIndex(nodeIdx, "#f1c40f");
                statusLabel.setText("Check: " + fmt(val));
            }));

            if (Math.abs(val - target) < 1e-9) {
                found[0] = true;
                seq.getChildren().add(step(0.18, () -> {
                    redrawAll();
                    clearHighlights();
                    highlightIndex(nodeIdx, "#2ecc71");
                    flashStatus("FOUND ✅", false);
                }));
                break;
            }
        }

        seq.setOnFinished(e -> {
            if (!found[0]) showInfoPopup("Not Found", "Value not found on head path.");
        });

        playSeq(seq);
    }

    @FXML
    void reverse() {
        if (guardBusy()) return;
        if (head == -1) { flashStatus("List is empty", true); return; }

        // reverse only safe when no cycles on head path
        List<Integer> order = linearOrderFromHead(nodes.size() + 10);
        if (order.size() <= 1) { flashStatus("Need 2+ nodes", true); return; }

        SequentialTransition seq = new SequentialTransition();
        seq.getChildren().add(step(0.12, () -> flashStatus("Reversing...", false)));

        for (int hi : order) {
            seq.getChildren().add(step(0.14, () -> {
                redrawAll();
                clearHighlights();
                highlightIndex(hi, "#f1c40f");
            }));
        }

        seq.getChildren().add(step(0.20, () -> {
            for (int i = 0; i < order.size(); i++) {
                int cur = order.get(i);
                nodes.get(cur).next = (i == 0) ? -1 : order.get(i - 1);
            }
            head = order.get(order.size() - 1);

            if (doublyMode) rebuildPrevFromNext();

            redrawAll();
            flashStatus("Reversed ✅", false);
        }));

        playSeq(seq);
    }

    // ========= EDGE BUILDER =========
    @FXML
    void applyEdge() {
        if (guardBusy()) return;

        Integer from = parseInt(fromField.getText());
        Integer to = parseInt(toField.getText());
        if (from == null || to == null) { flashStatus("from/to must be integers", true); return; }
        if (from < 0 || from >= nodes.size() || to < 0 || to >= nodes.size()) {
            flashStatus("from/to out of range", true);
            return;
        }

        String type = edgeTypeChoice.getValue();
        if (type == null) type = "→";

        int oldNext = nodes.get(from).next;

        SequentialTransition seq = new SequentialTransition();
        String msg = "Replace edge: " + from + " was " + (oldNext == -1 ? "null" : oldNext) + " -> " + to;
        seq.getChildren().add(step(0.12, () -> flashStatus(msg, false)));

        seq.getChildren().add(step(0.16, () -> {
            redrawAll();
            clearHighlights();
            highlightIndex(from, "#f1c40f");
            highlightIndex(to, "#9b59b6");
        }));

        String finalType = type;
        seq.getChildren().add(step(0.18, () -> {
            nodes.get(from).next = to;

            if ("↔".equals(finalType)) {
                nodes.get(to).next = from;
            }

            if (doublyMode) rebuildPrevFromNext();

            redrawAll();
            highlightIndex(from, "#2ecc71");
            highlightIndex(to, "#2ecc71");
            flashStatus("Edge applied ✅", false);
        }));

        playSeq(seq);
    }

    @FXML
    void removeEdge() {
        if (guardBusy()) return;

        Integer from = parseInt(fromField.getText());
        if (from == null || from < 0 || from >= nodes.size()) { flashStatus("Invalid from", true); return; }

        SequentialTransition seq = new SequentialTransition();
        seq.getChildren().add(step(0.12, () -> flashStatus("Removing edge from " + from, false)));

        seq.getChildren().add(step(0.16, () -> {
            redrawAll();
            clearHighlights();
            highlightIndex(from, "#e67e22");
        }));

        seq.getChildren().add(step(0.18, () -> {
            nodes.get(from).next = -1;
            if (doublyMode) rebuildPrevFromNext();
            redrawAll();
            flashStatus("Removed ✅", false);
        }));

        playSeq(seq);
    }

    // ========= CYCLE FIND + ANIMATION (your previous logic can stay) =========
    private List<List<Integer>> findAllCycles() {
        int n = nodes.size();
        int[] state = new int[n]; // 0 unvisited, 1 visiting, 2 done
        int[] parent = new int[n];
        Arrays.fill(parent, -1);

        List<List<Integer>> cycles = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            if (state[i] != 0) continue;

            int cur = i;
            while (cur != -1 && cur >= 0 && cur < n && state[cur] == 0) {
                state[cur] = 1;
                int nx = nodes.get(cur).next;
                if (nx != -1 && nx >= 0 && nx < n) parent[nx] = cur;
                cur = nx;
            }

            if (cur != -1 && cur >= 0 && cur < n && state[cur] == 1) {
                ArrayList<Integer> cyc = new ArrayList<>();
                cyc.add(cur);
                int p = parent[cur];
                while (p != -1 && p != cur) {
                    cyc.add(p);
                    p = parent[p];
                }
                Collections.reverse(cyc);
                if (!cyc.isEmpty()) cycles.add(normalizeCycle(cyc));
            }

            int x = i, cap = 0;
            while (x != -1 && x >= 0 && x < n && state[x] == 1 && cap < n + 5) {
                state[x] = 2;
                x = nodes.get(x).next;
                cap++;
            }
        }

        // dedupe
        HashSet<String> seen = new HashSet<>();
        ArrayList<List<Integer>> out = new ArrayList<>();
        for (List<Integer> c : cycles) {
            String k = c.toString();
            if (seen.add(k)) out.add(c);
        }
        return out;
    }

    private List<Integer> normalizeCycle(List<Integer> c) {
        int k = c.size();
        int minPos = 0;
        for (int i = 1; i < k; i++) if (c.get(i) < c.get(minPos)) minPos = i;
        ArrayList<Integer> out = new ArrayList<>(k);
        for (int i = 0; i < k; i++) out.add(c.get((minPos + i) % k));
        return out;
    }

    @FXML
    void findAndAnimateCycles() {
        if (guardBusy()) return;
        if (nodes.isEmpty()) { flashStatus("No nodes!", true); return; }

        List<List<Integer>> cycles = findAllCycles();
        if (cycles.isEmpty()) {
            flashStatus("No cycles ✅", false);
            showInfoPopup("Cycle Check", "No cycle found.");
            return;
        }

        LinkedHashSet<Integer> all = new LinkedHashSet<>();
        for (List<Integer> c : cycles) all.addAll(c);

        SequentialTransition seq = new SequentialTransition();
        seq.getChildren().add(step(0.12, () -> {
            redrawAll();
            clearHighlights();
            flashStatus("Found " + cycles.size() + " cycle(s).", false);
        }));

        for (int ci = 0; ci < cycles.size(); ci++) {
            List<Integer> cycle = cycles.get(ci);
            int cycleNo = ci + 1;

            seq.getChildren().add(step(0.12, () -> {
                redrawAll();
                clearHighlights();
                statusLabel.setText("Cycle " + cycleNo + "/" + cycles.size());
            }));

            for (int nodeIdx : cycle) {
                seq.getChildren().add(step(0.18, () -> {
                    highlightIndex(nodeIdx, "#e67e22");
                }));
            }

            seq.getChildren().add(step(0.18, () -> clearHighlights()));
        }

        seq.getChildren().add(step(0.20, () -> {
            redrawAll();
            clearHighlights();
            for (int idx : all) highlightIndex(idx, "#2ecc71");
            drawEdges();
            showInfoPopup("Cycles Found", "Total cycles: " + cycles.size() + "\nNodes: " + all);
        }));

        playSeq(seq);
    }
}