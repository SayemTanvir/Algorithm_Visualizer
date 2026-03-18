package org.example.VisuAlgorithm;

import javafx.scene.control.Alert;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;

public class StackController {

    @FXML private Pane canvas;
    @FXML private TextField valueField;
    @FXML private Label statusLabel;
    @FXML private Label headerStatusLabel;
    @FXML private Label topLabel;
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

    private void showPopup(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    public void initialize() {
        redraw(-1, -1);
    }

    @FXML
    private void onBack() {
        goTo("hello-view.fxml");
    }

    private void goTo(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            Stage stage = (Stage) canvas.getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (Exception e) {
            setStatus("Navigation failed: " + e.getMessage());
        }
    }

    @FXML
    private void onPush() {
        if (pushAnimationRunning || popAnimationRunning) return;

        if (stack.size() >= MAX_SIZE) {
            setStatus("Stack overflow");
            showPopup("Stack Overflow", "Maximum 5 elements allowed in the stack.");
            return;
        }

        Integer value = parseInt(valueField.getText());
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
            setStatus("Stack overflow");
            showPopup("Stack Overflow", "Maximum 7 elements allowed in the stack.");
            return;
        }

        if (pushAnimationRunning) return;
        pushAnimationRunning = true;

        redraw(-1, -1);

        double startAnimX = 80;
        double startAnimY = 120;

        double targetY = startY - stack.size() * gap;
        double targetX = x;

        createAnimatedBox(startAnimX, startAnimY, value, "#dbeafe", "#2563eb");
        setStatus("Pushing " + value + "...");

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(0.0),
                        new KeyValue(animatedBox.xProperty(), startAnimX),
                        new KeyValue(animatedBox.yProperty(), startAnimY),
                        new KeyValue(animatedText.xProperty(), startAnimX + 42),
                        new KeyValue(animatedText.yProperty(), startAnimY + 35)
                ),
                new KeyFrame(Duration.seconds(0.8),
                        new KeyValue(animatedBox.xProperty(), targetX),
                        new KeyValue(animatedBox.yProperty(), targetY),
                        new KeyValue(animatedText.xProperty(), targetX + 42),
                        new KeyValue(animatedText.yProperty(), targetY + 35)
                )
        );

        timeline.setOnFinished(e -> {
            clearAnimatedBox();
            stack.add(value);
            redraw(stack.size() - 1, -1);
            setStatus("Pushed " + value);
            pushAnimationRunning = false;
        });

        timeline.play();
    }

    private void playPopOutgoingAnimation() {
        if (stack.isEmpty()) {
            setStatus("Stack underflow");
            showPopup("Stack Empty", "Cannot pop because the stack is empty.");
            return;
        }

        if (popAnimationRunning) return;
        popAnimationRunning = true;

        int topIndex = stack.size() - 1;
        int removedValue = stack.get(topIndex);

        redraw(topIndex, -1);
        setStatus("Popping top...");

        double startAnimX = x;
        double startAnimY = startY - topIndex * gap;

        double targetAnimX = 80;
        double targetAnimY = 120;

        createAnimatedBox(startAnimX, startAnimY, removedValue, "#fee2e2", "#dc2626");

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(0.0),
                        new KeyValue(animatedBox.xProperty(), startAnimX),
                        new KeyValue(animatedBox.yProperty(), startAnimY),
                        new KeyValue(animatedText.xProperty(), startAnimX + 42),
                        new KeyValue(animatedText.yProperty(), startAnimY + 35)
                ),
                new KeyFrame(Duration.seconds(0.8),
                        new KeyValue(animatedBox.xProperty(), targetAnimX),
                        new KeyValue(animatedBox.yProperty(), targetAnimY),
                        new KeyValue(animatedText.xProperty(), targetAnimX + 42),
                        new KeyValue(animatedText.yProperty(), targetAnimY + 35)
                )
        );

        timeline.setOnFinished(e -> {
            clearAnimatedBox();

            if (!stack.isEmpty()) {
                stack.remove(stack.size() - 1);
            }

            redraw(-1, -1);
            setStatus("Popped " + removedValue);
            popAnimationRunning = false;
        });

        timeline.play();
    }

    private void redraw(int primaryIndex, int secondaryIndex) {
        canvas.getChildren().clear();

        canvas.setPrefHeight(760);
        canvas.setPrefWidth(1000);
        Text animationZone = makeText(70, 95, "", 14);
        animationZone.setFill(Color.web("#64748b"));
        canvas.getChildren().add(animationZone);
        if (stack.isEmpty()) {
            Text emptyText = makeText(320, 180, "Stack is empty", 24);
            emptyText.setFill(Color.web("#64748b"));
            canvas.getChildren().add(emptyText);
            topLabel.setText("-1");
            return;
        }

        for (int i = 0; i < stack.size(); i++) {
            double y = startY - i * gap;

            String fill = "#ffffff";
            if (i == secondaryIndex) fill = "#93c5fd";
            if (i == primaryIndex) fill = "#fde68a";

            Rectangle box = new Rectangle(x, y, boxW, boxH);
            box.setArcWidth(18);
            box.setArcHeight(18);
            box.setFill(Color.web(fill));
            box.setStroke(Color.web("#60a5fa"));
            box.setStrokeWidth(2.5);

            Text valueText = makeText(x + 42, y + 35, String.valueOf(stack.get(i)), 20);
            Text indexText = makeText(x - 60, y + 33, "[" + i + "]", 13);

            canvas.getChildren().addAll(box, valueText, indexText);

            if (i == stack.size() - 1) {
                Text topText = makeText(x + boxW + 25, y + 32, "TOP", 14);
                topText.setFill(Color.web("#dc2626"));
                canvas.getChildren().add(topText);
            }

            if (i == 0) {
                Text bottomText = makeText(x + boxW + 25, y + 32, "BOTTOM", 14);
                bottomText.setFill(Color.web("#0f766e"));
                canvas.getChildren().add(bottomText);
            }
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
}