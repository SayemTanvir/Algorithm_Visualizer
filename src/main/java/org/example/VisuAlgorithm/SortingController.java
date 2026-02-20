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
import java.util.List;
import java.util.Random;

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

    // Master Data
    private int[] array;
    private Rectangle[] bars;

    // Time-Machine Architecture
    private interface SortStep {
        void forward();
        void backward();
    }

    private List<SortStep> stepQueue = new ArrayList<>();
    private int currentStepIndex = 0;
    private Timeline playTimeline;
    private boolean isPlaying = false;

    // --- NEW: VIRTUAL STATE FOR GENERATORS ---
    // These track the "future" state while generating the steps
    private int[] tempArray;
    private Color[] virtualColors;

    @FXML
    public void initialize() {
        sizeSlider.setValue(10);
        updateSizeLabel(10);

        if (speedSlider != null) {
            speedSlider.setMin(0.5);
            speedSlider.setMax(10.0);
            speedSlider.setValue(3.0);
        }

        Platform.runLater(this::generateRandomArray);

        sizeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int size = newVal.intValue();
            updateSizeLabel(size);
            generateRandomArray();
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
        playTimeline = new Timeline(new KeyFrame(Duration.millis(300), e -> executeNextStep()));
        playTimeline.setCycleCount(Timeline.INDEFINITE);
        if (speedSlider != null) {
            playTimeline.rateProperty().bind(speedSlider.valueProperty());
        }
    }

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

    // --- MEDIA / STEP CONTROLS ---

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

    // =======================================================
    // --- STEP GENERATION HELPERS ---
    // =======================================================

    // Sets up the virtual arrays required to generate steps
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

    // Helper 1: Swap Step
    private void addSwapStep(int idx1, int idx2) {
        // 1. Update virtual array for future steps
        int temp = tempArray[idx1];
        tempArray[idx1] = tempArray[idx2];
        tempArray[idx2] = temp;

        // 2. Queue the visual step
        stepQueue.add(new SortStep() {
            @Override public void forward() { executeSwap(idx1, idx2); }
            @Override public void backward() { executeSwap(idx1, idx2); }
        });
    }

    // Helper 2: Single Color Step
    private void addColorStep(int idx, Color newColor) {
        Color oldColor = virtualColors[idx]; // Remember what it WAS
        virtualColors[idx] = newColor;       // Update what it WILL BE

        stepQueue.add(new SortStep() {
            @Override public void forward() { bars[idx].setFill(newColor); }
            @Override public void backward() { bars[idx].setFill(oldColor); }
        });
    }

    // Helper 3: Multi-Color Step (Useful for highlighting 2 bars at once)
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

    // Performs the physical swapping of visual bars and master array
    private void executeSwap(int idx1, int idx2) {
        double h = bars[idx1].getHeight();
        bars[idx1].setHeight(bars[idx2].getHeight());
        bars[idx2].setHeight(h);

        double y = bars[idx1].getY();
        bars[idx1].setY(bars[idx2].getY());
        bars[idx2].setY(y);

        int t = array[idx1];
        array[idx1] = array[idx2];
        array[idx2] = t;
    }

    // =======================================================
    // --- SORTING ALGORITHMS ---
    // =======================================================

    @FXML
    void runBubbleSort() {
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

        togglePlayPause(); // Auto-start playback
    }

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