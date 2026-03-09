package org.example.VisuAlgorithm;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.util.ArrayList;

public class StackController {

    @FXML private Pane canvas;
    @FXML private Button backBtn;
    @FXML private TextField valueField;
    @FXML private Label statusLabel;
    @FXML private Label headerStatusLabel;
    @FXML private Label topLabel;

    private final ArrayList<Integer> stack = new ArrayList<>();

    private final double boxW = 120;
    private final double boxH = 50;
    private final double x = 300;
    private final double startY = 700;
    private final double gap = 65;

    @FXML
    public void initialize() {
        redraw();
        setStatus("Ready.");
    }

    @FXML
    private void onBack() {
        goTo("hello-view.fxml"); // change if your menu fxml name is different
    }

    private void goTo(String fxmlName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlName));
            Parent root = loader.load();
            Stage stage = (Stage) canvas.getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (Exception e) {
            setStatus("Navigation failed: " + e.getMessage());
        }
    }

    @FXML
    private void onPush() {
        Integer value = parseInt(valueField.getText());
        if (value == null) return;

        stack.add(value);
        redraw();
        setStatus("Pushed: " + value);
    }

    @FXML
    private void onPop() {
        if (stack.isEmpty()) {
            setStatus("Stack underflow.");
            return;
        }

        int removed = stack.remove(stack.size() - 1);
        redraw();
        setStatus("Popped: " + removed);
    }

    @FXML
    private void onPeek() {
        if (stack.isEmpty()) {
            setStatus("Stack is empty.");
            return;
        }

        int top = stack.get(stack.size() - 1);
        setStatus("Top element: " + top);
    }

    @FXML
    private void onClear() {
        stack.clear();
        redraw();
        setStatus("Stack cleared.");
    }

    private void redraw() {
        canvas.getChildren().clear();

        for (int i = 0; i < stack.size(); i++) {
            double y = startY - i * gap;

            Rectangle box = new Rectangle(x, y, boxW, boxH);
            box.setArcWidth(14);
            box.setArcHeight(14);

            Text valueText = new Text(x + 45, y + 30, String.valueOf(stack.get(i)));
            Text indexText = new Text(x - 45, y + 30, "[" + i + "]");

            canvas.getChildren().addAll(box, valueText, indexText);

            if (i == stack.size() - 1) {
                Text topText = new Text(x + boxW + 20, y + 30, "TOP");
                canvas.getChildren().add(topText);
            }
        }

        if (stack.isEmpty()) {
            Text emptyText = new Text(300, 200, "Stack is empty");
            canvas.getChildren().add(emptyText);
            topLabel.setText("-1");
        } else {
            topLabel.setText(String.valueOf(stack.size() - 1));
        }
    }

    private Integer parseInt(String s) {
        try {
            if (s == null) return null;
            s = s.trim();
            if (s.isEmpty()) {
                setStatus("Input empty.");
                return null;
            }
            return Integer.parseInt(s);
        } catch (Exception e) {
            setStatus("Invalid number: " + s);
            return null;
        }
    }

    private void setStatus(String msg) {
        statusLabel.setText(msg);
        headerStatusLabel.setText(msg);
    }
}