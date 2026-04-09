package org.example.VisuAlgorithm;

import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.SnapshotParameters;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;
import org.jcodec.api.awt.AWTSequenceEncoder;

import javafx.animation.KeyValue;
import javafx.animation.Interpolator;

public class SortingController {

    @FXML private Pane displayPane;
    @FXML private Slider sizeSlider;
    @FXML private Slider speedSlider;
    @FXML private Label sizeLabel;
    @FXML private TextField customInput;

    @FXML private Button bubbleSortBtn;
    @FXML private Button selectionSortBtn;
    @FXML private Button insertionSortBtn;
    @FXML private Button quickSortBtn;
    @FXML private Button mergeSortBtn;

    @FXML private Button playPauseBtn;
    @FXML private Button stepBackBtn;
    @FXML private Button skipBtn;

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

    // Sort order radio buttons
    @FXML private RadioButton ascendingRadio;
    @FXML private RadioButton descendingRadio;

    // List to keep track of all sorting buttons for easy UI updates
    private List<Button> allSortButtons = new ArrayList<>();

    // --- Live Status UI ---
    @FXML private Label algoNameLabel;
    @FXML private Label currentStepLabel;
    @FXML private TextArea stepDescriptionArea;

    // Main Data -> changes as the animation goes
    private int[] array;
    private Rectangle[] bars;

    // QuickSort specific visual pointer
    private Polygon iPointerArrow;
    private int virtualArrowIdx = -1;
    private boolean virtualArrowShow = false;

    // UI State tracking
    private Button currentActiveSortBtn = null;

    // Animation helpers
    private interface SortStep {
        void forward();
        void backward();
    }

    private List<SortStep> stepQueue = new ArrayList<>();
    private List<String> stepMessages = new ArrayList<>();
    private int currentStepIndex = 0;
    private Timeline playTimeline;
    private boolean isPlaying = false;

    private int[] tempArray;
    private Color[] virtualColors;

    // -------------------------------------------------------
    // Helper: returns true if Ascending is selected
    // -------------------------------------------------------
    private boolean isAscending() {
        return ascendingRadio == null || ascendingRadio.isSelected();
    }

    @FXML
    public void initialize() {
        if (bubbleSortBtn != null) allSortButtons.add(bubbleSortBtn);
        if (selectionSortBtn != null) allSortButtons.add(selectionSortBtn);
        if (insertionSortBtn != null) allSortButtons.add(insertionSortBtn);
        if (quickSortBtn != null) allSortButtons.add(quickSortBtn);
        if (mergeSortBtn != null) allSortButtons.add(mergeSortBtn);

        screenshotBtn.setText("📷 Snapshot");
        recordBtn.setText("🎥 Record");

        screenshotBtn.setPrefWidth(130);
        screenshotBtn.setMinWidth(130);

        recordBtn.setPrefWidth(130);
        recordBtn.setMinWidth(130);

        sizeSlider.setMax(25);
        sizeSlider.setValue(10);
        updateSizeLabel(10);

        if (speedSlider != null) {
            speedSlider.setMin(0.5);
            speedSlider.setMax(10.0);
            speedSlider.setValue(2.0);
        }

        Platform.runLater(this::generateRandomArray);

        sizeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int oldSize = oldVal.intValue();
            int newSize = newVal.intValue();
            if (oldSize != newSize) {
                updateSizeLabel(newSize);
                generateRandomArray();
            }
        });

        displayPane.widthProperty().addListener((obs, o, n) -> drawArray());
        displayPane.heightProperty().addListener((obs, o, n) -> drawArray());

        setMediaControlsDisable(true);
        setupTimeline();
    }

    private void updateSizeLabel(int size) {
        if (sizeLabel != null) sizeLabel.setText("Size: " + size);
    }

    private void setupTimeline() {
        playTimeline = new Timeline(new KeyFrame(Duration.millis(700), e -> executeNextStep()));
        playTimeline.setCycleCount(Timeline.INDEFINITE);
        if (speedSlider != null) {
            playTimeline.rateProperty().bind(speedSlider.valueProperty());
        }
    }

    private void setControlsDisable(boolean disable) {
        sizeSlider.setDisable(disable);
        customInput.setDisable(disable);
        if (ascendingRadio != null) ascendingRadio.setDisable(disable);
        if (descendingRadio != null) descendingRadio.setDisable(disable);
    }

    private void setMediaControlsDisable(boolean disable) {
        if (playPauseBtn != null) playPauseBtn.setDisable(disable);
        if (stepBackBtn != null) stepBackBtn.setDisable(disable);
        if (skipBtn != null) skipBtn.setDisable(disable);
    }

    @FXML
    void togglePlayPause() {
        if (stepQueue.isEmpty() || currentStepIndex >= stepQueue.size()) return;

        if (isPlaying) {
            playTimeline.pause();
            isPlaying = false;
            playPauseBtn.setText("▶");
            updateStatusLabel(currentStepIndex > 0 ? currentStepIndex - 1 : 0);
        } else {
            playTimeline.play();
            isPlaying = true;
            playPauseBtn.setText("⏸");
        }
    }

    @FXML
    void stepForward() {
        if (isPlaying) togglePlayPause();
        executeNextStep();
    }

    @FXML
    void stepBackward() {
        if (isPlaying) togglePlayPause();

        if (currentStepIndex > 0) {
            currentStepIndex--;
            stepQueue.get(currentStepIndex).backward();
            updateStatusLabel(currentStepIndex - 1);

            if (currentStepIndex < stepQueue.size()) {
                setControlsDisable(true);
            }
        }
    }

    private void executeNextStep() {
        if (currentStepIndex < stepQueue.size()) {
            updateStatusLabel(currentStepIndex);
            stepQueue.get(currentStepIndex).forward();
            currentStepIndex++;
        } else {
            playTimeline.stop();
            isPlaying = false;
            playPauseBtn.setText("▶");
            setControlsDisable(false);

            for (Button btn : allSortButtons) {
                btn.setDisable(false);
            }
            if (currentActiveSortBtn != null) {
                currentActiveSortBtn.setStyle("");
                currentActiveSortBtn = null;
            }

            if (algoNameLabel != null) algoNameLabel.setText("Status: Finished");
            if (currentStepLabel != null) currentStepLabel.setText("Action: Sorted!");
            if (stepDescriptionArea != null) stepDescriptionArea.setText("The array is fully sorted.");
        }
    }

    private void updateStatusLabel(int index) {
        if (index >= 0 && index < stepMessages.size()) {
            String msg = stepMessages.get(index);
            if (stepDescriptionArea != null && msg != null) {
                stepDescriptionArea.setText(msg);
                if (currentStepLabel != null) currentStepLabel.setText("Action: Executing Step " + (index + 1));
            }
        } else if (index < 0) {
            if (stepDescriptionArea != null) stepDescriptionArea.setText("Ready to sort.");
            if (currentStepLabel != null) currentStepLabel.setText("Action: Idle");
        }
    }

    // --- DATA CREATION ---

    @FXML
    void generateRandomArray() {
        stopAll();
        int size = (int) sizeSlider.getValue();
        if (size <= 0) size = 10;

        array = new int[size];
        Random rand = new Random();
        for (int i = 0; i < size; i++) array[i] = rand.nextInt(90) + 10;

        drawArray();
    }

    @FXML
    void loadCustomInput() {
        stopAll();
        String input = customInput.getText();
        if (input == null || input.trim().isEmpty()) return;

        try {
            String[] parts = input.split(",");
            array = new int[parts.length];
            for (int i = 0; i < parts.length; i++) array[i] = Integer.parseInt(parts[i].trim());

            sizeSlider.setValue(array.length);
            drawArray();
        } catch (NumberFormatException e) {
            System.out.println("Invalid input");
        }
    }

    private void stopAll() {
        if (playTimeline != null) playTimeline.stop();
        isPlaying = false;
        if (playPauseBtn != null) playPauseBtn.setText("▶");
        stepQueue.clear();
        stepMessages.clear();
        currentStepIndex = 0;

        setControlsDisable(false);
        setMediaControlsDisable(true);

        for (Button btn : allSortButtons) {
            btn.setDisable(false);
            btn.setStyle("");
        }
        currentActiveSortBtn = null;

        if (algoNameLabel != null) algoNameLabel.setText("Algorithm: None");
        if (currentStepLabel != null) currentStepLabel.setText("Action: -");
        if (stepDescriptionArea != null) stepDescriptionArea.setText("Generate an array and select a sort.");

        if (iPointerArrow != null) {
            iPointerArrow.setVisible(false);
        }
    }

    private void drawArray() {
        if (array == null) return;

        displayPane.getChildren().clear();
        int size = array.length;
        bars = new Rectangle[size];

        double paneW = displayPane.getWidth();
        double paneH = displayPane.getHeight();

        if (paneW <= 0) paneW = 600;
        if (paneH <= 0) paneH = 400;

        double barWidth = (paneW / size) - 2;
        if (barWidth < 1) barWidth = 1;

        int maxVal = -1;
        for (int val : array) if (val > maxVal) maxVal = val;

        for (int i = 0; i < size; i++) {
            Rectangle bar = new Rectangle();
            double normalizedHeight = ((double) array[i] / maxVal) * (paneH * 0.85);

            bar.setX(i * (paneW / size));
            bar.setY(paneH - normalizedHeight);
            bar.setWidth(barWidth);
            bar.setHeight(normalizedHeight);
            bar.setFill(Color.CYAN);

            bars[i] = bar;
            displayPane.getChildren().add(bar);
        }

        if (iPointerArrow == null) {
            iPointerArrow = new Polygon(0, 0, 24, 0, 12, 18);
            iPointerArrow.setFill(Color.ORANGE);
            iPointerArrow.setStroke(Color.BLACK);
            iPointerArrow.setStrokeWidth(2);
        }
        displayPane.getChildren().add(iPointerArrow);

        if (virtualArrowShow) {
            updateArrow(virtualArrowIdx, true);
        } else {
            iPointerArrow.setVisible(false);
        }
    }

    private boolean prepareSort(String algoName, ActionEvent event) {
        if (array == null || array.length == 0) return false;

        if (playTimeline != null) playTimeline.stop();
        isPlaying = false;
        if (playPauseBtn != null) playPauseBtn.setText("▶");

        String direction = isAscending() ? "Ascending" : "Descending";
        if (algoNameLabel != null) algoNameLabel.setText("Algorithm: " + algoName + " (" + direction + ")");
        if (currentStepLabel != null) currentStepLabel.setText("Action: Starting...");
        if (stepDescriptionArea != null) stepDescriptionArea.setText("Ready to sort.");

        for (Button btn : allSortButtons) {
            btn.setDisable(true);
            btn.setStyle("");
        }

        if (event != null && event.getSource() instanceof Button) {
            currentActiveSortBtn = (Button) event.getSource();
            currentActiveSortBtn.setStyle("-fx-border-color: #FFA500; -fx-border-width: 2px; -fx-border-radius: 3px;");
        }

        setControlsDisable(true);
        setMediaControlsDisable(false);
        stepQueue.clear();
        stepMessages.clear();
        currentStepIndex = 0;

        virtualArrowIdx = -1;
        virtualArrowShow = false;
        if (iPointerArrow != null) iPointerArrow.setVisible(false);

        tempArray = array.clone();
        virtualColors = new Color[array.length];
        for (int i = 0; i < array.length; i++) virtualColors[i] = Color.CYAN;

        return true;
    }

    // --- QuickSort specific step for Arrow pointer ---
    private void addArrowStep(int targetIdx, boolean show, String msg) {
        int prevIdx = virtualArrowIdx;
        boolean prevShow = virtualArrowShow;

        virtualArrowIdx = targetIdx;
        virtualArrowShow = show;

        int newIdx = targetIdx;
        boolean newShow = show;

        stepQueue.add(new SortStep() {
            @Override public void forward() { updateArrow(newIdx, newShow); }
            @Override public void backward() { updateArrow(prevIdx, prevShow); }
        });

        if (msg == null || msg.isEmpty()) msg = "Updating target pointer position.";
        stepMessages.add(msg);
    }

    private void updateArrow(int idx, boolean show) {
        if (iPointerArrow == null) return;

        if (show && idx >= 0 && idx < array.length) {
            double paneW = displayPane.getWidth() > 0 ? displayPane.getWidth() : 600;
            double slotWidth = paneW / array.length;
            double barX = idx * slotWidth + (slotWidth / 2.0);

            iPointerArrow.setLayoutX(barX - 12);

            double barY = bars[idx].getY();
            iPointerArrow.setLayoutY(barY - 22);

            iPointerArrow.setVisible(true);
            iPointerArrow.toFront();
        } else {
            iPointerArrow.setVisible(false);
        }
    }

    // swap
    private void addSwapStep(int idx1, int idx2, String msg) {
        int temp = tempArray[idx1];
        tempArray[idx1] = tempArray[idx2];
        tempArray[idx2] = temp;

        stepQueue.add(new SortStep() {
            @Override public void forward() { executeSwap(idx1, idx2); }
            @Override public void backward() { executeSwap(idx1, idx2); }
        });
        stepMessages.add(msg);
    }

    // colors 1 bar
    private void addColorStep(int idx, Color newColor, String msg) {
        Color oldColor = virtualColors[idx];
        virtualColors[idx] = newColor;

        stepQueue.add(new SortStep() {
            @Override public void forward() { bars[idx].setFill(newColor); }
            @Override public void backward() { bars[idx].setFill(oldColor); }
        });
        stepMessages.add(msg);
    }

    // colors 2 bars in one frame
    private void addColorStep(int idx1, int idx2, Color newColor, String msg) {
        Color old1 = virtualColors[idx1];
        Color old2 = virtualColors[idx2];
        virtualColors[idx1] = newColor;
        virtualColors[idx2] = newColor;

        stepQueue.add(new SortStep() {
            @Override public void forward() { bars[idx1].setFill(newColor); bars[idx2].setFill(newColor); }
            @Override public void backward() { bars[idx1].setFill(old1); bars[idx2].setFill(old2); }
        });
        stepMessages.add(msg);
    }

    // swaps bars visually
    private void executeSwap(int idx1, int idx2) {
        Rectangle r1 = bars[idx1];
        Rectangle r2 = bars[idx2];

        bars[idx1] = r2;
        bars[idx2] = r1;

        int t = array[idx1];
        array[idx1] = array[idx2];
        array[idx2] = t;

        double paneW = displayPane.getWidth();
        if (paneW <= 0) paneW = 600;
        double slotWidth = paneW / array.length;

        double targetXForR1 = idx2 * slotWidth;
        double targetXForR2 = idx1 * slotWidth;

        Timeline swapAnimation = new Timeline(
                new KeyFrame(Duration.millis(600),
                        new KeyValue(r1.xProperty(), targetXForR1, Interpolator.EASE_BOTH),
                        new KeyValue(r2.xProperty(), targetXForR2, Interpolator.EASE_BOTH)
                )
        );

        if (speedSlider != null) {
            swapAnimation.rateProperty().bind(speedSlider.valueProperty());
        }
        swapAnimation.play();
    }

    private void addInstantInsertStep(int fromIdx, int toIdx, String msg) {
        if (fromIdx == toIdx) {
            stepQueue.add(new SortStep() {
                @Override public void forward() {}
                @Override public void backward() {}
            });
            stepMessages.add(msg);
            return;
        }

        int temp = tempArray[fromIdx];
        for (int k = fromIdx; k > toIdx; k--) {
            tempArray[k] = tempArray[k - 1];
        }
        tempArray[toIdx] = temp;

        Color[] oldColors = virtualColors.clone();

        for (int k = fromIdx; k > toIdx; k--) {
            virtualColors[k] = Color.LIMEGREEN;
        }
        virtualColors[toIdx] = Color.RED;

        stepQueue.add(new SortStep() {
            @Override
            public void forward() {
                for (int k = fromIdx; k > toIdx; k--) executeSwap(k, k - 1);
                for (int k = toIdx; k <= fromIdx; k++) bars[k].setFill(virtualColors[k]);
            }

            @Override
            public void backward() {
                for (int k = toIdx + 1; k <= fromIdx; k++) executeSwap(k, k - 1);
                for (int k = toIdx; k <= fromIdx; k++) bars[k].setFill(oldColors[k]);
            }
        });
        stepMessages.add(msg);
    }


    // =======================================================
    // --- SORTING ALGORITHMS ---
    // =======================================================

    @FXML
    void runBubbleSort(ActionEvent event) {
        if (!prepareSort("Bubble Sort", event)) return;

        boolean asc = isAscending();
        String cmpWord = asc ? "taller" : "shorter";

        for (int i = 0; i < tempArray.length - 1; i++) {
            for (int j = 0; j < tempArray.length - i - 1; j++) {

                addColorStep(j, j + 1, Color.GOLD, "Highlighting the two adjacent GOLD bars for comparison.");

                boolean shouldSwap = asc
                        ? tempArray[j] > tempArray[j + 1]
                        : tempArray[j] < tempArray[j + 1];

                if (shouldSwap) {
                    addSwapStep(j, j + 1, "The left GOLD bar is " + cmpWord + ", swapping them.");
                }

                addColorStep(j, j + 1, Color.CYAN, "Comparison complete. Reverting the two bars to CYAN.");
            }
            addColorStep(tempArray.length - i - 1, Color.LIMEGREEN,
                    asc ? "The tallest unsorted bar has reached its final position. Locking as GREEN."
                            : "The shortest unsorted bar has reached its final position. Locking as GREEN.");
        }

        addColorStep(0, Color.LIMEGREEN, "Final bar locked as GREEN. Array is fully sorted!");
        togglePlayPause();
    }

    @FXML
    void runSelectionSort(ActionEvent event) {
        if (!prepareSort("Selection Sort", event)) return;

        boolean asc = isAscending();
        String targetWord = asc ? "shortest" : "tallest";

        for (int i = 0; i < tempArray.length - 1; i++) {
            int extreme_idx = i;

            addColorStep(i, Color.GOLD, "Starting a new pass. Marking the current target position in GOLD.");

            for (int j = i + 1; j < tempArray.length; j++) {
                addColorStep(j, Color.GOLD, "Comparing the next bar in GOLD to RED to see if it is " + targetWord + ".");

                boolean isNewExtreme = asc
                        ? tempArray[j] < tempArray[extreme_idx]
                        : tempArray[j] > tempArray[extreme_idx];

                if (isNewExtreme) {
                    if (extreme_idx == i) {
                        addColorStep(i, Color.GOLD, "First bar is currently the " + targetWord + ".");
                    } else {
                        addColorStep(extreme_idx, Color.CYAN, "Discarding the old candidate, reverting it to CYAN.");
                    }
                    extreme_idx = j;
                    addColorStep(extreme_idx, Color.RED, "Found a " + targetWord + " bar! Marking the new candidate in RED.");
                } else {
                    addColorStep(j, Color.CYAN, "This bar does not qualify. Ignoring it and reverting to CYAN.");
                }
            }

            addColorStep(i, Color.CYAN, "Scan complete for this pass. Reverting the start position to CYAN.");
            addSwapStep(i, extreme_idx, "Swapping the RED bar into its correct sorted position.");
            addColorStep(i, Color.LIMEGREEN, "The bar is now in its final sorted position. Locking as GREEN.");
        }
        addColorStep(tempArray.length - 1, Color.LIMEGREEN, "Final element sorted! Locking as GREEN.");

        togglePlayPause();
    }

    @FXML
    void runInsertionSort(ActionEvent event) {
        if (!prepareSort("Insertion Sort", event)) return;

        boolean asc = isAscending();

        addColorStep(0, Color.LIMEGREEN, "The first bar is trivially sorted. Locking as GREEN.");

        for (int i = 1; i < tempArray.length; i++) {
            int j = i - 1;
            int target = tempArray[i];

            addColorStep(i, Color.RED, "Selecting the next unsorted bar and marking it RED.");

            boolean shiftCondition = asc
                    ? tempArray[j] > target
                    : tempArray[j] < target;

            while (j >= 0 && shiftCondition) {
                addColorStep(j, Color.GOLD, "Comparing the RED bar against the sorted GOLD bar.");
                addColorStep(j, Color.LIMEGREEN, "Needs to be shifted to insert the RED bar.");
                j--;
                if (j >= 0) {
                    shiftCondition = asc ? tempArray[j] > target : tempArray[j] < target;
                } else {
                    shiftCondition = false;
                }
            }
            int targetSpot = j + 1;

            if (targetSpot == i) {
                addColorStep(i, Color.RED, "Found spot! The RED bar is already in the correct sequence position.");
            } else {
                addColorStep(i, Color.RED, "Found spot! Preparing to insert the RED bar into the opened space.");
                addInstantInsertStep(i, targetSpot, "Inserting the RED bar into its correct position.");
            }

            addColorStep(targetSpot, Color.LIMEGREEN, "Bar is successfully placed in the sorted sequence. Locking as GREEN.");
        }

        togglePlayPause();
    }

    @FXML
    void runQuickSort(ActionEvent event) {
        if (!prepareSort("Quick Sort", event)) return;

        quickSortHelper(0, tempArray.length - 1);

        for (int i = 0; i < tempArray.length; i++) {
            addColorStep(i, Color.LIMEGREEN, "Marking all bars as GREEN. Array fully sorted!");
        }

        togglePlayPause();
    }

    private void quickSortHelper(int low, int high) {
        if (low < high) {
            int pivotIndex = partition(low, high);

            addColorStep(pivotIndex, Color.LIMEGREEN, "Pivot bar is perfectly placed. Locking as GREEN.");

            quickSortHelper(low, pivotIndex - 1);
            quickSortHelper(pivotIndex + 1, high);

        } else if (low == high) {
            addColorStep(low, Color.LIMEGREEN, "Single bar remaining in this partition. Locking as GREEN.");
        }
    }

    private int partition(int low, int high) {
        int pivotValue = tempArray[high];
        boolean asc = isAscending();
        addColorStep(high, Color.MAGENTA, "Selecting the end bar as the pivot and marking it MAGENTA.");

        int i = low - 1;

        addArrowStep(i + 1, true, "ORANGE arrow marks the boundary for " + (asc ? "smaller" : "larger") + " elements.");

        for (int j = low; j < high; j++) {
            addColorStep(j, Color.GOLD, "Highlighting the current bar in GOLD to compare against the MAGENTA pivot.");

            boolean shouldMove = asc
                    ? tempArray[j] < pivotValue
                    : tempArray[j] > pivotValue;

            if (shouldMove) {
                i++;

                if (i != j) {
                    addSwapStep(i, j, "The GOLD bar qualifies! Swapping it into the boundary under the arrow.");
                    addColorStep(i, Color.CYAN, "Swap complete. Reverting the bar to CYAN.");
                } else {
                    addColorStep(j, Color.CYAN, "The bar qualifies but is already in position. Reverting to CYAN.");
                }

                addArrowStep(i + 1, true, "Moving target boundary forward.");

            } else {
                addColorStep(j, Color.CYAN, "The GOLD bar does not qualify. Leaving it on the other side and reverting to CYAN.");
            }
        }

        addArrowStep(i + 1, true, "Partition scan complete! ORANGE arrow marks the pivot's final destination.");

        if (i + 1 != high) {
            addSwapStep(i + 1, high, "Swapping the MAGENTA pivot into the dividing point under the arrow.");
            addColorStep(i + 1, Color.MAGENTA, "The MAGENTA pivot has reached its correct dividing position.");
            addColorStep(high, Color.CYAN, "Reverting the displaced bar to CYAN.");
        }

        addArrowStep(-1, false, "Hiding target arrow.");

        return i + 1;
    }


    // Merge sort depth-row tracking
    private int[] virtualDepth;
    private int   maxSortDepth;
    private int   maxArrayValue;

    @FXML
    void runMergeSort(ActionEvent event) {
        if (!prepareSort("Merge Sort", event)) return;

        int n = tempArray.length;

        maxSortDepth   = (n <= 1) ? 0 : (int) Math.ceil(Math.log(n) / Math.log(2));
        virtualDepth   = new int[n];
        Arrays.fill(virtualDepth, -1);

        maxArrayValue = 0;
        for (int v : array) if (v > maxArrayValue) maxArrayValue = v;

        addRepositionStep(0, n - 1, 0, "Starting Merge Sort. Dropping all bars to the top level to begin dividing.");

        mergeSortHelper(0, n - 1, 0);

        addRepositionStep(0, n - 1, -1, "Algorithm complete! Expanding bars back to full height.");
        for (int i = 0; i < n; i++) addColorStep(i, Color.LIMEGREEN, "Merge Sort Finished!");

        togglePlayPause();
    }

    private void mergeSortHelper(int low, int high, int depth) {
        if (low >= high) {
            if (low == high) {
                addColorStep(low, Color.LIMEGREEN, "Base case: A single bar is already sorted. Marking GREEN.");
                addSingleRepositionStep(low, depth, "Moving the sorted single bar up to await merging.");
            }
            return;
        }

        int mid = (low + high) / 2;

        for (int i = low; i <= high; i++) addColorStep(i, Color.GOLD, "Dividing Phase: Highlighting the current subarray in GOLD.");
        for (int i = low; i <= high; i++) addColorStep(i, Color.CYAN, "Splitting the GOLD subarray into a left and right half.");

        addRepositionStep(low,      mid,  depth + 1, "Dropping the left half down one level to divide it further.");
        addRepositionStep(mid + 1, high, depth + 1, "Dropping the right half down one level to divide it further.");

        mergeSortHelper(low,      mid,  depth + 1);
        mergeSortHelper(mid + 1, high, depth + 1);

        merge(low, mid, high, depth);
    }

    private void merge(int low, int mid, int high, int depth) {
        boolean asc = isAscending();

        for (int i = low;      i <= mid;  i++) addColorStep(i, Color.CYAN, "Merge Phase: Marking the left half CYAN.");
        for (int i = mid + 1; i <= high; i++) addColorStep(i, Color.MAGENTA, "Merge Phase: Marking the right half MAGENTA. Now merging them into sorted order.");

        int left         = low;
        int currentMid   = mid;
        int right        = mid + 1;
        int currentDepth = depth + 1;

        while (left <= currentMid && right <= high) {
            addColorStep(left,  Color.GOLD, "Selecting the next bar from the left half (GOLD).");
            addColorStep(right, Color.GOLD, "Comparing it against the next bar from the right half (GOLD).");

            boolean leftWins = asc
                    ? tempArray[left] <= tempArray[right]
                    : tempArray[left] >= tempArray[right];

            if (leftWins) {
                addColorStep(left, Color.LIMEGREEN, "The left GOLD bar wins this comparison. Marking GREEN.");
                addSingleRepositionStep(left, depth, "Moving the GREEN bar up into its sorted position.");
                left++;
            } else {
                addMergeRotateAndRiseStep(right, left, currentDepth, depth, "The right GOLD bar wins! Shifting it past the left half and moving it up. Marking GREEN.");
                currentMid++;
                right++;
                left++;
            }
        }

        while (left <= currentMid) {
            addColorStep(left, Color.LIMEGREEN, "Right half is exhausted. Marking remaining left bar GREEN.");
            addSingleRepositionStep(left, depth, "Moving the remaining sorted bar up.");
            left++;
        }

        while (right <= high) {
            addColorStep(right, Color.LIMEGREEN, "Left half is exhausted. Marking remaining right bar GREEN.");
            addSingleRepositionStep(right, depth, "Moving the remaining sorted bar up.");
            right++;
        }
    }

    private void addMergeRotateAndRiseStep(int fromIdx, int toIdx, int currentDepth, int targetDepth, String msg) {
        int temp = tempArray[fromIdx];
        for (int k = fromIdx; k > toIdx; k--) tempArray[k] = tempArray[k - 1];
        tempArray[toIdx] = temp;

        virtualDepth[toIdx] = targetDepth;
        Color[] oldColors = virtualColors.clone();
        for (int k = fromIdx; k > toIdx; k--) virtualColors[k] = virtualColors[k - 1];
        virtualColors[toIdx] = Color.LIMEGREEN;

        stepQueue.add(new SortStep() {
            @Override
            public void forward() {
                Rectangle winner = bars[fromIdx];
                int winnerVal    = array[fromIdx];
                for (int k = fromIdx; k > toIdx; k--) {
                    bars[k]  = bars[k - 1];
                    array[k] = array[k - 1];
                }
                bars[toIdx]  = winner;
                array[toIdx] = winnerVal;

                double paneW = displayPane.getWidth()  > 0 ? displayPane.getWidth()  : 600;
                double paneH = displayPane.getHeight() > 0 ? displayPane.getHeight() : 400;
                double slotW = paneW / array.length;
                double rowH  = paneH / (maxSortDepth + 2.0);

                double wH = (array[toIdx] / (double) maxArrayValue) * rowH * 0.85;
                double wY = (targetDepth + 1) * rowH - wH;
                double wX = toIdx * slotW;

                Timeline tl = new Timeline();
                tl.getKeyFrames().add(new KeyFrame(Duration.millis(450),
                        new KeyValue(winner.xProperty(),      wX, Interpolator.EASE_BOTH),
                        new KeyValue(winner.yProperty(),      wY, Interpolator.EASE_BOTH),
                        new KeyValue(winner.heightProperty(), wH, Interpolator.EASE_BOTH)
                ));
                winner.setFill(Color.LIMEGREEN);

                if (speedSlider != null) tl.rateProperty().bind(speedSlider.valueProperty());
                tl.play();
            }

            @Override
            public void backward() {
                Rectangle winner = bars[toIdx];
                int winnerVal    = array[toIdx];
                for (int k = toIdx; k < fromIdx; k++) {
                    bars[k]  = bars[k + 1];
                    array[k] = array[k + 1];
                }
                bars[fromIdx]  = winner;
                array[fromIdx] = winnerVal;

                virtualDepth[toIdx] = currentDepth;
                System.arraycopy(oldColors, 0, virtualColors, 0, oldColors.length);

                double paneW = displayPane.getWidth()  > 0 ? displayPane.getWidth()  : 600;
                double paneH = displayPane.getHeight() > 0 ? displayPane.getHeight() : 400;
                double slotW = paneW / array.length;
                double rowH  = paneH / (maxSortDepth + 2.0);

                double wH = (array[fromIdx] / (double) maxArrayValue) * rowH * 0.85;
                double wY = (currentDepth + 1) * rowH - wH;
                double wX = fromIdx * slotW;

                Timeline tl = new Timeline();
                tl.getKeyFrames().add(new KeyFrame(Duration.millis(450),
                        new KeyValue(winner.xProperty(),      wX, Interpolator.EASE_BOTH),
                        new KeyValue(winner.yProperty(),      wY, Interpolator.EASE_BOTH),
                        new KeyValue(winner.heightProperty(), wH, Interpolator.EASE_BOTH)
                ));
                winner.setFill(oldColors[fromIdx]);

                for (int k = toIdx; k < fromIdx; k++) {
                    bars[k].setFill(oldColors[k]);
                }

                if (speedSlider != null) tl.rateProperty().bind(speedSlider.valueProperty());
                tl.play();
            }
        });
        stepMessages.add(msg);
    }

    private void addSingleRepositionStep(int idx, int targetDepth, String msg) {
        int prevDepth = virtualDepth[idx];
        virtualDepth[idx] = targetDepth;

        stepQueue.add(new SortStep() {
            @Override public void forward()  { playSingleRepositionAnim(idx, targetDepth); }
            @Override public void backward() { playSingleRepositionAnim(idx, prevDepth);   }
        });
        stepMessages.add(msg);
    }

    private void playSingleRepositionAnim(int idx, int depth) {
        double paneW = displayPane.getWidth()  > 0 ? displayPane.getWidth()  : 600;
        double paneH = displayPane.getHeight() > 0 ? displayPane.getHeight() : 400;
        double slotW = paneW / array.length;
        double rowH  = paneH / (maxSortDepth + 2.0);

        double barH, barY, barX;
        barX = idx * slotW;

        if (depth < 0) {
            barH = (array[idx] / (double) maxArrayValue) * paneH * 0.9;
            barY = paneH - barH;
        } else {
            barH = (array[idx] / (double) maxArrayValue) * rowH * 0.85;
            barY = (depth + 1) * rowH - barH;
        }

        Timeline tl = new Timeline(new KeyFrame(Duration.millis(400),
                new KeyValue(bars[idx].xProperty(),      barX, Interpolator.EASE_BOTH),
                new KeyValue(bars[idx].yProperty(),      barY, Interpolator.EASE_BOTH),
                new KeyValue(bars[idx].heightProperty(), barH, Interpolator.EASE_BOTH)
        ));
        if (speedSlider != null) tl.rateProperty().bind(speedSlider.valueProperty());
        tl.play();
    }

    private void addRepositionStep(int low, int high, int targetDepth, String msg) {
        int   count      = high - low + 1;
        int[] indices    = new int[count];
        int[] prevDepths = new int[count];

        for (int i = low; i <= high; i++) {
            int k      = i - low;
            indices[k] = i;
            prevDepths[k] = virtualDepth[i];
            virtualDepth[i] = targetDepth;
        }

        int[] targetDepths = makeUniform(count, targetDepth);

        stepQueue.add(new SortStep() {
            @Override public void forward()  { playRepositionAnim(indices, targetDepths); }
            @Override public void backward() { playRepositionAnim(indices, prevDepths);   }
        });
        stepMessages.add(msg);
    }

    private int[] makeUniform(int length, int value) {
        int[] arr = new int[length];
        Arrays.fill(arr, value);
        return arr;
    }

    private void playRepositionAnim(int[] indices, int[] depths) {
        double paneW = displayPane.getWidth()  > 0 ? displayPane.getWidth()  : 600;
        double paneH = displayPane.getHeight() > 0 ? displayPane.getHeight() : 400;
        double slotW = paneW / array.length;
        double rowH  = paneH / (maxSortDepth + 2.0);

        Timeline tl = new Timeline();

        for (int k = 0; k < indices.length; k++) {
            int idx   = indices[k];
            int depth = depths[k];

            double barH, barY, barX;
            barX = idx * slotW;

            if (depth < 0) {
                barH = (array[idx] / (double) maxArrayValue) * paneH * 0.9;
                barY = paneH - barH;
            } else {
                barH = (array[idx] / (double) maxArrayValue) * rowH * 0.85;
                barY = (depth + 1) * rowH - barH;
            }

            tl.getKeyFrames().add(new KeyFrame(Duration.millis(500),
                    new KeyValue(bars[idx].xProperty(),      barX, Interpolator.EASE_BOTH),
                    new KeyValue(bars[idx].yProperty(),      barY, Interpolator.EASE_BOTH),
                    new KeyValue(bars[idx].heightProperty(), barH, Interpolator.EASE_BOTH)
            ));
        }

        if (speedSlider != null) tl.rateProperty().bind(speedSlider.valueProperty());
        tl.play();
    }

    // ==========================================================================
    // CAPTURE & RECORDING LOGIC (LOCKED RESOLUTION & ANTI-FLOOD)
    // ==========================================================================
    @FXML
    void takeScreenshot() {
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.WHITE); // White background for sorting

        WritableImage snapshot = displayPane.snapshot(params, null);
        BufferedImage buffered = SwingFXUtils.fromFXImage(snapshot, null);

        String downloadsDir = getDownloadsPath();
        String timestamp    = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File   outputFile   = new File(downloadsDir, "sorting_" + timestamp + ".png");

        try {
            ImageIO.write(buffered, "png", outputFile);
            System.out.println("Screenshot saved: " + outputFile.getAbsolutePath());
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
        isRecording = true;
        isCapturing = false;
        recordBtn.setText("⏹");
        recordBtn.setStyle(
                "-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-size: 16px;" +
                        "-fx-background-radius: 8; -fx-cursor: hand; -fx-border-color: #991b1b; -fx-border-radius: 8;"
        );

        // LOCK RESOLUTION based on the starting size of the displayPane
        SnapshotParameters initParams = new SnapshotParameters();
        initParams.setFill(Color.WHITE);
        WritableImage initSnap = displayPane.snapshot(initParams, null);

        final int lockedW = ((int) initSnap.getWidth() % 2 == 0) ? (int) initSnap.getWidth() : (int) initSnap.getWidth() + 1;
        final int lockedH = ((int) initSnap.getHeight() % 2 == 0) ? (int) initSnap.getHeight() : (int) initSnap.getHeight() + 1;

        try {
            String downloadsDir = getDownloadsPath();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File outputFile = new File(downloadsDir, "sorting_rec_" + timestamp + ".mp4");

            encoder = AWTSequenceEncoder.createSequenceEncoder(outputFile, RECORD_FPS);
            System.out.println("Recording started... Streaming to: " + outputFile.getAbsolutePath() + " at " + lockedW + "x" + lockedH);
        } catch (IOException e) {
            System.err.println("Failed to start video encoder: " + e.getMessage());
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

                        g.setColor(java.awt.Color.WHITE); // Fill background white
                        g.fillRect(0, 0, lockedW, lockedH);

                        g.drawImage(frame, 0, 0, null); // Draw exact pixels
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

        recordingExecutor.scheduleAtFixedRate(() -> {
            if (!isRecording || isCapturing) return;
            isCapturing = true;

            Platform.runLater(() -> {
                try {
                    SnapshotParameters params = new SnapshotParameters();
                    params.setFill(Color.WHITE);

                    WritableImage fxFrame = displayPane.snapshot(params, null);
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
            recordBtn.setStyle("");
        });
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------
    private String getDownloadsPath() {
        String home = System.getProperty("user.home");
        Path   dl   = Paths.get(home, "Downloads");
        if (!dl.toFile().exists()) dl.toFile().mkdirs();
        return dl.toString();
    }

    @FXML
    void backToHome(ActionEvent event) throws IOException {
        if (isRecording) stopRecording();
        stopAll();
        Launcher.switchScene("hello-view.fxml");
    }
}