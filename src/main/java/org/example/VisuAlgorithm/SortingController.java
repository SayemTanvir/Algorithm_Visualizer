package org.example.VisuAlgorithm;

import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import javafx.animation.KeyValue;
import javafx.animation.Interpolator;

public class SortingController {

    @FXML private Pane displayPane;
    @FXML private Slider sizeSlider;
    @FXML private Slider speedSlider;
    @FXML private Label sizeLabel;
    @FXML private TextField customInput;

    @FXML private Button bubbleSortBtn;
    @FXML private Button playPauseBtn;
    @FXML private Button stepBackBtn;
    @FXML private Button skipBtn;

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

    private List<SortStep> stepQueue = new ArrayList<>();               //stores thee steps
    private List<String> stepMessages = new ArrayList<>();              //stores the live status text for each step
    private int currentStepIndex = 0;                                   //current step in animation
    private Timeline playTimeline;                                      //jfx animator
    private boolean isPlaying = false;

    private int[] tempArray;                                            //array sorted at the beginning to record steps
    private Color[] virtualColors;                                      // array of colors to keep track of bar colors

    @FXML
    public void initialize() {
        // Enforcing max limit of 25 programmatically to make space
        sizeSlider.setMax(25);
        sizeSlider.setValue(10);
        updateSizeLabel(10);

        //setting speed slider (default = 3x)
        if (speedSlider != null) {
            speedSlider.setMin(0.5);
            speedSlider.setMax(10.0);
            speedSlider.setValue(2.0);
        }

        //generates array after creating window
        Platform.runLater(this::generateRandomArray);

        //generates new array everytime size slider is changed  (.addListener watches for value change and executes the following lambda func)
        sizeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int oldSize = oldVal.intValue();
            int newSize = newVal.intValue();

            if (oldSize != newSize) {
                updateSizeLabel(newSize);
                generateRandomArray();
            }
        });

        //draws new bars for everytime window size is changed
        displayPane.widthProperty().addListener((obs, o, n) -> drawArray());
        displayPane.heightProperty().addListener((obs, o, n) -> drawArray());

        //play,pause,back,forward buttons disabled
        setMediaControlsDisable(true);

        setupTimeline();
    }

    //updates size text
    private void updateSizeLabel(int size) {
        if (sizeLabel != null) sizeLabel.setText("Size: " + size);
    }

    //animator
    private void setupTimeline() {
        playTimeline = new Timeline(new KeyFrame(Duration.millis(1000), e -> executeNextStep()));        //calls executeNextStep() every 300ms
        playTimeline.setCycleCount(Timeline.INDEFINITE);          // setting end to indefinite
        if (speedSlider != null) {
            playTimeline.rateProperty().bind(speedSlider.valueProperty());      //rate is bind to value of speed slider
        }
    }

    //disable controls
    private void setControlsDisable(boolean disable) {
        sizeSlider.setDisable(disable);
        customInput.setDisable(disable);
        // Individual sorting buttons are now handled dynamically by currentActiveSortBtn
    }

    private void setMediaControlsDisable(boolean disable) {
        if (playPauseBtn != null) playPauseBtn.setDisable(disable);
        if (stepBackBtn != null) stepBackBtn.setDisable(disable);
        if (skipBtn != null) skipBtn.setDisable(disable);
    }

    //controls
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
            updateStatusLabel(currentStepIndex - 1); // Show message for the step we reverted TO

            if (currentStepIndex < stepQueue.size()) {
                setControlsDisable(true);
            }
        }
    }

    private void executeNextStep() {
        if (currentStepIndex < stepQueue.size()) {
            updateStatusLabel(currentStepIndex); // Show message for the current step
            stepQueue.get(currentStepIndex).forward();
            currentStepIndex++;
        } else {
            playTimeline.stop();
            isPlaying = false;
            playPauseBtn.setText("▶");
            setControlsDisable(false);

            // Re-enable the active sorting button now that sorting is finished
            if (currentActiveSortBtn != null) {
                currentActiveSortBtn.setDisable(false);
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

        if (currentActiveSortBtn != null) {
            currentActiveSortBtn.setDisable(false);
            currentActiveSortBtn = null;
        }

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
            double normalizedHeight = ((double) array[i] / maxVal) * (paneH * 0.85); // slightly smaller to give space for arrow

            bar.setX(i * (paneW / size));
            bar.setY(paneH - normalizedHeight);
            bar.setWidth(barWidth);
            bar.setHeight(normalizedHeight);
            bar.setFill(Color.CYAN);

            bars[i] = bar;
            displayPane.getChildren().add(bar);
        }

        // Ensure Arrow exists and is added over the bars
        if (iPointerArrow == null) {
            // A bold downward pointing triangle arrow
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

        // Stop any currently running timeline if we switch algorithms midway
        if (playTimeline != null) playTimeline.stop();
        isPlaying = false;
        if (playPauseBtn != null) playPauseBtn.setText("▶");

        if (algoNameLabel != null) algoNameLabel.setText("Algorithm: " + algoName);
        if (currentStepLabel != null) currentStepLabel.setText("Action: Starting...");
        if (stepDescriptionArea != null) stepDescriptionArea.setText("Ready to sort.");

        // Re-enable previously greyed out button if user clicked a new one
        if (currentActiveSortBtn != null) {
            currentActiveSortBtn.setDisable(false);
        }
        // Grey out the newly clicked algorithm button
        if (event != null && event.getSource() instanceof Button) {
            currentActiveSortBtn = (Button) event.getSource();
            currentActiveSortBtn.setDisable(true);
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

            // Center arrow horizontally
            iPointerArrow.setLayoutX(barX - 12);

            // Get the physical Y coordinate of the actual bar we are pointing to
            double barY = bars[idx].getY();

            // Set the arrow's bounding box to end right before the top of the bar
            // (18 is the arrow's height, plus 4 pixels of padding = 22)
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

        // 2. add the step
        stepQueue.add(new SortStep() {
            @Override public void forward() { executeSwap(idx1, idx2); }
            @Override public void backward() { executeSwap(idx1, idx2); }
        });
        stepMessages.add(msg);
    }

    // colors 1 bar
    private void addColorStep(int idx, Color newColor, String msg) {
        Color oldColor = virtualColors[idx]; // Remember what it WAS
        virtualColors[idx] = newColor;       // Update what it WILL BE

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

    //swaps bars visually
    private void executeSwap(int idx1, int idx2){
        Rectangle r1 = bars[idx1];
        Rectangle r2 = bars[idx2];

        // Swap tracking array
        bars[idx1] = r2;
        bars[idx2] = r1;

        // Swap math array
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

        // shifting math
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
    void runBubbleSort(ActionEvent event){
        if (!prepareSort("Bubble Sort", event)) return;

        for (int i = 0; i < tempArray.length - 1; i++) {
            for (int j = 0; j < tempArray.length - i - 1; j++) {

                addColorStep(j, j + 1, Color.YELLOW, "Highlighting the two adjacent YELLOW bars for comparison.");

                if (tempArray[j] > tempArray[j + 1]) {
                    addSwapStep(j, j + 1, "The left YELLOW bar is taller, swapping them.");
                }

                addColorStep(j, j + 1, Color.CYAN, "Comparison complete. Reverting the two bars to CYAN.");
            }
            addColorStep(tempArray.length - i - 1, Color.LIMEGREEN, "The tallest unsorted bar has reached its final position. Locking as GREEN.");
        }

        addColorStep(0, Color.LIMEGREEN, "Final bar locked as GREEN. Array is fully sorted!");
        togglePlayPause();
    }

    @FXML
    void runSelectionSort(ActionEvent event){
        if(!prepareSort("Selection Sort", event)) return;

        for(int i = 0; i < tempArray.length - 1; i++){
            int min_idx = i;

            addColorStep(i, Color.YELLOW, "Starting a new pass. Marking the current target position in YELLOW.");

            for(int j = i + 1; j < tempArray.length; j++){
                addColorStep(j, Color.YELLOW, "Comparing the next bar in YELLOW to RED to see if it is shorter.");

                if(tempArray[j] < tempArray[min_idx]){
                    if(min_idx == i){
                        addColorStep(i, Color.YELLOW, "First bar is currently the shortest.");
                    }
                    else{
                        addColorStep(min_idx, Color.CYAN, "Discarding the old minimum, reverting it to CYAN.");
                    }
                    min_idx = j;
                    addColorStep(min_idx, Color.RED, "Found a shorter bar! Marking the new minimum candidate in RED.");
                }
                else {
                    addColorStep(j, Color.CYAN, "This bar is taller. Ignoring it and reverting to CYAN.");
                }
            }

            addColorStep(i, Color.CYAN, "Scan complete for this pass. Reverting the start position to CYAN.");
            addSwapStep(i, min_idx, "Swapping the shortest RED bar into its correct sorted position.");
            addColorStep(i, Color.LIMEGREEN, "The bar is now in its final sorted position. Locking as GREEN.");
        }
        addColorStep(tempArray.length - 1, Color.LIMEGREEN, "Final element sorted! Locking as GREEN.");

        togglePlayPause();
    }

    @FXML
    void runInsertionSort(ActionEvent event){
        if(!prepareSort("Insertion Sort", event)) return;

        addColorStep(0, Color.LIMEGREEN, "The first bar is trivially sorted. Locking as GREEN.");

        for(int i = 1; i < tempArray.length; i++){
            int j = i - 1;
            int target = tempArray[i];

            addColorStep(i, Color.RED, "Selecting the next unsorted bar and marking it RED.");

            while(j >= 0 && tempArray[j] > target){
                addColorStep(j, Color.YELLOW, "Comparing the RED bar against the sorted YELLOW bar.");
                addColorStep(j, Color.LIMEGREEN, "Needs to be shifted to insert the RED bar.");
                j--;
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

            addColorStep(pivotIndex, Color.LIMEGREEN, "Pivot bar is perfectly placed. Locking as LIME GREEN.");

            quickSortHelper(low, pivotIndex - 1);
            quickSortHelper(pivotIndex + 1, high);

        } else if (low == high) {
            addColorStep(low, Color.LIMEGREEN, "Single bar remaining in this partition. Locking as LIME GREEN.");
        }
    }

    private int partition(int low, int high){
        int pivotValue = tempArray[high];
        addColorStep(high, Color.MAGENTA, "Selecting the end bar as the pivot and marking it MAGENTA.");

        int i = low - 1;

        // Setup arrow boundary marker at the beginning of partition
        addArrowStep(i + 1, true, "ORANGE arrow marks the boundary for smaller elements.");

        for (int j = low; j < high; j++){
            addColorStep(j, Color.YELLOW, "Highlighting the current bar in YELLOW to compare against the MAGENTA pivot.");

            if (tempArray[j] < pivotValue){
                i++;

                if (i != j) {
                    addSwapStep(i, j, "The YELLOW bar is shorter! Swapping it into the boundary under the arrow.");
                    addColorStep(i, Color.CYAN, "Swap complete. Reverting the shorter bar to CYAN.");
                } else {
                    addColorStep(j, Color.CYAN, "The bar is shorter but already in position. Reverting to CYAN.");
                }

                // Shift arrow boundary right if there are more elements
                addArrowStep(i + 1, true, "Moving target boundary forward.");

            } else {
                addColorStep(j, Color.CYAN, "The YELLOW bar is taller than the pivot. Leaving it on the right and reverting to CYAN.");
            }
        }

        addArrowStep(i + 1, true, "Partition scan complete! ORANGE arrow marks the pivot's final destination.");

        if (i + 1 != high) {
            addSwapStep(i + 1, high, "Swapping the MAGENTA pivot into the dividing point under the arrow.");
            addColorStep(i + 1, Color.MAGENTA, "The MAGENTA pivot has reached its correct dividing position.");
            addColorStep(high, Color.CYAN, "Reverting the placed bar to CYAN.");
        }

        // Hide arrow at the end of this partition frame
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
                addColorStep(low, Color.LIMEGREEN, "Base case: A single bar is already sorted. Marking LIME GREEN.");
                addSingleRepositionStep(low, depth, "Moving the sorted single bar up to await merging.");
            }
            return;
        }

        int mid = (low + high) / 2;

        for (int i = low; i <= high; i++) addColorStep(i, Color.YELLOW, "Dividing Phase: Highlighting the current subarray in YELLOW.");
        for (int i = low; i <= high; i++) addColorStep(i, Color.CYAN, "Splitting the YELLOW subarray into a left and right half.");

        addRepositionStep(low,      mid,  depth + 1, "Dropping the left half down one level to divide it further.");
        addRepositionStep(mid + 1, high, depth + 1, "Dropping the right half down one level to divide it further.");

        mergeSortHelper(low,      mid,  depth + 1);
        mergeSortHelper(mid + 1, high, depth + 1);

        merge(low, mid, high, depth);
    }

    private void merge(int low, int mid, int high, int depth) {
        for (int i = low;      i <= mid;  i++) addColorStep(i, Color.CYAN, "Merge Phase: Marking the left half CYAN.");
        for (int i = mid + 1; i <= high; i++) addColorStep(i, Color.MAGENTA, "Merge Phase: Marking the right half MAGENTA. Now merging them into sorted order.");

        int left         = low;
        int currentMid   = mid;
        int right        = mid + 1;
        int currentDepth = depth + 1;

        while (left <= currentMid && right <= high) {
            addColorStep(left,  Color.YELLOW, "Selecting the smallest remaining bar from the left half (YELLOW).");
            addColorStep(right, Color.YELLOW, "Comparing it against the smallest remaining bar from the right half (YELLOW).");

            if (tempArray[left] <= tempArray[right]) {
                addColorStep(left, Color.LIMEGREEN, "The left YELLOW bar is smaller (or equal). Marking LIME GREEN.");
                addSingleRepositionStep(left, depth, "Moving the LIME GREEN bar up into its sorted position in the merged array.");
                left++;
            } else {
                addMergeRotateAndRiseStep(right, left, currentDepth, depth, "The right YELLOW bar is smaller! Shifting it past the left half and moving it up. Marking LIME GREEN.");
                currentMid++;
                right++;
                left++;
            }
        }

        while (left <= currentMid) {
            addColorStep(left, Color.LIMEGREEN, "Right half is exhausted. Marking remaining left bar LIME GREEN.");
            addSingleRepositionStep(left, depth, "Moving the remaining sorted bar up.");
            left++;
        }

        while (right <= high) {
            addColorStep(right, Color.LIMEGREEN, "Left half is exhausted. Marking remaining right bar LIME GREEN.");
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

    @FXML
    void backToHome(ActionEvent event) throws IOException {
        stopAll();
//        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("hello-view.fxml"));
//        Parent root = fxmlLoader.load();
//        Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
//        Scene scene = new Scene(root);
//
//        // Always re-add CSS!
//        scene.getStylesheets().add(getClass().getResource("/org/example/VisuAlgorithm/styles/main.css").toExternalForm());
//
//        stage.setScene(scene);
//        stage.show();
        Launcher.switchScene("hello-view.fxml");
    }
}