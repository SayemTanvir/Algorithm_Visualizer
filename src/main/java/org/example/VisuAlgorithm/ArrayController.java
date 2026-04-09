package org.example.VisuAlgorithm;

import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;
import javafx.scene.paint.Color;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;
import org.jcodec.api.awt.AWTSequenceEncoder;

import java.util.ArrayList;
import java.util.Random;

public class ArrayController {

    // UI
    @FXML private VBox capturePane; // Main center area for snapshots/video
    @FXML private HBox arrayBox;    // The actual array container
    @FXML private Label statusLabel;
    @FXML private Label sizeCapLabel;
    @FXML private Label stepLabel; // Detailed step explanation label
    @FXML private Label modeLabel;

    @FXML private RadioButton fixedBtn;
    @FXML private RadioButton dynamicBtn;

    @FXML private TextField sizeField;
    @FXML private TextField indexField;
    @FXML private TextField valueField;
    @FXML private TextField searchField;

    @FXML private Slider speedSlider;
    @FXML private ToggleGroup modeGroup;

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

        if (fixedBtn != null && dynamicBtn != null) {
            modeGroup = new ToggleGroup();
            fixedBtn.setToggleGroup(modeGroup);
            dynamicBtn.setToggleGroup(modeGroup);
            fixedBtn.setSelected(true);
        }

        switchMode();
    }

    // ------------ Navigation ------------
    @FXML
    void backHome() throws IOException {
        if (isRecording) stopRecording();
        Launcher.switchScene("hello-view.fxml");
    }

    // ------------ Mode ------------
    @FXML
    void switchMode() {
        if (dynamicBtn != null) dynamicMode = dynamicBtn.isSelected();
        if (modeLabel != null) modeLabel.setText(dynamicMode ? "Mode: Dynamic Array" : "Mode: Fixed Array");
        clearAll();
        if (statusLabel != null) statusLabel.setText("Create an array to start");
        if (sizeCapLabel != null) sizeCapLabel.setText("");
    }

    private void clearAll() {
        fixed = null;
        fixedLast = -1;
        dyn = null;
        dynSize = 0;
        dynCap = 0;
        if (arrayBox != null) arrayBox.getChildren().clear();
        if (stepLabel != null) stepLabel.setText("");
    }

    // ------------ Create / Random ------------
    @FXML
    void createArray() {
        if (runningAnimation) return;
        if (stepLabel != null) stepLabel.setText("");

        String inputText = sizeField.getText();
        int n = (inputText == null || inputText.trim().isEmpty()) ? 10 : parseInt(inputText, -1);
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
        if (stepLabel != null) stepLabel.setText("");

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
        if (stepLabel != null) stepLabel.setText("");

        int idx = parseInt(indexField.getText(), -1);
        if (!inRangeRead(idx)) return;

        String v = dynamicMode ? formatNum(dyn[idx]) : formatNumSafe(fixed[idx]);
        highlightOnce(idx, "#f1c40f", "Get at index " + idx + " = " + v);
    }

    @FXML
    void setValue() {
        if (runningAnimation) return;
        if (!ensureCreated()) return;
        if (stepLabel != null) stepLabel.setText("");

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
        if (stepLabel != null) stepLabel.setText("");

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
                    if (statusLabel != null) statusLabel.setText("Shift: " + from + " -> " + to);
                }));
            }

            seq.getChildren().add(step(0.25, () -> {
                fixed[insertPos] = val;
                fixedLast++;
                drawFixed();
                colorCell(insertPos, "#2ecc71");
                if (statusLabel != null) statusLabel.setText("Inserted " + formatNum(val) + " at " + insertPos);
            }));

            seq.getChildren().add(step(0.10, () -> runningAnimation = false));
            seq.play();

        } else {
            if (idx < 0 || idx > dynSize) { flashStatus("Index must be 0..size", true); return; }

            SequentialTransition seq = new SequentialTransition();
            runningAnimation = true;

            // Double capacity if size/capacity >= 0.5
            if (dynCap == 0 || (double) dynSize / dynCap >= 0.5) {
                int newCap = Math.max(1, dynCap * 2);

                seq.getChildren().add(step(0.30, () -> {
                    if (statusLabel != null) statusLabel.setText("Resize: capacity " + dynCap + " -> " + newCap);
                    if (sizeCapLabel != null) sizeCapLabel.setText("size=" + dynSize + "  capacity=" + newCap + " (copying...)");
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
                    if (statusLabel != null) statusLabel.setText("Shift: " + from + " -> " + to);
                }));
            }

            seq.getChildren().add(step(0.22, () -> {
                dyn[idx] = val;
                dynSize++;
                drawDynamic();
                colorCell(idx, "#2ecc71");
                if (statusLabel != null) statusLabel.setText("Inserted " + formatNum(val) + " at " + idx);
            }));

            seq.getChildren().add(step(0.10, () -> runningAnimation = false));
            seq.play();
        }
    }

    @FXML
    void deleteAt() {
        if (runningAnimation) return;
        if (!ensureCreated()) return;
        if (stepLabel != null) stepLabel.setText("");

        int idx = parseInt(indexField.getText(), -1);

        if (!dynamicMode) {
            if (idx < 0 || idx > fixedLast) { flashStatus("Index must be 0..lastUsed (" + fixedLast + ")", true); return; }

            SequentialTransition seq = new SequentialTransition();
            runningAnimation = true;

            seq.getChildren().add(step(0.20, () -> {
                if (statusLabel != null) statusLabel.setText("Delete at " + idx);
                colorCell(idx, "#e74c3c");
            }));

            for (int i = idx; i < fixedLast; i++) {
                final int from = i + 1, to = i;
                seq.getChildren().add(step(0.22, () -> {
                    fixed[to] = fixed[from];
                    drawFixed();
                    colorCell(to, "#3498db");
                    colorCell(from, "#3498db");
                    if (statusLabel != null) statusLabel.setText("Shift: " + from + " -> " + to);
                }));
            }

            seq.getChildren().add(step(0.20, () -> {
                fixed[fixedLast] = null;
                fixedLast--;
                drawFixed();
                if (statusLabel != null) statusLabel.setText("Deleted!");
            }));

            seq.getChildren().add(step(0.10, () -> runningAnimation = false));
            seq.play();

        } else {
            if (idx < 0 || idx >= dynSize) { flashStatus("Index must be 0..size-1", true); return; }

            SequentialTransition seq = new SequentialTransition();
            runningAnimation = true;

            seq.getChildren().add(step(0.20, () -> {
                if (statusLabel != null) statusLabel.setText("Delete at " + idx);
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
                if (statusLabel != null) statusLabel.setText("Deleted!");
            }));

            // Shrink capacity by half if size/capacity <= 0.25
            int futureSize = dynSize - 1; // Calculating size after the deletion completes
            if (dynCap > 2 && (double) futureSize / dynCap <= 0.25) {
                int newCap = Math.max(1, dynCap / 2);

                seq.getChildren().add(step(0.30, () -> {
                    if (statusLabel != null) statusLabel.setText("Shrink: capacity " + dynCap + " -> " + newCap);
                    if (sizeCapLabel != null) sizeCapLabel.setText("size=" + futureSize + "  capacity=" + newCap + " (copying...)");
                }));

                seq.getChildren().add(step(0.30, () -> {
                    double[] newArr = new double[newCap];
                    System.arraycopy(dyn, 0, newArr, 0, futureSize);
                    dyn = newArr;
                    dynCap = newCap;
                    drawDynamic();
                }));
            }

            seq.getChildren().add(step(0.10, () -> runningAnimation = false));
            seq.play();
        }
    }

    // ------------ Searching ------------
    @FXML
    void linearSearch() {
        if (runningAnimation) return;
        if (!ensureCreated()) return;
        if (stepLabel != null) stepLabel.setText("");

        Double target = parseDoubleValue(searchField.getText());
        if (target == null) { flashStatus("Invalid search value!", true); return; }

        int n = dynamicMode ? dynSize : (fixedLast + 1);
        SequentialTransition seq = new SequentialTransition();
        final boolean[] found = {false};

        for (int i = 0; i < n; i++) {
            final int idx = i;

            seq.getChildren().add(step(0.25, () -> {
                clearColors();
                colorCell(idx, "#f1c40f");
                if (statusLabel != null) statusLabel.setText("Checking index " + idx);

                double val = dynamicMode ? dyn[idx] : (fixed[idx] != null ? fixed[idx] : 0);
                boolean isNull = (!dynamicMode && fixed[idx] == null);

                if (stepLabel != null) {
                    if (isNull) {
                        stepLabel.setText("Checking index " + idx + ": Cell is empty.");
                    } else {
                        stepLabel.setText("Checking index " + idx + ". Value is " + formatNum(val) + ".");
                    }
                }
            }));

            boolean match = dynamicMode
                    ? eq(dyn[idx], target)
                    : (fixed[idx] != null && eq(fixed[idx], target));

            if (match) {
                found[0] = true;
                seq.getChildren().add(step(0.25, () -> {
                    clearColors();
                    colorCell(idx, "#2ecc71");
                    if (statusLabel != null) statusLabel.setText("FOUND at index " + idx);
                    if (stepLabel != null) stepLabel.setText("Match found! Target " + formatNum(target) + " is at index " + idx + ".");
                }));
                break;
            }
        }

        seq.getChildren().add(step(0.18, () -> {
            if (!found[0]) {
                clearColors();
                if (statusLabel != null) statusLabel.setText("NOT FOUND ❌");
                if (stepLabel != null) stepLabel.setText("Reached the end of the array. Value is not present.");
                showInfoPopup("Not Found", "Value " + formatNum(target) + " is not in the array.");
            }
        }));

        seq.play();
    }

    @FXML
    void binarySearch() {
        if (runningAnimation) return;
        if (!ensureCreated()) return;
        if (stepLabel != null) stepLabel.setText("");

        Double target = parseDoubleValue(searchField.getText());
        if (target == null) { flashStatus("Invalid search value!", true); return; }

        if (!isSortedNonDecreasing()) {
            showWarningPopup("Array Not Sorted",
                    "Binary Search requires a sorted array.\n\nClick Sort first.");
            return;
        }

        int low = 0;
        int high = dynamicMode ? dynSize - 1 : fixedLast;

        SequentialTransition seq = new SequentialTransition();
        final boolean[] found = {false};

        while (low <= high) {
            int mid = low + (high - low) / 2;
            final int fLow = low, fMid = mid, fHigh = high;
            double midVal = dynamicMode ? dyn[mid] : fixed[mid];

            seq.getChildren().add(step(0.40, () -> {
                clearColors();
                colorCell(fLow, "#9b59b6");
                colorCell(fHigh, "#9b59b6");
                colorCell(fMid, "#f1c40f");
                if (statusLabel != null) statusLabel.setText("low=" + fLow + " mid=" + fMid + " high=" + fHigh);
                if (stepLabel != null) stepLabel.setText("Search space: [" + fLow + " .. " + fHigh + "]. Mid is " + fMid + ".\nComparing mid value " + formatNum(midVal) + " with target " + formatNum(target) + ".");
            }));

            if (eq(midVal, target)) {
                found[0] = true;
                seq.getChildren().add(step(0.30, () -> {
                    clearColors();
                    colorCell(fMid, "#2ecc71");
                    if (statusLabel != null) statusLabel.setText("FOUND at index " + fMid);
                    if (stepLabel != null) stepLabel.setText("Match found! " + formatNum(midVal) + " == " + formatNum(target) + ".");
                }));
                break;
            } else if (midVal < target) {
                low = mid + 1;
                final int newLow = low;
                seq.getChildren().add(step(0.30, () -> {
                    if (stepLabel != null) stepLabel.setText(formatNum(midVal) + " < " + formatNum(target) + ".\nTarget must be in the right half. Updating low = " + newLow + ".");
                }));
            } else {
                high = mid - 1;
                final int newHigh = high;
                seq.getChildren().add(step(0.30, () -> {
                    if (stepLabel != null) stepLabel.setText(formatNum(midVal) + " > " + formatNum(target) + ".\nTarget must be in the left half. Updating high = " + newHigh + ".");
                }));
            }
        }

        seq.getChildren().add(step(0.20, () -> {
            if (!found[0]) {
                clearColors();
                if (statusLabel != null) statusLabel.setText("NOT FOUND ❌");
                if (stepLabel != null) stepLabel.setText("Search space exhausted (low > high). Value is not in the array.");
                showInfoPopup("Not Found", "Value " + formatNum(target) + " is not in the array.");
            }
        }));

        seq.play();
    }

    @FXML
    void ternarySearch() {
        if (runningAnimation) return;
        if (!ensureCreated()) return;
        if (stepLabel != null) stepLabel.setText("");

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
            double v1 = dynamicMode ? dyn[mid1] : fixed[mid1];
            double v2 = dynamicMode ? dyn[mid2] : fixed[mid2];

            seq.getChildren().add(step(0.45, () -> {
                clearColors();
                colorCell(fLow, "#9b59b6");
                colorCell(fHigh, "#9b59b6");
                colorCell(fMid1, "#f1c40f");
                colorCell(fMid2, "#f1c40f");
                if (statusLabel != null) statusLabel.setText("low=" + fLow + " mid1=" + fMid1 + " mid2=" + fMid2 + " high=" + fHigh);
                if (stepLabel != null) stepLabel.setText("Search space: [" + fLow + " .. " + fHigh + "]. Checking mid1 (" + fMid1 + ") = " + formatNum(v1) + " and mid2 (" + fMid2 + ") = " + formatNum(v2) + ".");
            }));

            if (eq(v1, target)) {
                found[0] = true;
                seq.getChildren().add(step(0.30, () -> {
                    clearColors();
                    colorCell(fMid1, "#2ecc71");
                    if (statusLabel != null) statusLabel.setText("FOUND at index " + fMid1);
                    if (stepLabel != null) stepLabel.setText("Target " + formatNum(target) + " found at mid1!");
                }));
                break;
            }

            if (eq(v2, target)) {
                found[0] = true;
                seq.getChildren().add(step(0.30, () -> {
                    clearColors();
                    colorCell(fMid2, "#2ecc71");
                    if (statusLabel != null) statusLabel.setText("FOUND at index " + fMid2);
                    if (stepLabel != null) stepLabel.setText("Target " + formatNum(target) + " found at mid2!");
                }));
                break;
            }

            if (target < v1) {
                high = mid1 - 1;
                final int newHigh = high;
                seq.getChildren().add(step(0.35, () -> {
                    if (stepLabel != null) stepLabel.setText(formatNum(target) + " < " + formatNum(v1) + " (mid1).\nTarget is in the first third. Updating high = " + newHigh + ".");
                }));
            } else if (target > v2) {
                low = mid2 + 1;
                final int newLow = low;
                seq.getChildren().add(step(0.35, () -> {
                    if (stepLabel != null) stepLabel.setText(formatNum(target) + " > " + formatNum(v2) + " (mid2).\nTarget is in the last third. Updating low = " + newLow + ".");
                }));
            } else {
                low = mid1 + 1;
                high = mid2 - 1;
                final int newLow = low, newHigh = high;
                seq.getChildren().add(step(0.35, () -> {
                    if (stepLabel != null) stepLabel.setText("Target is between mid1 and mid2.\nDiscarding outer thirds. Updating low = " + newLow + ", high = " + newHigh + ".");
                }));
            }
        }

        seq.getChildren().add(step(0.20, () -> {
            if (!found[0]) {
                clearColors();
                if (statusLabel != null) statusLabel.setText("NOT FOUND ❌");
                if (stepLabel != null) stepLabel.setText("Search space exhausted (low > high). Value is not in the array.");
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
        if (stepLabel != null) stepLabel.setText("");

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
                    if (statusLabel != null) statusLabel.setText("Write " + formatNum(s.value) + " at index " + s.index);
                }));
            }

            seq.getChildren().add(step(0.20, () -> {
                for (int i = 0; i < n; i++) fixed[i] = sim[i];
                drawFixed();
                clearColors();
                if (statusLabel != null) statusLabel.setText("Sorting DONE ✅");
                if (stepLabel != null) stepLabel.setText("Merge sort completed successfully.");
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
                    if (statusLabel != null) statusLabel.setText("Write " + formatNum(s.value) + " at index " + s.index);
                    if (stepLabel != null) stepLabel.setText("Merging: placing " + formatNum(s.value) + " at sorted position " + s.index + ".");
                }));
            }

            seq.getChildren().add(step(0.20, () -> {
                System.arraycopy(sim, 0, dyn, 0, n);
                drawDynamic();
                clearColors();
                if (statusLabel != null) statusLabel.setText("Sorting DONE ✅");
                if (stepLabel != null) stepLabel.setText("Merge sort completed successfully.");
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
        if (arrayBox != null) arrayBox.getChildren().clear();
        if (fixed == null) return;

        for (int i = 0; i < fixed.length; i++) {
            String val = (fixed[i] == null) ? "" : formatNum(fixed[i]);
            if (arrayBox != null) arrayBox.getChildren().add(makeCell(i, val, fixed[i] == null));
        }
        if (sizeCapLabel != null) sizeCapLabel.setText("size=" + (fixedLast + 1) + "  capacity=" + fixed.length);
    }

    private void drawDynamic() {
        if (arrayBox != null) arrayBox.getChildren().clear();
        if (dyn == null) return;

        for (int i = 0; i < dynCap; i++) {
            boolean empty = i >= dynSize;
            String val = empty ? "" : formatNum(dyn[i]);
            if (arrayBox != null) arrayBox.getChildren().add(makeCell(i, val, empty));
        }
        if (sizeCapLabel != null) sizeCapLabel.setText("size=" + dynSize + "  capacity=" + dynCap);
    }

    // draw the working (only used slots shown, rest empty)
    private void drawWorking(double[] a, boolean isDynamic) {
        if (arrayBox != null) arrayBox.getChildren().clear();

        if (!isDynamic) {
            int cap = fixed.length;
            int used = a.length;
            for (int i = 0; i < cap; i++) {
                boolean empty = i >= used;
                String val = empty ? "" : formatNum(a[i]);
                if (arrayBox != null) arrayBox.getChildren().add(makeCell(i, val, empty));
            }
            if (sizeCapLabel != null) sizeCapLabel.setText("size=" + used + "  capacity=" + cap);
        } else {
            int cap = dynCap;
            int used = a.length;
            for (int i = 0; i < cap; i++) {
                boolean empty = i >= used;
                String val = empty ? "" : formatNum(a[i]);
                if (arrayBox != null) arrayBox.getChildren().add(makeCell(i, val, empty));
            }
            if (sizeCapLabel != null) sizeCapLabel.setText("size=" + used + "  capacity=" + cap);
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
        if (arrayBox == null) return;
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
        if (arrayBox == null || idx < 0 || idx >= arrayBox.getChildren().size()) return;
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
        if (statusLabel != null) statusLabel.setText(msg);
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
        if (statusLabel != null) {
            statusLabel.setText(msg);
            statusLabel.setStyle(error ? "-fx-text-fill:#d32f2f;" : "-fx-text-fill:#0056b3;");
            PauseTransition p = new PauseTransition(Duration.seconds(0.5));
            p.setOnFinished(e -> statusLabel.setStyle("-fx-text-fill:#0056b3;"));
            p.play();
        }
    }

    // ------------ Popups ------------
    private void showWarningPopup(String title, String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);

            if (arrayBox != null && arrayBox.getScene() != null) {
                alert.initOwner(arrayBox.getScene().getWindow());
            }

            DialogPane dialogPane = alert.getDialogPane();
            try {
                dialogPane.getStylesheets().add(
                        getClass().getResource("/org/example/VisuAlgorithm/styles/main.css").toExternalForm()
                );
                dialogPane.getStyleClass().add("custom-alert");
            } catch (Exception e) {
                System.out.println("Could not load CSS for Alert: " + e.getMessage());
            }

            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(msg);
            alert.show();
        });
    }

    private void showInfoPopup(String title, String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);

            if (arrayBox != null && arrayBox.getScene() != null) {
                alert.initOwner(arrayBox.getScene().getWindow());
            }

            DialogPane dialogPane = alert.getDialogPane();
            try {
                dialogPane.getStylesheets().add(
                        getClass().getResource("/org/example/VisuAlgorithm/styles/main.css").toExternalForm()
                );
                dialogPane.getStyleClass().add("custom-alert");
            } catch (Exception e) {
                System.out.println("Could not load CSS for Alert: " + e.getMessage());
            }

            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(msg);
            alert.show();
        });
    }

    // ==========================================================================
    // CAPTURE & RECORDING LOGIC (LOCKED RESOLUTION & ANTI-FLOOD)
    // ==========================================================================
    @FXML
    void takeScreenshot() {
        if (arrayBox == null) return;
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.web("#0f172a")); // Dark theme background

        WritableImage snapshot = arrayBox.snapshot(params, null);
        BufferedImage buffered = SwingFXUtils.fromFXImage(snapshot, null);

        String downloadsDir = getDownloadsPath();
        String timestamp    = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File   outputFile   = new File(downloadsDir, "array_" + timestamp + ".png");

        try {
            ImageIO.write(buffered, "png", outputFile);
            System.out.println("Screenshot saved: " + outputFile.getAbsolutePath());
            if (statusLabel != null) statusLabel.setText("Screenshot saved! ✓");
            buffered.flush();
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
        if (arrayBox == null) return;
        isRecording = true;
        isCapturing = false;

        if (recordBtn != null) {
            recordBtn.setText("⏹");
            recordBtn.setStyle(
                    "-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-size: 14px;" +
                            "-fx-background-radius: 6; -fx-cursor: hand; -fx-border-color: #991b1b; -fx-border-radius: 6;"
            );
        }

        // LOCK RESOLUTION based on the starting size of the arrayBox
        SnapshotParameters initParams = new SnapshotParameters();
        initParams.setFill(Color.web("#0f172a"));
        WritableImage initSnap = arrayBox.snapshot(initParams, null);

        int rawW = (int) initSnap.getWidth();
        int rawH = (int) initSnap.getHeight();
        if (rawW < 2) rawW = 2; // safety bound
        if (rawH < 2) rawH = 2; // safety bound

        final int lockedW = (rawW % 2 == 0) ? rawW : rawW + 1;
        final int lockedH = (rawH % 2 == 0) ? rawH : rawH + 1;

        // 1. Initialize the video file and encoder IMMEDIATELY
        try {
            String downloadsDir = getDownloadsPath();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File outputFile = new File(downloadsDir, "array_rec_" + timestamp + ".mp4");

            encoder = AWTSequenceEncoder.createSequenceEncoder(outputFile, RECORD_FPS);
            System.out.println("Recording started... Streaming to: " + outputFile.getAbsolutePath() + " at " + lockedW + "x" + lockedH);
        } catch (IOException e) {
            System.err.println("Failed to start video encoder: " + e.getMessage());
            isRecording = false;
            return;
        }

        frameQueue = new ArrayBlockingQueue<>(30);

        // 2. Start the background capture loop
        recordingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "screen-recorder");
            t.setDaemon(true);
            return t;
        });

        // Encoder Thread (Consumer)
        Thread encoderThread = new Thread(() -> {
            try {
                while (isRecording || !frameQueue.isEmpty()) {
                    BufferedImage frame = frameQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (frame != null) {

                        BufferedImage bgrFrame = new BufferedImage(lockedW, lockedH, BufferedImage.TYPE_3BYTE_BGR);
                        Graphics2D g = bgrFrame.createGraphics();

                        g.setColor(new java.awt.Color(15, 23, 42)); // #0f172a
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
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        encoderThread.setDaemon(true);
        encoderThread.start();

        // Capture Loop (Producer on UI Thread)
        recordingExecutor.scheduleAtFixedRate(() -> {
            if (!isRecording || isCapturing) return;
            isCapturing = true;

            Platform.runLater(() -> {
                try {
                    SnapshotParameters params = new SnapshotParameters();
                    params.setFill(Color.web("#0f172a"));

                    WritableImage fxFrame = arrayBox.snapshot(params, null);
                    BufferedImage buffered = SwingFXUtils.fromFXImage(fxFrame, null);

                    if (!frameQueue.offer(buffered)) {
                        buffered.flush(); // Queue full, drop frame to save RAM
                    }
                } catch (Exception e) {
                    System.err.println("Capture error: " + e.getMessage());
                } finally {
                    isCapturing = false; // Allow next frame to be captured
                }
            });
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
            if (recordBtn != null) {
                recordBtn.setText("🎥 Record");
                recordBtn.setStyle("-fx-background-color: rgba(255,255,255,0.15); -fx-text-fill: white; -fx-font-size: 14px; -fx-background-radius: 6; -fx-cursor: hand; -fx-border-color: rgba(255,255,255,0.25); -fx-border-radius: 6;");
            }
        });
    }

    private String getDownloadsPath() {
        String home = System.getProperty("user.home");
        Path   dl   = Paths.get(home, "Downloads");
        if (!dl.toFile().exists()) dl.toFile().mkdirs();
        return dl.toString();
    }
}