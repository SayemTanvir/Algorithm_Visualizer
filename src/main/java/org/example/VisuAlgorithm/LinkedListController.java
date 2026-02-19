package org.example.VisuAlgorithm;

import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
//import javafx.scene.layout.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;

public class LinkedListController {

    // ========= UI =========
    @FXML private HBox listBox;
    @FXML private Label statusLabel;

    @FXML private TextField valueField;
    @FXML private TextField indexField;
    @FXML private TextField searchField;

    @FXML private Slider speedSlider;

    // to lock UI during animations
    @FXML private ScrollPane controlsPane;
    @FXML private Button backBtn;

    private boolean busy = false;

    // ========= DATA MODEL =========
    private static class NodeModel {
        double val;
        NodeModel next;
        NodeModel(double v) { val = v; }
    }

    private NodeModel head = null;
    private int size = 0;

    // floating compare tolerance
    private static final double EPS = 1e-9;

    // ========= NAV =========
    @FXML
    void backHome() throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource("/org/example/VisuAlgorithm/hello-view.fxml"));
        Stage stage = (Stage) listBox.getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.show();
    }

    // ========= BUSY LOCK =========
    private void setBusyUI(boolean isBusy) {
        if (controlsPane != null) controlsPane.setDisable(isBusy);
        if (backBtn != null) backBtn.setDisable(false);          // keep back
        if (speedSlider != null) speedSlider.setDisable(false);  // keep speed
    }

    private void playSeq(SequentialTransition seq) {
        busy = true;
        setBusyUI(true);

        seq.setOnFinished(e -> {
            busy = false;
            setBusyUI(false);
        });

        seq.play();
    }

    private boolean guardBusy() {
        if (busy) {
            flashStatus("Busy... wait animation finish", true);
            return true;
        }
        return false;
    }

    // ========= HELPERS =========
    private double speed() {
        return speedSlider == null ? 1.0 : Math.max(0.2, speedSlider.getValue());
    }

    private PauseTransition step(double baseSeconds, Runnable r) {
        PauseTransition p = new PauseTransition(Duration.seconds(baseSeconds / speed()));
        p.setOnFinished(e -> r.run());
        return p;
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    // ✅ NEW: parse doubles/floats
    private Double parseDouble(String s) {
        try {
            if (s == null) return null;
            String t = s.trim();
            if (t.isEmpty()) return null;
            return Double.parseDouble(t);
        } catch (Exception e) {
            return null;
        }
    }

    // ✅ NEW: format numbers nicely for UI
    private String fmt(double x) {
        if (Math.abs(x - Math.rint(x)) < 1e-9) return String.valueOf((long) Math.rint(x));
        return String.valueOf(x);
    }

    // ✅ NEW: safe floating comparison
    private boolean same(double a, double b) {
        return Math.abs(a - b) < EPS;
    }

    private void flashStatus(String msg, boolean error) {
        statusLabel.setText(msg);
        statusLabel.setStyle(error ? "-fx-text-fill:#ff6b6b;" : "-fx-text-fill:#51c4d3;");
        PauseTransition p = new PauseTransition(Duration.seconds(0.6));
        p.setOnFinished(e -> statusLabel.setStyle("-fx-text-fill:#51c4d3;"));
        p.play();
    }

    // ✅ never showAndWait during animation
    private void showInfoPopup(String title, String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle(title);
            a.setHeaderText(title);
            a.setContentText(msg);
            a.show();
        });
    }

    // ========= DRAW =========
    private StackPane makeNodeCell(String text) {
        Rectangle bg = new Rectangle(70, 60);
        bg.setArcWidth(12);
        bg.setArcHeight(12);
        bg.setStyle("-fx-fill:#0f3460; -fx-stroke:white; -fx-stroke-width:1;");

        Text t = new Text(text);
        t.setStyle("-fx-fill:white; -fx-font-size:16; -fx-font-weight:bold;");

        StackPane cell = new StackPane(bg, t);
        cell.setUserData(bg);
        return cell;
    }

    private StackPane makeArrowCell() {
        Rectangle bg = new Rectangle(40, 24);
        bg.setArcWidth(10);
        bg.setArcHeight(10);
        bg.setStyle("-fx-fill:#2c2c54; -fx-stroke:white; -fx-stroke-width:1;");

        Text t = new Text("→");
        t.setStyle("-fx-fill:#bdbdbd; -fx-font-size:16; -fx-font-weight:bold;");

        StackPane cell = new StackPane(bg, t);
        cell.setUserData(bg);
        return cell;
    }

    private void redraw() {
        listBox.getChildren().clear();

        NodeModel cur = head;
        while (cur != null) {
            listBox.getChildren().add(makeNodeCell(fmt(cur.val)));  // ✅ use fmt()
            if (cur.next != null) listBox.getChildren().add(makeArrowCell());
            cur = cur.next;
        }

        statusLabel.setText(size == 0 ? "List is empty" : "size=" + size);
        statusLabel.setStyle("-fx-text-fill:#51c4d3;");
    }

    // node0 at child 0, arrow at 1, node1 at 2 ...
    private int nodeIndexToChildIndex(int nodeIndex) {
        return nodeIndex * 2;
    }

    private void clearHighlights() {
        for (int i = 0; i < listBox.getChildren().size(); i += 2) {
            Node n = listBox.getChildren().get(i);
            Rectangle r = (Rectangle) n.getUserData();
            if (r != null) r.setStyle(r.getStyle().replaceAll("-fx-fill:[^;]+;", "-fx-fill:#0f3460;"));
        }
    }

    private void highlightNode(int nodeIndex, String color) {
        int child = nodeIndexToChildIndex(nodeIndex);
        if (child < 0 || child >= listBox.getChildren().size()) return;

        Node n = listBox.getChildren().get(child);
        Rectangle r = (Rectangle) n.getUserData();
        if (r == null) return;

        r.setStyle(r.getStyle().replaceAll("-fx-fill:[^;]+;", "-fx-fill:" + color + ";"));
    }

    private boolean indexOkForInsert(int idx) {
        if (idx < 0 || idx > size) { flashStatus("Index must be 0..size", true); return false; }
        return true;
    }

    private boolean indexOkForAccess(int idx) {
        if (idx < 0 || idx >= size) { flashStatus("Index must be 0..size-1", true); return false; }
        return true;
    }

    // ========= OPERATIONS =========
    @FXML
    void clear() {
        if (guardBusy()) return;
        head = null;
        size = 0;
        redraw();
        flashStatus("Cleared ✅", false);
    }

    @FXML
    void insertHead() {
        if (guardBusy()) return;

        Double v = parseDouble(valueField.getText());
        if (v == null) { flashStatus("Invalid value! (int/float/double)", true); return; }

        SequentialTransition seq = new SequentialTransition();
        seq.getChildren().add(step(0.15, () -> flashStatus("Insert at HEAD: " + fmt(v), false)));

        seq.getChildren().add(step(0.22, () -> {
            NodeModel n = new NodeModel(v);
            n.next = head;
            head = n;
            size++;

            redraw();
            clearHighlights();
            highlightNode(0, "#2ecc71");
            statusLabel.setText("Inserted at head ✅  size=" + size);
        }));

        playSeq(seq);
    }

    @FXML
    void insertTail() {
        if (guardBusy()) return;

        Double v = parseDouble(valueField.getText());
        if (v == null) { flashStatus("Invalid value! (int/float/double)", true); return; }

        SequentialTransition seq = new SequentialTransition();
        seq.getChildren().add(step(0.15, () -> flashStatus("Insert at TAIL: " + fmt(v), false)));

        if (head == null) {
            seq.getChildren().add(step(0.22, () -> {
                head = new NodeModel(v);
                size = 1;

                redraw();
                clearHighlights();
                highlightNode(0, "#2ecc71");
                statusLabel.setText("Inserted at tail ✅  size=" + size);
            }));
            playSeq(seq);
            return;
        }

        // animate traverse to last
        NodeModel cur = head;
        int idx = 0;
        while (cur.next != null) {
            final int fIdx = idx;
            seq.getChildren().add(step(0.18, () -> {
                redraw();
                clearHighlights();
                highlightNode(fIdx, "#f1c40f");
                statusLabel.setText("Traverse: at index " + fIdx);
            }));
            cur = cur.next;
            idx++;
        }

        final int lastIdx = idx;
        seq.getChildren().add(step(0.22, () -> {
            NodeModel tail = head;
            while (tail.next != null) tail = tail.next;
            tail.next = new NodeModel(v);
            size++;

            redraw();
            clearHighlights();
            highlightNode(lastIdx + 1, "#2ecc71");
            statusLabel.setText("Inserted at tail ✅  size=" + size);
        }));

        playSeq(seq);
    }

    @FXML
    void insertAt() {
        if (guardBusy()) return;

        int idx = parseInt(indexField.getText(), -1);
        if (!indexOkForInsert(idx)) return;

        Double v = parseDouble(valueField.getText());
        if (v == null) { flashStatus("Invalid value! (int/float/double)", true); return; }

        if (idx == 0) { insertHead(); return; }
        if (idx == size) { insertTail(); return; }

        SequentialTransition seq = new SequentialTransition();

        for (int i = 0; i < idx; i++) {
            final int f = i;
            seq.getChildren().add(step(0.18, () -> {
                redraw();
                clearHighlights();
                highlightNode(f, "#f1c40f");
                statusLabel.setText("Traverse: to insert at " + idx + " (now at " + f + ")");
            }));
        }

        seq.getChildren().add(step(0.22, () -> {
            NodeModel prev = head;
            for (int i = 0; i < idx - 1; i++) prev = prev.next;

            NodeModel n = new NodeModel(v);
            n.next = prev.next;
            prev.next = n;
            size++;

            redraw();
            clearHighlights();
            highlightNode(idx, "#2ecc71");
            statusLabel.setText("Inserted " + fmt(v) + " at index " + idx + " ✅  size=" + size);
        }));

        playSeq(seq);
    }

    @FXML
    void deleteAt() {
        if (guardBusy()) return;

        int idx = parseInt(indexField.getText(), -1);
        if (!indexOkForAccess(idx)) return;
        if (head == null) { flashStatus("List is empty!", true); return; }

        SequentialTransition seq = new SequentialTransition();

        for (int i = 0; i <= idx; i++) {
            final int f = i;
            seq.getChildren().add(step(0.18, () -> {
                redraw();
                clearHighlights();
                highlightNode(f, "#f1c40f");
                statusLabel.setText("Traverse: at index " + f);
            }));
        }

        seq.getChildren().add(step(0.18, () -> {
            redraw();
            clearHighlights();
            highlightNode(idx, "#e74c3c");
            statusLabel.setText("Deleting index " + idx);
        }));

        seq.getChildren().add(step(0.22, () -> {
            if (idx == 0) {
                head = head.next;
            } else {
                NodeModel prev = head;
                for (int i = 0; i < idx - 1; i++) prev = prev.next;

                if (prev == null || prev.next == null) {
                    flashStatus("Delete failed (corrupted list)", true);
                    redraw();
                    return;
                }
                prev.next = prev.next.next;
            }

            size--;
            redraw();
            clearHighlights();
            flashStatus("Deleted index " + idx + " ✅", false);
        }));

        playSeq(seq);
    }

    @FXML
    void search() {
        if (guardBusy()) return;

        Double target = parseDouble(searchField.getText());
        if (target == null) { flashStatus("Invalid search value! (int/float/double)", true); return; }

        if (size == 0) {
            flashStatus("List is empty", true);
            showInfoPopup("Not Found", "List is empty.");
            return;
        }

        SequentialTransition seq = new SequentialTransition();
        final boolean[] found = {false};

        NodeModel cur = head;
        int idx = 0;

        while (cur != null) {
            final int fIdx = idx;
            final double curVal = cur.val;

            seq.getChildren().add(step(0.18, () -> {
                redraw();
                clearHighlights();
                highlightNode(fIdx, "#f1c40f");
                statusLabel.setText("Check index " + fIdx + " = " + fmt(curVal));
            }));

            if (same(curVal, target)) {
                found[0] = true;
                seq.getChildren().add(step(0.22, () -> {
                    redraw();
                    clearHighlights();
                    highlightNode(fIdx, "#2ecc71");
                    statusLabel.setText("FOUND at index " + fIdx + " ✅");
                }));
                break;
            }

            cur = cur.next;
            idx++;
        }

        seq.setOnFinished(e -> {
            if (!found[0]) {
                flashStatus("NOT FOUND ❌", true);
                showInfoPopup("Not Found", "Value " + fmt(target) + " not in the list.");
            }
        });

        playSeq(seq);
    }

    @FXML
    void reverse() {
        if (guardBusy()) return;

        if (size <= 1) { flashStatus("Need at least 2 nodes", true); return; }

        SequentialTransition seq = new SequentialTransition();
        seq.getChildren().add(step(0.15, () -> flashStatus("Reversing...", false)));

        for (int i = 0; i < size; i++) {
            final int f = i;
            seq.getChildren().add(step(0.18, () -> {
                redraw();
                clearHighlights();
                highlightNode(f, "#f1c40f");
                statusLabel.setText("Reverse: visiting index " + f);
            }));
        }

        seq.getChildren().add(step(0.25, () -> {
            NodeModel prev = null;
            NodeModel cur = head;
            while (cur != null) {
                NodeModel next = cur.next;
                cur.next = prev;
                prev = cur;
                cur = next;
            }
            head = prev;

            redraw();
            clearHighlights();
            flashStatus("Reversed ✅", false);
        }));

        playSeq(seq);
    }
}
