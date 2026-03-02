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
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
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

    // Main Data -> changes as the animation goes
    private int[] array;
    private Rectangle[] bars;

    // Animation helpers
    private interface SortStep {
        void forward();
        void backward();
    }

    private List<SortStep> stepQueue = new ArrayList<>();               //stores thee steps
    private int currentStepIndex = 0;                                   //current step in animation
    private Timeline playTimeline;                                      //jfx animator
    private boolean isPlaying = false;

    private int[] tempArray;                                            //array sorted at the beginning to record steps
    private Color[] virtualColors;                                      // array of colors to keep track of bar colors

    @FXML
    public void initialize() {
        //setting default size to 10
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
        playTimeline = new Timeline(new KeyFrame(Duration.millis(300), e -> executeNextStep()));        //calls executeNextStep() every 300ms
        playTimeline.setCycleCount(Timeline.INDEFINITE);          // setting end to indefinite
        if (speedSlider != null) {
            playTimeline.rateProperty().bind(speedSlider.valueProperty());      //rate is bind to value of speed slider
        }
    }

    //disable controls
    private void setControlsDisable(boolean disable) {
        sizeSlider.setDisable(disable);
        customInput.setDisable(disable);
        if (bubbleSortBtn != null) bubbleSortBtn.setDisable(disable);
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

            if (currentStepIndex < stepQueue.size()) {
                setControlsDisable(true);
            }
        }
    }

    private void executeNextStep() {
        if (currentStepIndex < stepQueue.size()) {
            stepQueue.get(currentStepIndex).forward();
            currentStepIndex++;
        } else {
            playTimeline.stop();
            isPlaying = false;
            playPauseBtn.setText("▶");
            setControlsDisable(false);
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
        currentStepIndex = 0;
        setControlsDisable(false);
        setMediaControlsDisable(true);
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
            double normalizedHeight = ((double) array[i] / maxVal) * (paneH * 0.9);

            bar.setX(i * (paneW / size));
            bar.setY(paneH - normalizedHeight);
            bar.setWidth(barWidth);
            bar.setHeight(normalizedHeight);
            bar.setFill(Color.CYAN);

            bars[i] = bar;
            displayPane.getChildren().add(bar);
        }
    }


    private boolean prepareSort() {
        if (array == null || array.length == 0) return false;

        setControlsDisable(true);
        setMediaControlsDisable(false);
        stepQueue.clear();
        currentStepIndex = 0;

        tempArray = array.clone();
        virtualColors = new Color[array.length];
        for (int i = 0; i < array.length; i++) virtualColors[i] = Color.CYAN;

        return true;
    }

    // swap
    private void addSwapStep(int idx1, int idx2) {
        int temp = tempArray[idx1];
        tempArray[idx1] = tempArray[idx2];
        tempArray[idx2] = temp;

        // 2. add the step
        stepQueue.add(new SortStep() {
            @Override public void forward() { executeSwap(idx1, idx2); }
            @Override public void backward() { executeSwap(idx1, idx2); }
        });
    }

    // colors 1 bar
    private void addColorStep(int idx, Color newColor) {
        Color oldColor = virtualColors[idx]; // Remember what it WAS
        virtualColors[idx] = newColor;       // Update what it WILL BE

        stepQueue.add(new SortStep() {
            @Override public void forward() { bars[idx].setFill(newColor); }
            @Override public void backward() { bars[idx].setFill(oldColor); }
        });
    }

    // colors 2 bars in one frame
    private void addColorStep(int idx1, int idx2, Color newColor) {
        Color old1 = virtualColors[idx1];
        Color old2 = virtualColors[idx2];
        virtualColors[idx1] = newColor;
        virtualColors[idx2] = newColor;

        stepQueue.add(new SortStep() {
            @Override public void forward() { bars[idx1].setFill(newColor); bars[idx2].setFill(newColor); }
            @Override public void backward() { bars[idx1].setFill(old1); bars[idx2].setFill(old2); }
        });
    }

    //swaps bars
    private void executeSwap(int idx1, int idx2){
        Rectangle r1 = bars[idx1];
        Rectangle r2 = bars[idx2];

        // 2. Swap them in our tracking array so future steps grab the correct physical bar!
        bars[idx1] = r2;
        bars[idx2] = r1;

        // 3. Swap the master math array
        int t = array[idx1];
        array[idx1] = array[idx2];
        array[idx2] = t;

        // 4. Calculate exactly where they are supposed to move to on the screen
        double paneW = displayPane.getWidth();
        if (paneW <= 0) paneW = 600;
        double slotWidth = paneW / array.length;

        double targetXForR1 = idx2 * slotWidth;
        double targetXForR2 = idx1 * slotWidth;

        // 5. Create the smooth sliding animation
        Timeline swapAnimation = new Timeline(
                new KeyFrame(Duration.millis(600), // Takes 250ms to slide
                        new KeyValue(r1.xProperty(), targetXForR1, Interpolator.EASE_BOTH),
                        new KeyValue(r2.xProperty(), targetXForR2, Interpolator.EASE_BOTH)
                )
        );

        // 6. Bind the sliding animation speed to our speed slider!
        if (speedSlider != null) {
            swapAnimation.rateProperty().bind(speedSlider.valueProperty());
        }

        // Action!
        swapAnimation.play();
    }

    private void addInstantInsertStep(int fromIdx, int toIdx) {
        if (fromIdx == toIdx) return;

        // shifting math
        int temp = tempArray[fromIdx];
        for (int k = fromIdx; k > toIdx; k--) {
            tempArray[k] = tempArray[k - 1];
        }
        tempArray[toIdx] = temp;

        // 2. Instantly update the color scratchpad
        Color[] oldColors = virtualColors.clone(); // Save snapshot for Rewind

        for (int k = fromIdx; k > toIdx; k--) {
            virtualColors[k] = Color.LIMEGREEN; // The bars shifted right stay Green
        }
        virtualColors[toIdx] = Color.RED; // The newly inserted bar stays Red

        // 3. Package all of the movement into ONE single animation frame
        stepQueue.add(new SortStep() {
            @Override
            public void forward() {
                // Ripple the swaps downward instantly
                for (int k = fromIdx; k > toIdx; k--) executeSwap(k, k - 1);

                // Snap all the colors to their new states instantly
                for (int k = toIdx; k <= fromIdx; k++) bars[k].setFill(virtualColors[k]);
            }

            @Override
            public void backward() {
                // To undo, ripple the swaps back upward instantly
                for (int k = toIdx + 1; k <= fromIdx; k++) executeSwap(k, k - 1);

                // Restore old colors
                for (int k = toIdx; k <= fromIdx; k++) bars[k].setFill(oldColors[k]);
            }
        });
    }

    private void quickSortHelper(int low, int high) {
        if (low < high) {
            int pivotIndex = partition(low, high);

            addColorStep(pivotIndex, Color.LIMEGREEN);

            quickSortHelper(low, pivotIndex - 1);
            quickSortHelper(pivotIndex + 1, high);

        } else if (low == high) {
            addColorStep(low, Color.LIMEGREEN);
        }
    }

    private int partition(int low, int high){
        int pivotValue = tempArray[high];
        addColorStep(high, Color.MAGENTA);

        int i = low - 1;

        for (int j = low; j < high; j++){
            addColorStep(j, Color.YELLOW);

            if (tempArray[j] < pivotValue){
                i++;

                if (i != j) {
                    addSwapStep(i, j);
                    addColorStep(i, Color.CYAN);

                } else {
                    addColorStep(j, Color.CYAN);
                }
            } else {
                addColorStep(j, Color.CYAN);
            }
        }

        if (i + 1 != high) {
            addSwapStep(i + 1, high);

            addColorStep(i + 1, Color.MAGENTA);
            addColorStep(high, Color.CYAN);
        }

        return i + 1;
    }
    // =======================================================
    // --- SORTING ALGORITHMS ---
    // =======================================================

    @FXML
    void runBubbleSort(){
        if (!prepareSort()) return;

        for (int i = 0; i < tempArray.length - 1; i++) {
            for (int j = 0; j < tempArray.length - i - 1; j++) {

                // 1. Highlight the bars we are comparing
                addColorStep(j, j + 1, Color.YELLOW);

                // 2. Check and Swap
                if (tempArray[j] > tempArray[j + 1]) {
                    addSwapStep(j, j + 1);
                }

                // 3. Un-highlight the bars
                addColorStep(j, j + 1, Color.CYAN);
            }
            // 4. Mark the end of this pass as fully sorted
            addColorStep(tempArray.length - i - 1, Color.LIMEGREEN);
        }

        // Mark the very first element as sorted when done
        addColorStep(0, Color.LIMEGREEN);

        togglePlayPause();
    }

    @FXML
    void runSelectionSort(){
        if(!prepareSort()) return;

        for(int i = 0; i < tempArray.length - 1; i++){
            int min_idx = i;

            addColorStep(i, Color.YELLOW);
            for(int j = i + 1; j < tempArray.length; j++){
                addColorStep(j, Color.YELLOW);
                if(tempArray[j] < tempArray[min_idx]){
                    if(min_idx == i){
                        addColorStep(i, Color.YELLOW);
                    }
                    else{
                        addColorStep(min_idx, Color.CYAN);
                    }
                    min_idx = j;
                    addColorStep(min_idx, Color.RED);
                }
                else addColorStep(j, Color.CYAN);
            }
            addColorStep(i, Color.CYAN);
            addSwapStep(i, min_idx);
            addColorStep(i, Color.LIMEGREEN);
        }
        addColorStep(tempArray.length - 1, Color.LIMEGREEN);

        togglePlayPause();
    }

    @FXML
    void runInsertionSort(){
        if(!prepareSort()) return;

        addColorStep(0, Color.LIMEGREEN);
        for(int i = 1; i < tempArray.length; i++){
            int j = i - 1;
            addColorStep(i, Color.RED);
            int target = tempArray[i];
            while(j >= 0 && tempArray[j] > target){
                addColorStep(j, Color.YELLOW);
                addColorStep(j, Color.LIMEGREEN);
                j--;
            }
            int targetSpot = j + 1;

            if (targetSpot != i) {
                addInstantInsertStep(i, targetSpot);
            }

            addColorStep(targetSpot, Color.LIMEGREEN);
        }


        togglePlayPause();
    }

    @FXML
    void runQuickSort() {
        if (!prepareSort()) return;

        quickSortHelper(0, tempArray.length - 1);

        for (int i = 0; i < tempArray.length; i++) {
            addColorStep(i, Color.LIMEGREEN);
        }

        togglePlayPause();
    }

    // Merge sort depth-row tracking
    private int[] virtualDepth;   // -1 = full-height display, 0+ = merge sort row
    private int   maxSortDepth;   // ceil(log₂ n) — how many rows the tree needs
    private int   maxArrayValue;  // max value, stable — used for height scaling
    // =======================================================
// MERGE SORT — depth-row tree visualisation
// =======================================================

    @FXML
    void runMergeSort() {
        if (!prepareSort()) return;

        int n = tempArray.length;

        // How many levels the recursion tree has
        maxSortDepth   = (n <= 1) ? 0 : (int) Math.ceil(Math.log(n) / Math.log(2));
        virtualDepth   = new int[n];
        Arrays.fill(virtualDepth, -1);   // -1 = currently in full-height display mode

        maxArrayValue = 0;
        for (int v : array) if (v > maxArrayValue) maxArrayValue = v;

        // ── Phase 1: shrink all bars and slide them into row 0 ──
        addRepositionStep(0, n - 1, 0);

        // ── Phase 2: recursive divide + merge ──
        mergeSortHelper(0, n - 1, 0);

        // ── Phase 3: expand bars back to full-height and colour green ──
        addRepositionStep(0, n - 1, -1);
        for (int i = 0; i < n; i++) addColorStep(i, Color.LIMEGREEN);

        togglePlayPause();
    }

    /**
     * Recursively builds steps for the depth-row tree.
     *
     * @param depth  current row in the tree (0 = root row at the top)
     */
    private void mergeSortHelper(int low, int high, int depth) {
        if (low >= high) {
            if (low == high) {
                addColorStep(low, Color.LIMEGREEN);
                addSingleRepositionStep(low, depth);    // leaf rises to its own row
            }
            return;
        }

        int mid = (low + high) / 2;

        // Flash the subarray so the viewer sees the divide happening
        for (int i = low; i <= high; i++) addColorStep(i, Color.YELLOW);
        for (int i = low; i <= high; i++) addColorStep(i, Color.CYAN);

        // Divide: both halves drop one row lower
        addRepositionStep(low,      mid,  depth + 1);
        addRepositionStep(mid + 1, high, depth + 1);

        mergeSortHelper(low,      mid,  depth + 1);
        mergeSortHelper(mid + 1, high, depth + 1);

        // Merge: each element rises to `depth` one by one as it's sorted
        merge(low, mid, high, depth);
        // ↑ no separate addRepositionStep here anymore — merge() does it per element
    }

    private void merge(int low, int mid, int high, int depth) {
        for (int i = low;      i <= mid;  i++) addColorStep(i, Color.CYAN);
        for (int i = mid + 1; i <= high; i++) addColorStep(i, Color.MAGENTA);

        int left         = low;
        int currentMid   = mid;
        int right        = mid + 1;
        int currentDepth = depth + 1;   // elements are sitting one level below target

        while (left <= currentMid && right <= high) {
            addColorStep(left,  Color.YELLOW);
            addColorStep(right, Color.YELLOW);

            if (tempArray[left] <= tempArray[right]) {
                // Left element wins: already in place, just rise straight up
                addColorStep(left, Color.LIMEGREEN);
                addSingleRepositionStep(left, depth);
                left++;
            } else {
                // Right element wins: diagonal arc up-left + displace middle bars right
                // (no separate RED/addInstantInsertStep needed — this does it all in one)
                addMergeRotateAndRiseStep(right, left, currentDepth, depth);
                currentMid++;
                right++;
                left++;
            }
        }

        // Remaining left-half: already in place, rise straight up
        while (left <= currentMid) {
            addColorStep(left, Color.LIMEGREEN);
            addSingleRepositionStep(left, depth);
            left++;
        }

        // Remaining right-half: already in place, rise straight up
        while (right <= high) {
            addColorStep(right, Color.LIMEGREEN);
            addSingleRepositionStep(right, depth);
            right++;
        }
    }
// =======================================================
// REPOSITION HELPERS
// =======================================================

    /**
     * Records a step that smoothly animates bars [low..high] to the target depth row.
     *
     *  depth  <  0  →  full-height display (the normal pre/post-sort view)
     *  depth  >= 0  →  a compact row in the merge tree
     *                  row 0 is near the top, deeper rows go further down
     */
    /** Records a step that moves a single bar to a target depth row. */

    /**
     * When the right-half element wins the comparison:
     * - It rotates into position AND rises to the parent row in one diagonal arc.
     * - The displaced bars [toIdx..fromIdx-1] slide right simultaneously.
     */
    private void addMergeRotateAndRiseStep(int fromIdx, int toIdx, int currentDepth, int targetDepth) {
        // ── Advance tempArray ──
        int temp = tempArray[fromIdx];
        for (int k = fromIdx; k > toIdx; k--) tempArray[k] = tempArray[k - 1];
        tempArray[toIdx] = temp;

        // ── Advance virtualDepth for the winner ──
        virtualDepth[toIdx] = targetDepth;   // winner rises; shifted bars stay at currentDepth

        // ── Snapshot virtualColors before this step (needed for backward) ──
        Color[] oldColors = virtualColors.clone();
        // Rotate virtualColors to match bars moving right
        for (int k = fromIdx; k > toIdx; k--) virtualColors[k] = virtualColors[k - 1];
        virtualColors[toIdx] = Color.LIMEGREEN;

        stepQueue.add(new SortStep() {
            @Override
            public void forward() {
                // 1. Rotate bars[] and array[] in sync
                Rectangle winner = bars[fromIdx];
                int winnerVal    = array[fromIdx];
                for (int k = fromIdx; k > toIdx; k--) {
                    bars[k]  = bars[k - 1];
                    array[k] = array[k - 1];
                }
                bars[toIdx]  = winner;
                array[toIdx] = winnerVal;

                // 2. Build one Timeline — winner goes diagonal, others slide right
                double paneW = displayPane.getWidth()  > 0 ? displayPane.getWidth()  : 600;
                double paneH = displayPane.getHeight() > 0 ? displayPane.getHeight() : 400;
                double slotW = paneW / array.length;
                double rowH  = paneH / (maxSortDepth + 2.0);

                // Winner: arc diagonally up-left to (toIdx, targetDepth)
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
                // 1. Reverse-rotate bars[] and array[]
                Rectangle winner = bars[toIdx];
                int winnerVal    = array[toIdx];
                for (int k = toIdx; k < fromIdx; k++) {
                    bars[k]  = bars[k + 1];
                    array[k] = array[k + 1];
                }
                bars[fromIdx]  = winner;
                array[fromIdx] = winnerVal;

                // 2. Restore depth and color tracking
                virtualDepth[toIdx] = currentDepth;
                System.arraycopy(oldColors, 0, virtualColors, 0, oldColors.length);

                // 3. Animate winner back down-right, shifted bars back left
                double paneW = displayPane.getWidth()  > 0 ? displayPane.getWidth()  : 600;
                double paneH = displayPane.getHeight() > 0 ? displayPane.getHeight() : 400;
                double slotW = paneW / array.length;
                double rowH  = paneH / (maxSortDepth + 2.0);

                // Winner returns to fromIdx at currentDepth
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

                // Restore colors only — positions were never moved
                for (int k = toIdx; k < fromIdx; k++) {
                    bars[k].setFill(oldColors[k]);
                }

                if (speedSlider != null) tl.rateProperty().bind(speedSlider.valueProperty());
                tl.play();
            }
        });
    }

    private void addSingleRepositionStep(int idx, int targetDepth) {
        int prevDepth = virtualDepth[idx];
        virtualDepth[idx] = targetDepth;

        stepQueue.add(new SortStep() {
            @Override public void forward()  { playSingleRepositionAnim(idx, targetDepth); }
            @Override public void backward() { playSingleRepositionAnim(idx, prevDepth);   }
        });
    }

    private void playSingleRepositionAnim(int idx, int depth) {
        double paneW = displayPane.getWidth()  > 0 ? displayPane.getWidth()  : 600;
        double paneH = displayPane.getHeight() > 0 ? displayPane.getHeight() : 400;
        double slotW = paneW / array.length;
        double rowH  = paneH / (maxSortDepth + 2.0);

        double barH, barY, barX;
        barX = idx * slotW;   // ← always land in the correct sorted column

        if (depth < 0) {
            barH = (array[idx] / (double) maxArrayValue) * paneH * 0.9;
            barY = paneH - barH;
        } else {
            barH = (array[idx] / (double) maxArrayValue) * rowH * 0.85;
            barY = (depth + 1) * rowH - barH;
        }

        Timeline tl = new Timeline(new KeyFrame(Duration.millis(400),
                new KeyValue(bars[idx].xProperty(),      barX, Interpolator.EASE_BOTH),  // ← X corrected
                new KeyValue(bars[idx].yProperty(),      barY, Interpolator.EASE_BOTH),
                new KeyValue(bars[idx].heightProperty(), barH, Interpolator.EASE_BOTH)
        ));
        if (speedSlider != null) tl.rateProperty().bind(speedSlider.valueProperty());
        tl.play();
    }

    private void addRepositionStep(int low, int high, int targetDepth) {
        int   count      = high - low + 1;
        int[] indices    = new int[count];
        int[] prevDepths = new int[count];

        for (int i = low; i <= high; i++) {
            int k      = i - low;
            indices[k] = i;
            prevDepths[k] = virtualDepth[i];
            virtualDepth[i] = targetDepth;
        }

        // Capture for lambdas
        int[] targetDepths = makeUniform(count, targetDepth);

        stepQueue.add(new SortStep() {
            @Override public void forward()  { playRepositionAnim(indices, targetDepths); }
            @Override public void backward() { playRepositionAnim(indices, prevDepths);   }
        });
    }

    /** Convenience: fills an int[] of given length with one value. */
    private int[] makeUniform(int length, int value) {
        int[] arr = new int[length];
        Arrays.fill(arr, value);
        return arr;
    }

    /**
     * Fires a Timeline that slides each bar[indices[k]] to the Y/height
     * dictated by depths[k].
     *
     * Called at PLAYBACK time, so array[idx] always reflects the live sorted
     * state of the display (executeSwap keeps array[] in sync).
     */
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
            barX = idx * slotW;   // ← always snap to correct column

            if (depth < 0) {
                barH = (array[idx] / (double) maxArrayValue) * paneH * 0.9;
                barY = paneH - barH;
            } else {
                barH = (array[idx] / (double) maxArrayValue) * rowH * 0.85;
                barY = (depth + 1) * rowH - barH;
            }

            tl.getKeyFrames().add(new KeyFrame(Duration.millis(500),
                    new KeyValue(bars[idx].xProperty(),      barX, Interpolator.EASE_BOTH),  // ← X corrected
                    new KeyValue(bars[idx].yProperty(),      barY, Interpolator.EASE_BOTH),
                    new KeyValue(bars[idx].heightProperty(), barH, Interpolator.EASE_BOTH)
            ));
        }

        if (speedSlider != null) tl.rateProperty().bind(speedSlider.valueProperty());
        tl.play();
    }
    //run run
    // =======================================================

    @FXML
    void backToHome(ActionEvent event) throws IOException {
        stopAll();
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("hello-view.fxml"));
        Parent root = fxmlLoader.load();
        Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
        stage.getScene().setRoot(root);
    }
}