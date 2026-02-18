package org.example.VisuAlgorithm;

import javafx.scene.layout.VBox;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.Random;

public class ArrayController {

    // UI
    @FXML private HBox arrayBox;
    @FXML private Label statusLabel;
    @FXML private Label sizeCapLabel;
    @FXML private Label modeLabel;

    @FXML private RadioButton fixedBtn;
    @FXML private RadioButton dynamicBtn;

    @FXML private TextField sizeField;
    @FXML private TextField indexField;
    @FXML private TextField valueField;
    @FXML private TextField searchField;

    @FXML private Slider speedSlider;
    @FXML private ToggleGroup modeGroup;

    // Data
    private Integer[] fixed;      // null means empty
    private int fixedLast = -1;   // last used index

    private int[] dyn;
    private int dynSize = 0;
    private int dynCap = 0;

    private boolean dynamicMode = false;
    private final Random rnd = new Random();

    @FXML
    public void initialize() {
        modeGroup = new ToggleGroup();
        fixedBtn.setToggleGroup(modeGroup);
        dynamicBtn.setToggleGroup(modeGroup);
        fixedBtn.setSelected(true);
        switchMode();
    }

    // ------------ Navigation ------------
    @FXML
    void backHome() throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource("/org/example/VisuAlgorithm/hello-view.fxml"));
        Stage stage = (Stage) arrayBox.getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.show();
    }


    // ------------ Mode ------------
    @FXML
    void switchMode() {
        dynamicMode = dynamicBtn.isSelected();
        modeLabel.setText(dynamicMode ? "Mode: Dynamic Array" : "Mode: Fixed Array");
        clearAll();
        statusLabel.setText("Create an array to start");
        sizeCapLabel.setText("");
    }

    private void clearAll() {
        fixed = null;
        fixedLast = -1;
        dyn = null;
        dynSize = 0;
        dynCap = 0;
        arrayBox.getChildren().clear();
    }

    // ------------ Create / Random ------------
    @FXML
    void createArray() {
        int n = parseInt(sizeField.getText(), 10);
        if (n <= 0) { flashStatus("Invalid size!", true); return; }

        if (!dynamicMode) {
            fixed = new Integer[n];
            fixedLast = -1;
            flashStatus("Fixed array created (size = " + n + ")", false);
            drawFixed();
        } else {
            dynCap = n;
            dynSize = 0;
            dyn = new int[dynCap];
            flashStatus("Dynamic array created (capacity = " + dynCap + ")", false);
            drawDynamic();
        }
    }

    @FXML
    void randomFill() {
        if (!ensureCreated()) return;

        if (!dynamicMode) {
            // fill random into all slots
            for (int i = 0; i < fixed.length; i++) fixed[i] = rnd.nextInt(90) + 10;
            fixedLast = fixed.length - 1;
            flashStatus("Fixed array random filled", false);
            drawFixed();
        } else {
            // fill up to size = cap
            dynSize = dynCap;
            for (int i = 0; i < dynSize; i++) dyn[i] = rnd.nextInt(90) + 10;
            flashStatus("Dynamic array random filled (size = capacity)", false);
            drawDynamic();
        }
    }

    // ------------ Basic Ops ------------
    @FXML
    void getValue() {
        if (!ensureCreated()) return;
        int idx = parseInt(indexField.getText(), -1);
        if (!inRange(idx)) return;

        highlightOnce(idx, "#f1c40f", "Get at index " + idx +
                " = " + (dynamicMode ? dyn[idx] : fixed[idx]));
    }

    @FXML
    void setValue() {
        if (!ensureCreated()) return;
        int idx = parseInt(indexField.getText(), -1);
        Integer val = parseInteger(valueField.getText());
        if (val == null) { flashStatus("Invalid value!", true); return; }
        if (!inRange(idx)) return;

        if (!dynamicMode) {
            fixed[idx] = val;
            fixedLast = Math.max(fixedLast, idx);
            drawFixed();
        } else {
            if (idx >= dynSize) { flashStatus("Index must be < size (" + dynSize + ")", true); return; }
            dyn[idx] = val;
            drawDynamic();
        }
        highlightOnce(idx, "#2ecc71", "Set index " + idx + " = " + val);
    }

    @FXML
    void insertAt() {
        if (!ensureCreated()) return;
        int idx = parseInt(indexField.getText(), -1);
        Integer val = parseInteger(valueField.getText());
        if (val == null) { flashStatus("Invalid value!", true); return; }

        if (!dynamicMode) {
            if (idx < 0 || idx >= fixed.length) { flashStatus("Index out of range!", true); return; }
            if (fixedLast == fixed.length - 1) { flashStatus("Fixed array is full!", true); return; }

            int insertPos = Math.min(idx, fixedLast + 1);

            // animate shifting right
            SequentialTransition seq = new SequentialTransition();
            for (int i = fixedLast; i >= insertPos; i--) {
                final int from = i, to = i + 1;
                seq.getChildren().add(step(0.25, () -> {
                    fixed[to] = fixed[from];
                    drawFixed();
                    colorCell(to, "#3498db");
                    colorCell(from, "#3498db");
                    statusLabel.setText("Shift: " + from + " -> " + to);
                }));
            }
            seq.getChildren().add(step(0.25, () -> {
                fixed[insertPos] = val;
                fixedLast++;
                drawFixed();
                colorCell(insertPos, "#2ecc71");
                statusLabel.setText("Inserted " + val + " at " + insertPos);
            }));
            seq.play();

        } else {
            if (idx < 0 || idx > dynSize) { flashStatus("Index must be 0..size", true); return; }

            SequentialTransition seq = new SequentialTransition();

            // resize if full
            if (dynSize == dynCap) {
                int newCap = Math.max(1, dynCap * 2);
                seq.getChildren().add(step(0.35, () -> {
                    statusLabel.setText("Resize: capacity " + dynCap + " -> " + newCap);
                    sizeCapLabel.setText("size=" + dynSize + "  capacity=" + newCap + " (copying...)");
                }));
                seq.getChildren().add(step(0.35, () -> {
                    int[] newArr = new int[newCap];
                    System.arraycopy(dyn, 0, newArr, 0, dynSize);
                    dyn = newArr;
                    dynCap = newCap;
                    drawDynamic();
                }));
            }

            // shift right
            for (int i = dynSize - 1; i >= idx; i--) {
                final int from = i, to = i + 1;
                seq.getChildren().add(step(0.22, () -> {
                    dyn[to] = dyn[from];
                    drawDynamic();
                    colorCell(to, "#3498db");
                    colorCell(from, "#3498db");
                    statusLabel.setText("Shift: " + from + " -> " + to);
                }));
            }

            // insert
            seq.getChildren().add(step(0.22, () -> {
                dyn[idx] = val;
                dynSize++;
                drawDynamic();
                colorCell(idx, "#2ecc71");
                statusLabel.setText("Inserted " + val + " at " + idx);
            }));

            seq.play();
        }
    }

    @FXML
    void deleteAt() {
        if (!ensureCreated()) return;
        int idx = parseInt(indexField.getText(), -1);

        if (!dynamicMode) {
            if (idx < 0 || idx > fixedLast) { flashStatus("Index must be 0..lastUsed (" + fixedLast + ")", true); return; }

            SequentialTransition seq = new SequentialTransition();
            seq.getChildren().add(step(0.2, () -> {
                statusLabel.setText("Delete at " + idx);
                colorCell(idx, "#e74c3c");
            }));

            for (int i = idx; i < fixedLast; i++) {
                final int from = i + 1, to = i;
                seq.getChildren().add(step(0.22, () -> {
                    fixed[to] = fixed[from];
                    drawFixed();
                    colorCell(to, "#3498db");
                    colorCell(from, "#3498db");
                    statusLabel.setText("Shift: " + from + " -> " + to);
                }));
            }

            seq.getChildren().add(step(0.2, () -> {
                fixed[fixedLast] = null;
                fixedLast--;
                drawFixed();
                statusLabel.setText("Deleted!");
            }));
            seq.play();

        } else {
            if (idx < 0 || idx >= dynSize) { flashStatus("Index must be 0..size-1", true); return; }

            SequentialTransition seq = new SequentialTransition();
            seq.getChildren().add(step(0.2, () -> {
                statusLabel.setText("Delete at " + idx);
                colorCell(idx, "#e74c3c");
            }));

            for (int i = idx; i < dynSize - 1; i++) {
                final int from = i + 1, to = i;
                seq.getChildren().add(step(0.2, () -> {
                    dyn[to] = dyn[from];
                    drawDynamic();
                    colorCell(to, "#3498db");
                    colorCell(from, "#3498db");
                }));
            }

            seq.getChildren().add(step(0.2, () -> {
                dynSize--;
                drawDynamic();
                statusLabel.setText("Deleted!");
            }));
            seq.play();
        }
    }

    // ------------ Searching ------------
    @FXML
    void linearSearch() {
        if (!ensureCreated()) return;
        Integer target = parseInteger(searchField.getText());
        if (target == null) { flashStatus("Invalid search value!", true); return; }

        int n = dynamicMode ? dynSize : (fixedLast + 1);
        SequentialTransition seq = new SequentialTransition();

        for (int i = 0; i < n; i++) {
            final int idx = i;
            seq.getChildren().add(step(0.18, () -> {
                clearColors();
                colorCell(idx, "#f1c40f");
                statusLabel.setText("Checking index " + idx);
            }));

            boolean match = dynamicMode ? (dyn[idx] == target) : (fixed[idx] != null && fixed[idx] == target);
            if (match) {
                seq.getChildren().add(step(0.18, () -> {
                    clearColors();
                    colorCell(idx, "#2ecc71");
                    statusLabel.setText("FOUND at index " + idx);
                }));
                break;
            }
        }

        seq.play();
    }

    @FXML
    void binarySearch() {
        if (!ensureCreated()) return;
        Integer target = parseInteger(searchField.getText());
        if (target == null) { flashStatus("Invalid search value!", true); return; }

        // requirement: sorted + no nulls for fixed
        if (!dynamicMode) {
            if (fixedLast < 0) { flashStatus("Array empty!", true); return; }
            for (int i = 0; i <= fixedLast; i++) if (fixed[i] == null) { flashStatus("Fixed array has empty slots before lastUsed.", true); return; }
        } else {
            if (dynSize == 0) { flashStatus("Array empty!", true); return; }
        }

        int low = 0;
        int high = dynamicMode ? dynSize - 1 : fixedLast;

        SequentialTransition seq = new SequentialTransition();

        while (low <= high) {
            int mid = (low + high) / 2;
            final int fLow = low, fMid = mid, fHigh = high;

            seq.getChildren().add(step(0.25, () -> {
                clearColors();
                colorCell(fLow, "#9b59b6");
                colorCell(fHigh, "#9b59b6");
                colorCell(fMid, "#f1c40f");
                statusLabel.setText("low=" + fLow + " mid=" + fMid + " high=" + fHigh);
            }));

            int midVal = dynamicMode ? dyn[mid] : fixed[mid];
            if (midVal == target) {
                seq.getChildren().add(step(0.25, () -> {
                    clearColors();
                    colorCell(fMid, "#2ecc71");
                    statusLabel.setText("FOUND at index " + fMid);
                }));
                break;
            } else if (midVal < target) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        seq.play();
    }

    // ------------ Draw helpers ------------
    private void drawFixed() {
        arrayBox.getChildren().clear();
        if (fixed == null) return;

        for (int i = 0; i < fixed.length; i++) {
            String val = (fixed[i] == null) ? "" : String.valueOf(fixed[i]);
            arrayBox.getChildren().add(makeCell(i, val, fixed[i] == null));
        }
        sizeCapLabel.setText("size=" + (fixedLast + 1) + "  capacity=" + fixed.length);
    }

    private void drawDynamic() {
        arrayBox.getChildren().clear();
        if (dyn == null) return;

        for (int i = 0; i < dynCap; i++) {
            boolean empty = i >= dynSize;
            String val = empty ? "" : String.valueOf(dyn[i]);
            arrayBox.getChildren().add(makeCell(i, val, empty));
        }
        sizeCapLabel.setText("size=" + dynSize + "  capacity=" + dynCap);
    }

    private StackPane makeCell(int idx, String val, boolean empty) {
        Rectangle r = new Rectangle(52, 60);
        r.setArcWidth(10);
        r.setArcHeight(10);
        r.setStyle("-fx-fill:" + (empty ? "#2c2c54" : "#0f3460") + "; -fx-stroke: white; -fx-stroke-width: 1;");

        Text valueText = new Text(val);
        valueText.setStyle("-fx-fill: white; -fx-font-size: 16; -fx-font-weight: bold;");

        Text indexText = new Text(String.valueOf(idx));
        indexText.setStyle("-fx-fill: #bdbdbd; -fx-font-size: 10;");

        VBox v = new VBox(4, valueText, indexText);
        v.setStyle("-fx-alignment: center;");

        StackPane cell = new StackPane(r, v);
        cell.setUserData(r); // store rectangle for coloring
        return cell;
    }

    private void clearColors() {
        for (Node n : arrayBox.getChildren()) {
            Rectangle r = (Rectangle) n.getUserData();
            if (r != null) {
                r.setStyle(r.getStyle().replaceAll("-fx-fill:[^;]+;", "-fx-fill:#0f3460;"));
            }
        }
        // re-apply empties color
        if (!dynamicMode && fixed != null) {
            for (int i = 0; i < fixed.length; i++) if (fixed[i] == null) setFill(i, "#2c2c54");
        }
        if (dynamicMode && dyn != null) {
            for (int i = dynSize; i < dynCap; i++) setFill(i, "#2c2c54");
        }
    }

    private void colorCell(int idx, String hex) {
        setFill(idx, hex);
    }

    private void setFill(int idx, String hex) {
        if (idx < 0 || idx >= arrayBox.getChildren().size()) return;
        Node n = arrayBox.getChildren().get(idx);
        Rectangle r = (Rectangle) n.getUserData();
        if (r != null) {
            String s = r.getStyle();
            if (s.contains("-fx-fill:")) {
                s = s.replaceAll("-fx-fill:[^;]+;", "-fx-fill:" + hex + ";");
            } else {
                s += " -fx-fill:" + hex + ";";
            }
            r.setStyle(s);
        }
    }

    private void highlightOnce(int idx, String color, String msg) {
        if (!ensureCreated()) return;
        clearColors();
        colorCell(idx, color);
        statusLabel.setText(msg);
        PauseTransition p = new PauseTransition(Duration.seconds(0.35 / speed()));
        p.setOnFinished(e -> clearColors());
        p.play();
    }

    private PauseTransition step(double baseSeconds, Runnable r) {
        PauseTransition p = new PauseTransition(Duration.seconds(baseSeconds / speed()));
        p.setOnFinished(e -> r.run());
        return p;
    }

    private double speed() {
        return speedSlider == null ? 1.0 : Math.max(0.2, speedSlider.getValue());
    }

    // ------------ Validation ------------
    private boolean ensureCreated() {
        if (!dynamicMode && fixed == null) { flashStatus("Create a fixed array first!", true); return false; }
        if (dynamicMode && dyn == null) { flashStatus("Create a dynamic array first!", true); return false; }
        return true;
    }

    private boolean inRange(int idx) {
        if (!dynamicMode) {
            if (fixed == null) return false;
            if (idx < 0 || idx >= fixed.length) { flashStatus("Index out of range!", true); return false; }
            return true;
        } else {
            if (dyn == null) return false;
            if (idx < 0 || idx >= dynSize) { flashStatus("Index must be < size (" + dynSize + ")", true); return false; }
            return true;
        }
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private Integer parseInteger(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return null; }
    }

    private void flashStatus(String msg, boolean error) {
        statusLabel.setText(msg);
        statusLabel.setStyle(error ? "-fx-text-fill:#ff6b6b;" : "-fx-text-fill:#51c4d3;");
        PauseTransition p = new PauseTransition(Duration.seconds(0.5));
        p.setOnFinished(e -> statusLabel.setStyle("-fx-text-fill:#51c4d3;"));
        p.play();
    }
}
