package org.example.VisuAlgorithm;

import javafx.application.Platform;
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
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

    // Data (DOUBLE supported)
    private Double[] fixed;      // null means empty
    private int fixedLast = -1;  // last used index

    private double[] dyn;
    private int dynSize = 0;
    private int dynCap = 0;

    private boolean dynamicMode = false;
    private final Random rnd = new Random();

    private static final double EPS = 1e-9;

    private boolean runningAnimation = false;

    // ----- merge sort step recorder -----
    private static class WriteStep {
        final int index;
        final double value;
        final int l, r; // merged range for highlight
        WriteStep(int index, double value, int l, int r) {
            this.index = index;
            this.value = value;
            this.l = l;
            this.r = r;
        }
    }

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
        if (runningAnimation) return;

        int n = parseInt(sizeField.getText(), -1);
        if (n <= 0) { flashStatus("Invalid size!", true); return; }

        if (!dynamicMode) {
            fixed = new Double[n];
            fixedLast = -1;
            flashStatus("Fixed array created (size = " + n + ")", false);
            drawFixed();
        } else {
            dynCap = n;
            dynSize = 0;
            dyn = new double[dynCap];
            flashStatus("Dynamic array created (capacity = " + dynCap + ")", false);
            drawDynamic();
        }
    }

    @FXML
    void randomFill() {
        if (runningAnimation) return;
        if (!ensureCreated()) return;

        if (!dynamicMode) {
            for (int i = 0; i < fixed.length; i++) fixed[i] = rnd.nextInt(90)/1.0 + 10;
            fixedLast = fixed.length - 1;
            flashStatus("Fixed array random filled", false);
            drawFixed();
        } else {
            dynSize = dynCap;
            for (int i = 0; i < dynSize; i++) dyn[i] = rnd.nextInt(90) + 10;
            flashStatus("Dynamic array random filled (size = capacity)", false);
            drawDynamic();
        }
    }

    // ------------ Basic Ops ------------
    @FXML
    void getValue() {
        if (runningAnimation) return;
        if (!ensureCreated()) return;

        int idx = parseInt(indexField.getText(), -1);
        if (!inRangeRead(idx)) return;

        String v = dynamicMode ? formatNum(dyn[idx]) : formatNumSafe(fixed[idx]);
        highlightOnce(idx, "#f1c40f", "Get at index " + idx + " = " + v);
    }

    @FXML
    void setValue() {
        if (runningAnimation) return;
        if (!ensureCreated()) return;

        int idx = parseInt(indexField.getText(), -1);
        Double val = parseDoubleValue(valueField.getText());
        if (val == null) { flashStatus("Invalid value!", true); return; }

        if (!dynamicMode) {
            if (idx < 0 || idx >= fixed.length) { flashStatus("Index out of range!", true); return; }

            fixed[idx] = val;
            fixedLast = Math.max(fixedLast, idx);
            drawFixed();
            highlightOnce(idx, "#2ecc71", "Set index " + idx + " = " + formatNum(val));
        } else {
            if (idx < 0 || idx >= dynSize) { flashStatus("Index must be < size (" + dynSize + ")", true); return; }

            dyn[idx] = val;
            drawDynamic();
            highlightOnce(idx, "#2ecc71", "Set index " + idx + " = " + formatNum(val));
        }
    }

    @FXML
    void insertAt() {
        if (runningAnimation) return;
        if (!ensureCreated()) return;

        int idx = parseInt(indexField.getText(), -1);
        Double val = parseDoubleValue(valueField.getText());
        if (val == null) { flashStatus("Invalid value!", true); return; }

        if (!dynamicMode) {
            if (idx < 0 || idx >= fixed.length) { flashStatus("Index out of range!", true); return; }
            if (fixedLast == fixed.length - 1) { flashStatus("Fixed array is full!", true); return; }

            int insertPos = Math.min(idx, fixedLast + 1);

            SequentialTransition seq = new SequentialTransition();
            runningAnimation = true;

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
                statusLabel.setText("Inserted " + formatNum(val) + " at " + insertPos);
            }));

            seq.getChildren().add(step(0.10, () -> runningAnimation = false));
            seq.play();

        } else {
            if (idx < 0 || idx > dynSize) { flashStatus("Index must be 0..size", true); return; }

            SequentialTransition seq = new SequentialTransition();
            runningAnimation = true;

            if (dynSize == dynCap) {
                int newCap = Math.max(1, dynCap * 2);

                seq.getChildren().add(step(0.30, () -> {
                    statusLabel.setText("Resize: capacity " + dynCap + " -> " + newCap);
                    sizeCapLabel.setText("size=" + dynSize + "  capacity=" + newCap + " (copying...)");
                }));

                seq.getChildren().add(step(0.30, () -> {
                    double[] newArr = new double[newCap];
                    System.arraycopy(dyn, 0, newArr, 0, dynSize);
                    dyn = newArr;
                    dynCap = newCap;
                    drawDynamic();
                }));
            }

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

            seq.getChildren().add(step(0.22, () -> {
                dyn[idx] = val;
                dynSize++;
                drawDynamic();
                colorCell(idx, "#2ecc71");
                statusLabel.setText("Inserted " + formatNum(val) + " at " + idx);
            }));

            seq.getChildren().add(step(0.10, () -> runningAnimation = false));
            seq.play();
        }
    }

    @FXML
    void deleteAt() {
        if (runningAnimation) return;
        if (!ensureCreated()) return;

        int idx = parseInt(indexField.getText(), -1);

        if (!dynamicMode) {
            if (idx < 0 || idx > fixedLast) { flashStatus("Index must be 0..lastUsed (" + fixedLast + ")", true); return; }

            SequentialTransition seq = new SequentialTransition();
            runningAnimation = true;

            seq.getChildren().add(step(0.20, () -> {
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

            seq.getChildren().add(step(0.20, () -> {
                fixed[fixedLast] = null;
                fixedLast--;
                drawFixed();
                statusLabel.setText("Deleted!");
            }));

            seq.getChildren().add(step(0.10, () -> runningAnimation = false));
            seq.play();

        } else {
            if (idx < 0 || idx >= dynSize) { flashStatus("Index must be 0..size-1", true); return; }

            SequentialTransition seq = new SequentialTransition();
            runningAnimation = true;

            seq.getChildren().add(step(0.20, () -> {
                statusLabel.setText("Delete at " + idx);
                colorCell(idx, "#e74c3c");
            }));

            for (int i = idx; i < dynSize - 1; i++) {
                final int from = i + 1, to = i;
                seq.getChildren().add(step(0.20, () -> {
                    dyn[to] = dyn[from];
                    drawDynamic();
                    colorCell(to, "#3498db");
                    colorCell(from, "#3498db");
                }));
            }

            seq.getChildren().add(step(0.20, () -> {
                dynSize--;
                drawDynamic();
                statusLabel.setText("Deleted!");
            }));

            seq.getChildren().add(step(0.10, () -> runningAnimation = false));
            seq.play();
        }
    }

    // ------------ Searching ------------
    @FXML
    void linearSearch() {
        if (runningAnimation) return;
        if (!ensureCreated()) return;

        Double target = parseDoubleValue(searchField.getText());
        if (target == null) { flashStatus("Invalid search value!", true); return; }

        int n = dynamicMode ? dynSize : (fixedLast + 1);
        SequentialTransition seq = new SequentialTransition();
        final boolean[] found = {false};

        for (int i = 0; i < n; i++) {
            final int idx = i;

            seq.getChildren().add(step(0.18, () -> {
                clearColors();
                colorCell(idx, "#f1c40f");
                statusLabel.setText("Checking index " + idx);
            }));

            boolean match = dynamicMode
                    ? eq(dyn[idx], target)
                    : (fixed[idx] != null && eq(fixed[idx], target));

            if (match) {
                found[0] = true;
                seq.getChildren().add(step(0.18, () -> {
                    clearColors();
                    colorCell(idx, "#2ecc71");
                    statusLabel.setText("FOUND at index " + idx);
                }));
                break;
            }
        }

        seq.getChildren().add(step(0.18, () -> {
            if (!found[0]) {
                clearColors();
                statusLabel.setText("NOT FOUND ❌");
                showInfoPopup("Not Found", "Value " + formatNum(target) + " is not in the array.");
            }
        }));

        seq.play();
    }

    @FXML
    void binarySearch() {
        if (runningAnimation) return;
        if (!ensureCreated()) return;

        Double target = parseDoubleValue(searchField.getText());
        if (target == null) { flashStatus("Invalid search value!", true); return; }

        if (!isSortedNonDecreasing()) {
            showWarningPopup("Array Not Sorted",
                    "Binary Search requires a sorted array.\n\nClick Merge Sort first.");
            return;
        }

        int low = 0;
        int high = dynamicMode ? dynSize - 1 : fixedLast;

        SequentialTransition seq = new SequentialTransition();
        final boolean[] found = {false};

        while (low <= high) {
            int mid = low + (high - low) / 2;
            final int fLow = low, fMid = mid, fHigh = high;

            seq.getChildren().add(step(0.25, () -> {
                clearColors();
                colorCell(fLow, "#9b59b6");
                colorCell(fHigh, "#9b59b6");
                colorCell(fMid, "#f1c40f");
                statusLabel.setText("low=" + fLow + " mid=" + fMid + " high=" + fHigh);
            }));

            double midVal = dynamicMode ? dyn[mid] : fixed[mid];

            if (eq(midVal, target)) {
                found[0] = true;
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

        seq.getChildren().add(step(0.18, () -> {
            if (!found[0]) {
                clearColors();
                statusLabel.setText("NOT FOUND ❌");
                showInfoPopup("Not Found", "Value " + formatNum(target) + " is not in the array.");
            }
        }));

        seq.play();
    }

    @FXML
    void ternarySearch() {
        if (runningAnimation) return;
        if (!ensureCreated()) return;

        Double target = parseDoubleValue(searchField.getText());
        if (target == null) { flashStatus("Invalid search value!", true); return; }

        if (!isSortedNonDecreasing()) {
            showWarningPopup("Array Not Sorted",
                    "Ternary Search requires a sorted array.\n\nClick Merge Sort first.");
            return;
        }

        int low = 0;
        int high = dynamicMode ? dynSize - 1 : fixedLast;

        SequentialTransition seq = new SequentialTransition();
        final boolean[] found = {false};

        while (low <= high) {
            int third = (high - low) / 3;
            int mid1 = low + third;
            int mid2 = high - third;

            final int fLow = low, fHigh = high, fMid1 = mid1, fMid2 = mid2;

            seq.getChildren().add(step(0.28, () -> {
                clearColors();
                colorCell(fLow, "#9b59b6");
                colorCell(fHigh, "#9b59b6");
                colorCell(fMid1, "#f1c40f");
                colorCell(fMid2, "#f1c40f");
                statusLabel.setText("low=" + fLow + " mid1=" + fMid1 + " mid2=" + fMid2 + " high=" + fHigh);
            }));

            double v1 = dynamicMode ? dyn[mid1] : fixed[mid1];
            double v2 = dynamicMode ? dyn[mid2] : fixed[mid2];

            if (eq(v1, target)) {
                found[0] = true;
                seq.getChildren().add(step(0.28, () -> {
                    clearColors();
                    colorCell(fMid1, "#2ecc71");
                    statusLabel.setText("FOUND at index " + fMid1);
                }));
                break;
            }

            if (eq(v2, target)) {
                found[0] = true;
                seq.getChildren().add(step(0.28, () -> {
                    clearColors();
                    colorCell(fMid2, "#2ecc71");
                    statusLabel.setText("FOUND at index " + fMid2);
                }));
                break;
            }

            if (target < v1) {
                high = mid1 - 1;
            } else if (target > v2) {
                low = mid2 + 1;
            } else {
                low = mid1 + 1;
                high = mid2 - 1;
            }
        }

        seq.getChildren().add(step(0.18, () -> {
            if (!found[0]) {
                clearColors();
                statusLabel.setText("NOT FOUND ❌");
                showInfoPopup("Not Found", "Value " + formatNum(target) + " is not in the array.");
            }
        }));

        seq.play();
    }

    // ------------ Merge Sort (correct animation) ------------
    @FXML
    void mergeSort() {
        if (runningAnimation) return;
        if (!ensureCreated()) return;

        if (!dynamicMode) {
            if (fixedLast <= 0) { flashStatus("Need at least 2 elements!", true); return; }
            for (int i = 0; i <= fixedLast; i++) {
                if (fixed[i] == null) { flashStatus("Fill array first (no empty slots).", true); return; }
            }

            int n = fixedLast + 1;

            double[] sim = new double[n];
            for (int i = 0; i < n; i++) sim[i] = fixed[i];

            List<WriteStep> steps = new ArrayList<>();
            recordMergeSort(sim, 0, n - 1, steps);

            double[] display = new double[n];
            for (int i = 0; i < n; i++) display[i] = fixed[i];

            SequentialTransition seq = new SequentialTransition();
            runningAnimation = true;

            for (WriteStep s : steps) {
                seq.getChildren().add(step(0.18, () -> {
                    display[s.index] = s.value;
                    drawWorking(display, false);

                    clearColors();
                    colorRange(s.l, s.r, "#34495e");
                    colorCell(s.index, "#e67e22");
                    statusLabel.setText("Write " + formatNum(s.value) + " at index " + s.index);
                }));
            }

            seq.getChildren().add(step(0.20, () -> {
                for (int i = 0; i < n; i++) fixed[i] = sim[i];
                drawFixed();
                clearColors();
                statusLabel.setText("Sorting DONE ✅");
                runningAnimation = false;
            }));

            seq.play();

        } else {
            if (dynSize <= 1) { flashStatus("Need at least 2 elements!", true); return; }

            int n = dynSize;

            double[] sim = new double[n];
            System.arraycopy(dyn, 0, sim, 0, n);

            List<WriteStep> steps = new ArrayList<>();
            recordMergeSort(sim, 0, n - 1, steps);

            double[] display = new double[n];
            System.arraycopy(dyn, 0, display, 0, n);

            SequentialTransition seq = new SequentialTransition();
            runningAnimation = true;

            for (WriteStep s : steps) {
                seq.getChildren().add(step(0.18, () -> {
                    display[s.index] = s.value;
                    drawWorking(display, true);

                    clearColors();
                    colorRange(s.l, s.r, "#34495e");
                    colorCell(s.index, "#e67e22");
                    statusLabel.setText("Write " + formatNum(s.value) + " at index " + s.index);
                }));
            }

            seq.getChildren().add(step(0.20, () -> {
                System.arraycopy(sim, 0, dyn, 0, n);
                drawDynamic();
                clearColors();
                statusLabel.setText("Sorting DONE ✅");
                runningAnimation = false;
            }));

            seq.play();
        }
    }

    private void recordMergeSort(double[] a, int l, int r, List<WriteStep> steps) {
        if (l >= r) return;
        int m = l + (r - l) / 2;
        recordMergeSort(a, l, m, steps);
        recordMergeSort(a, m + 1, r, steps);
        recordMerge(a, l, m, r, steps);
    }

    private void recordMerge(double[] a, int l, int m, int r, List<WriteStep> steps) {
        int n1 = m - l + 1;
        int n2 = r - m;

        double[] L = new double[n1];
        double[] R = new double[n2];
        System.arraycopy(a, l, L, 0, n1);
        System.arraycopy(a, m + 1, R, 0, n2);

        int i = 0, j = 0, k = l;

        while (i < n1 && j < n2) {
            double val = (L[i] <= R[j]) ? L[i++] : R[j++];
            a[k] = val;
            steps.add(new WriteStep(k, val, l, r));
            k++;
        }
        while (i < n1) {
            double val = L[i++];
            a[k] = val;
            steps.add(new WriteStep(k, val, l, r));
            k++;
        }
        while (j < n2) {
            double val = R[j++];
            a[k] = val;
            steps.add(new WriteStep(k, val, l, r));
            k++;
        }
    }

    // ------------ Sorted check ------------
    private boolean isSortedNonDecreasing() {
        if (!ensureCreated()) return false;

        if (!dynamicMode) {
            if (fixedLast <= 0) return true;
            for (int i = 0; i <= fixedLast; i++) {
                if (fixed[i] == null) return false;
            }
            for (int i = 0; i < fixedLast; i++) {
                if (fixed[i] > fixed[i + 1]) return false;
            }
            return true;
        } else {
            if (dynSize <= 1) return true;
            for (int i = 0; i < dynSize - 1; i++) {
                if (dyn[i] > dyn[i + 1]) return false;
            }
            return true;
        }
    }

    // ------------ Draw helpers ------------
    private void drawFixed() {
        arrayBox.getChildren().clear();
        if (fixed == null) return;

        for (int i = 0; i < fixed.length; i++) {
            String val = (fixed[i] == null) ? "" : formatNum(fixed[i]);
            arrayBox.getChildren().add(makeCell(i, val, fixed[i] == null));
        }
        sizeCapLabel.setText("size=" + (fixedLast + 1) + "  capacity=" + fixed.length);
    }

    private void drawDynamic() {
        arrayBox.getChildren().clear();
        if (dyn == null) return;

        for (int i = 0; i < dynCap; i++) {
            boolean empty = i >= dynSize;
            String val = empty ? "" : formatNum(dyn[i]);
            arrayBox.getChildren().add(makeCell(i, val, empty));
        }
        sizeCapLabel.setText("size=" + dynSize + "  capacity=" + dynCap);
    }

    // draw the working (only used slots shown, rest empty)
    private void drawWorking(double[] a, boolean isDynamic) {
        arrayBox.getChildren().clear();

        if (!isDynamic) {
            int cap = fixed.length;
            int used = a.length;
            for (int i = 0; i < cap; i++) {
                boolean empty = i >= used;
                String val = empty ? "" : formatNum(a[i]);
                arrayBox.getChildren().add(makeCell(i, val, empty));
            }
            sizeCapLabel.setText("size=" + used + "  capacity=" + cap);
        } else {
            int cap = dynCap;
            int used = a.length;
            for (int i = 0; i < cap; i++) {
                boolean empty = i >= used;
                String val = empty ? "" : formatNum(a[i]);
                arrayBox.getChildren().add(makeCell(i, val, empty));
            }
            sizeCapLabel.setText("size=" + used + "  capacity=" + cap);
        }
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
        cell.setUserData(r);
        return cell;
    }

    private void clearColors() {
        for (Node n : arrayBox.getChildren()) {
            Rectangle r = (Rectangle) n.getUserData();
            if (r != null) r.setStyle(r.getStyle().replaceAll("-fx-fill:[^;]+;", "-fx-fill:#0f3460;"));
        }

        // re-apply empties color
        if (!dynamicMode && fixed != null) {
            for (int i = 0; i < fixed.length; i++) if (fixed[i] == null) setFill(i, "#2c2c54");
        }
        if (dynamicMode && dyn != null) {
            for (int i = dynSize; i < dynCap; i++) setFill(i, "#2c2c54");
        }
    }

    private void colorCell(int idx, String hex) { setFill(idx, hex); }

    private void colorRange(int l, int r, String hex) {
        for (int i = l; i <= r; i++) colorCell(i, hex);
    }

    private void setFill(int idx, String hex) {
        if (idx < 0 || idx >= arrayBox.getChildren().size()) return;
        Node n = arrayBox.getChildren().get(idx);
        Rectangle r = (Rectangle) n.getUserData();
        if (r == null) return;

        String s = r.getStyle();
        if (s.contains("-fx-fill:")) s = s.replaceAll("-fx-fill:[^;]+;", "-fx-fill:" + hex + ";");
        else s += " -fx-fill:" + hex + ";";
        r.setStyle(s);
    }

    private void highlightOnce(int idx, String color, String msg) {
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

    private boolean inRangeRead(int idx) {
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

    private Double parseDoubleValue(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return null; }
    }

    private boolean eq(double a, double b) { return Math.abs(a - b) < EPS; }

    // nicer formatting for doubles (avoid .0 always)
    private String formatNum(double x) {
        if (Math.abs(x - Math.rint(x)) < EPS) return String.valueOf((long) Math.rint(x));
        return String.format("%.3f", x).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String formatNumSafe(Double x) {
        if (x == null) return "";
        return formatNum(x);
    }

    private void flashStatus(String msg, boolean error) {
        statusLabel.setText(msg);
        statusLabel.setStyle(error ? "-fx-text-fill:#ff6b6b;" : "-fx-text-fill:#51c4d3;");
        PauseTransition p = new PauseTransition(Duration.seconds(0.5));
        p.setOnFinished(e -> statusLabel.setStyle("-fx-text-fill:#51c4d3;"));
        p.play();
    }

    // ------------ Popups ------------
    private void showWarningPopup(String title, String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(msg);
            alert.show(); // NOT showAndWait
        });
    }

    private void showInfoPopup(String title, String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(msg);
            alert.show(); // NOT showAndWait
        });
    }

}
