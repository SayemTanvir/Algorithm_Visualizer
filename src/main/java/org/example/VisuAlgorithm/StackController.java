package org.example.VisuAlgorithm;

import java.util.Random;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

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
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;
import org.jcodec.api.awt.AWTSequenceEncoder;

public class StackController {

    @FXML private Pane canvas;
    @FXML private TextField valueField;
    @FXML private Label statusLabel;
    @FXML private Label headerStatusLabel;
    @FXML private Label topLabel;

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

    private Rectangle animatedBox;
    private Text animatedText;
    private static final int MAX_SIZE = 5;
    private boolean popAnimationRunning = false;
    private boolean pushAnimationRunning = false;
    private final ArrayList<Integer> stack = new ArrayList<>();

    private final double boxW = 120;
    private final double boxH = 55;
    private final double x = 320;
    private final double startY = 400;
    private final double gap = 80;
    private Timeline currentTimeline;
    private boolean isPaused = false;
    private int currentStepIndex = 0;
    private final Random random = new Random();

    @FXML
    public void initialize() {
        screenshotBtn.setText("📷 Snapshot");
        recordBtn.setText("🎥 Record");

        screenshotBtn.setPrefWidth(130);
        screenshotBtn.setMinWidth(130);

        recordBtn.setPrefWidth(130);
        recordBtn.setMinWidth(130);

        redraw(-1, -1);
    }

    private int getRandomValue() {
        return random.nextInt(90) + 10; // 10–99 (clean UI numbers)
    }
    @FXML
    private void onPush() {
        onPush(false); // Default behavior: not random
    }
    @FXML
    private void onRandom() {
        if (pushAnimationRunning || popAnimationRunning) return;

        stack.clear();
        redraw(-1, -1); // Clears the canvas but keeps the container

        Timeline timeline = new Timeline();
        for (int i = 0; i < MAX_SIZE; i++) {
            // We space the starts by 1.2 seconds so the previous
            // box has time to finish its 'drop' before the next starts.
            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(i * 1.2), e -> onPush(true))
            );
        }
        timeline.play();
    }
    private void showPopup(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        // FIX: Set the owner window to prevent macOS full-screen issues
        if (canvas != null && canvas.getScene() != null) {
            Stage stage = (Stage) canvas.getScene().getWindow();
            alert.initOwner(stage);
        }

        DialogPane dialogPane = alert.getDialogPane();
        try {
            // Link to your existing main.css
            dialogPane.getStylesheets().add(
                    getClass().getResource("/org/example/VisuAlgorithm/styles/main.css").toExternalForm()
            );
            // Apply the CSS class we just created
            dialogPane.getStyleClass().add("custom-alert");
        } catch (Exception e) {
            System.out.println("Could not load CSS for Alert: " + e.getMessage());
        }

        alert.showAndWait();
    }

    @FXML
    private void onBack() {
        if (isRecording) stopRecording();
        Launcher.switchScene("hello-view.fxml");
    }

    private void goTo(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            Stage stage = (Stage) canvas.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/org/example/VisuAlgorithm/styles/main.css").toExternalForm()); // <--- ADD THIS!
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            setStatus("Navigation failed: " + e.getMessage());
        }
    }

    @FXML
    private void onPush(boolean onRandom) {
        if (pushAnimationRunning || popAnimationRunning) return;

        if (stack.size() >= MAX_SIZE) {
            setStatus("Stack overflow");
            showPopup("Stack Overflow", "Maximum 5 elements allowed in the stack.");
            return;
        }

        Integer value;
        if(onRandom) {
            value=getRandomValue();
        }
        else {
            value = parseInt(valueField.getText());
        }
        if (value == null) return;

        playPushIncomingAnimation(value);
    }

    @FXML
    private void onPop() {
        if (pushAnimationRunning || popAnimationRunning) return;

        if (stack.isEmpty()) {
            setStatus("Stack underflow");
            showPopup("Stack Empty", "Cannot pop because the stack is empty.");
            return;
        }

        playPopOutgoingAnimation();
    }

    @FXML
    private void onPeek() {
        if (stack.isEmpty()) {
            setStatus("Stack is empty");
            showPopup("Stack Empty", "Cannot peek because the stack is empty.");
            return;
        }

        int topIndex = stack.size() - 1;
        redraw(topIndex, -1);
        setStatus("Top element: " + stack.get(topIndex));
    }

    @FXML
    private void onSearch() {
        Integer value = parseInt(valueField.getText());
        if (value == null) return;

        if (stack.isEmpty()) {
            setStatus("Stack is empty");
            showPopup("Stack Empty", "Cannot search because the stack is empty.");
            return;
        }

        Timeline timeline = new Timeline();
        int foundIndex = -1;
        int step = 0;

        for (int i = stack.size() - 1; i >= 0; i--) {
            int currentIndex = i;
            int currentStep = step;

            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(currentStep * 0.6), e -> {
                        redraw(-1, currentIndex);
                        setStatus("Checking index " + currentIndex);
                    })
            );

            if (stack.get(i) == value) {
                foundIndex = i;
                break;
            }

            step++;
        }

        if (foundIndex != -1) {
            int finalFound = foundIndex;
            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds((step + 1) * 0.6), e -> {
                        redraw(finalFound, -1);
                        setStatus("Found " + value + " at index " + finalFound);
                    })
            );
        } else {
            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds((step + 1) * 0.6), e -> {
                        redraw(-1, -1);
                        setStatus("Value not found");
                        showPopup("Search Result", "Value not found in stack.");
                    })
            );
        }

        timeline.play();
    }

    @FXML
    private void onTraverse() {
        if (stack.isEmpty()) {
            setStatus("Stack is empty");
            showPopup("Stack Empty", "Cannot traverse because the stack is empty.");
            return;
        }

        Timeline timeline = new Timeline();
        int step = 0;

        for (int i = stack.size() - 1; i >= 0; i--) {
            int currentIndex = i;
            int currentValue = stack.get(i);
            int currentStep = step;

            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(currentStep * 0.5), e -> {
                        redraw(-1, currentIndex);
                        setStatus("Visited " + currentValue + " at index " + currentIndex);
                    })
            );
            step++;
        }

        timeline.getKeyFrames().add(
                new KeyFrame(Duration.seconds(step * 0.5), e -> {
                    redraw(-1, -1);
                    setStatus("Traversal complete");
                })
        );

        timeline.play();
    }

    @FXML
    private void onClear() {
        stack.clear();
        redraw(-1, -1);
        setStatus("Stack cleared");
    }

    private void clearAnimatedBox() {
        if (animatedBox != null) {
            canvas.getChildren().remove(animatedBox);
            animatedBox = null;
        }
        if (animatedText != null) {
            canvas.getChildren().remove(animatedText);
            animatedText = null;
        }
    }

    private void createAnimatedBox(double x, double y, int value, String fillColor, String strokeColor) {
        clearAnimatedBox();

        animatedBox = new Rectangle(x, y, boxW, boxH);
        animatedBox.setArcWidth(18);
        animatedBox.setArcHeight(18);
        animatedBox.setFill(Color.web(fillColor));
        animatedBox.setStroke(Color.web(strokeColor));
        animatedBox.setStrokeWidth(2.5);

        animatedText = makeText(x + 42, y + 35, String.valueOf(value), 20);

        canvas.getChildren().addAll(animatedBox, animatedText);
    }

    private void playPushIncomingAnimation(int value) {
        if (stack.size() >= MAX_SIZE) {
            playOverflowAnimation(value); // Custom overflow animation
            return;
        }

        pushAnimationRunning = true;
        double entryX = x;
        double entryY = 50; // Start high up
        double topOfContainerY = startY - (MAX_SIZE * gap);
        double targetY = startY - stack.size() * gap;

        createAnimatedBox(entryX, entryY, value, "#3b82f6", "#ffffff");

        Timeline gravityDrop = new Timeline(
                // Phase 1: Move to top of container
                new KeyFrame(Duration.ZERO, new KeyValue(animatedBox.yProperty(), entryY)),

                // Phase 2: Drop into position (Gravity effect)
                new KeyFrame(Duration.seconds(0.8), new KeyValue(animatedBox.yProperty(), targetY))
        );

        gravityDrop.setOnFinished(e -> {
            stack.add(value);
            clearAnimatedBox();
            redraw(-1, -1);
            pushAnimationRunning = false;
        });
        gravityDrop.play();
    }

    private void playPopOutgoingAnimation() {
        popAnimationRunning = true;
        int topIdx = stack.size() - 1;
        int val = stack.get(topIdx);
        stack.remove(topIdx);
        redraw(-1, -1); // Redraw without the top element

        createAnimatedBox(x, startY - topIdx * gap, val, "#ef4444", "#ffffff");

        Timeline liftAndFade = new Timeline(
                new KeyFrame(Duration.seconds(0.6),
                        new KeyValue(animatedBox.yProperty(), 50),
                        new KeyValue(animatedBox.opacityProperty(), 0),
                        new KeyValue(animatedText.opacityProperty(), 0)
                )
        );

        liftAndFade.setOnFinished(e -> {
            clearAnimatedBox();
            popAnimationRunning = false;
        });
        liftAndFade.play();
    }

    private void playOverflowAnimation(int value) {
        pushAnimationRunning = true;
        createAnimatedBox(x, 50, value, "#ef4444", "#ffffff");

        Timeline overflow = new Timeline(
                new KeyFrame(Duration.seconds(0.5), new KeyValue(animatedBox.yProperty(), startY - (MAX_SIZE * gap) - 20)),
                new KeyFrame(Duration.seconds(0.8), new KeyValue(animatedBox.xProperty(), x + 150), new KeyValue(animatedBox.opacityProperty(), 0))
        );

        overflow.setOnFinished(e -> {
            clearAnimatedBox();
            pushAnimationRunning = false;
            showPopup("Stack Overflow", "Stack is full!");
        });
        overflow.play();
    }

    private void drawContainer() {
        double containerX = x - 10;
        double containerWidth = boxW + 20;
        double containerHeight = (MAX_SIZE * gap) + 20;
        double containerY = startY - containerHeight + boxH + 10;

        // The Glass Body
        Rectangle body = new Rectangle(containerX, containerY, containerWidth, containerHeight);
        body.setFill(Color.web("#f1f5f9", 0.5)); // Semi-transparent
        body.setStroke(Color.web("#cbd5e1"));
        body.setStrokeWidth(2);
        body.setArcWidth(20);
        body.setArcHeight(20);

        // Left and Right Walls (Visual priority)
        Rectangle leftWall = new Rectangle(containerX, containerY, 5, containerHeight);
        leftWall.setFill(Color.web("#94a3b8"));
        Rectangle rightWall = new Rectangle(containerX + containerWidth - 5, containerY, 5, containerHeight);
        rightWall.setFill(Color.web("#94a3b8"));

        canvas.getChildren().addAll(body, leftWall, rightWall);
    }

    private void redraw(int primaryIndex, int secondaryIndex) {
        canvas.getChildren().clear();
        drawContainer(); // Always draw the bucket first

        if (stack.isEmpty()) {
            Text emptyText = makeText(x + 8, startY - 100, "EMPTY STACK", 18);
            emptyText.setFill(Color.web("#94a3b8"));
            canvas.getChildren().add(emptyText);
            topLabel.setText("-1");
            return;
        }

        String[] colors = {"#3b82f6", "#10b981", "#f59e0b", "#8b5cf6", "#0891B2"};

        for (int i = 0; i < stack.size(); i++) {
            double y = startY - i * gap;
            Rectangle box = new Rectangle(x, y, boxW, boxH);
            box.setArcWidth(10);
            box.setArcHeight(10);
            box.setFill(Color.web(colors[i % colors.length]));
            box.setStroke(Color.WHITE);
            box.setStrokeWidth(2);

            Text val = makeText(x + 45, y + 35, String.valueOf(stack.get(i)), 18);
            val.setFill(Color.WHITE);

            canvas.getChildren().addAll(box, val);
        }
        topLabel.setText(String.valueOf(stack.size() - 1));
    }

    private Text makeText(double x, double y, String value, int size) {
        Text t = new Text(x, y, value);
        t.setFont(Font.font(size));
        t.setFill(Color.web("#1e293b"));
        return t;
    }

    private Integer parseInt(String s) {
        try {
            if (s == null || s.trim().isEmpty()) {
                setStatus("Input empty");
                return null;
            }
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            setStatus("Invalid number");
            return null;
        }
    }

    private void setStatus(String msg) {
        statusLabel.setText(msg);
        headerStatusLabel.setText(msg);
    }

    // ==========================================================================
    // CAPTURE & RECORDING LOGIC (LOCKED RESOLUTION & ANTI-FLOOD)
    // ==========================================================================
    @FXML
    void takeScreenshot() {
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.WHITE); // Set background to white

        WritableImage snapshot = canvas.snapshot(params, null);
        BufferedImage buffered = SwingFXUtils.fromFXImage(snapshot, null);

        String downloadsDir = getDownloadsPath();
        String timestamp    = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File   outputFile   = new File(downloadsDir, "stack_" + timestamp + ".png");

        try {
            ImageIO.write(buffered, "png", outputFile);
            System.out.println("Screenshot saved: " + outputFile.getAbsolutePath());
            setStatus("Screenshot saved! ✓");
            buffered.flush(); // Prevent memory leak here too
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
                "-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-size: 14px;" +
                        "-fx-background-radius: 6; -fx-cursor: hand; -fx-border-color: #991b1b; -fx-border-radius: 6;"
        );

        // LOCK RESOLUTION based on the starting size of the canvas
        SnapshotParameters initParams = new SnapshotParameters();
        initParams.setFill(Color.WHITE);
        WritableImage initSnap = canvas.snapshot(initParams, null);

        final int lockedW = ((int) initSnap.getWidth() % 2 == 0) ? (int) initSnap.getWidth() : (int) initSnap.getWidth() + 1;
        final int lockedH = ((int) initSnap.getHeight() % 2 == 0) ? (int) initSnap.getHeight() : (int) initSnap.getHeight() + 1;

        try {
            String downloadsDir = getDownloadsPath();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File outputFile = new File(downloadsDir, "stack_rec_" + timestamp + ".mp4");

            encoder = AWTSequenceEncoder.createSequenceEncoder(outputFile, RECORD_FPS);
            System.out.println("Recording started... Streaming to: " + outputFile.getAbsolutePath() + " at " + lockedW + "x" + lockedH);
            setStatus("Recording started...");
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

                        g.setColor(java.awt.Color.WHITE); // Fill extra space with white
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
                Platform.runLater(() -> setStatus("Recording saved! ✓"));
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

                    WritableImage fxFrame = canvas.snapshot(params, null);
                    BufferedImage buffered = SwingFXUtils.fromFXImage(fxFrame, null);

                    if (!frameQueue.offer(buffered)) {
                        buffered.flush(); // Queue full, drop frame
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
            recordBtn.setStyle("-fx-background-color: rgba(255,255,255,0.15); -fx-text-fill: white; -fx-font-size: 14px; -fx-background-radius: 6; -fx-cursor: hand; -fx-border-color: rgba(255,255,255,0.25); -fx-border-radius: 6;");
        });
    }

    private String getDownloadsPath() {
        String home = System.getProperty("user.home");
        Path   dl   = Paths.get(home, "Downloads");
        if (!dl.toFile().exists()) dl.toFile().mkdirs();
        return dl.toString();
    }
}