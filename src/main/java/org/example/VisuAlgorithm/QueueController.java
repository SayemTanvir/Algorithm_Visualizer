package org.example.VisuAlgorithm;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class QueueController {

    // ---------- UI ----------
    @FXML private Pane canvas;
    @FXML private Button backBtn;

    @FXML private ToggleGroup modeGroup;
    @FXML private RadioButton rbQueue;
    @FXML private RadioButton rbDeque;
    @FXML private RadioButton rbPriority;
    @FXML private RadioButton rbCircular;

    @FXML private ToggleGroup priorityTypeGroup;
    @FXML private RadioButton rbMaxPriority;
    @FXML private RadioButton rbMinPriority;

    @FXML private TextField valueField;
    @FXML private TextField priorityField;
    @FXML private TextField capacityField;

    @FXML private Label priorityLabel;
    @FXML private Label capacityLabel;
    @FXML private Label statusLabel;
    @FXML private Label headerStatusLabel;
    @FXML private Label modeHintLabel;
    @FXML private Label frontLabel;
    @FXML private Label rearLabel;

    @FXML private Button enqueueBtn;
    @FXML private Button dequeueBtn;
    @FXML private Button pushFrontBtn;
    @FXML private Button pushRearBtn;
    @FXML private Button popFrontBtn;
    @FXML private Button popRearBtn;
    @FXML private Button peekBtn;
    @FXML private Button setCapacityBtn;

    @FXML private HBox priorityTypeBox;

    // ---------- Modes ----------
    private enum Mode { QUEUE, DEQUE, PRIORITY_QUEUE, CIRCULAR_QUEUE }
    private Mode mode = Mode.QUEUE;

    // ---------- Data ----------
    private final ArrayList<Integer> dequeData = new ArrayList<>();

    private static class PQNode {
        int value;
        int priority;

        PQNode(int value, int priority) {
            this.value = value;
            this.priority = priority;
        }
    }

    private final ArrayList<PQNode> priorityData = new ArrayList<>();

    // circular queue
    private int[] circularArray = new int[0];
    private int circularCapacity = 0;
    private int front = -1;
    private int rear = -1;
    private int circularSize = 0;

    // priority mode
    private boolean isMaxPriority = true;

    // layout
    private final double startX = 90;
    private final double y = 250;
    private final double boxW = 120;
    private final double boxH = 64;
    private final double gap = 150;

    @FXML
    public void initialize() {
        setMode(Mode.QUEUE);
        redraw();
        setStatus("Ready.");
    }

    // ================= BACK =================
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

    // ================= MODE =================
    @FXML
    private void onModeChanged() {
        if (rbQueue.isSelected()) setMode(Mode.QUEUE);
        else if (rbDeque.isSelected()) setMode(Mode.DEQUE);
        else if (rbPriority.isSelected()) setMode(Mode.PRIORITY_QUEUE);
        else setMode(Mode.CIRCULAR_QUEUE);
    }
    private void updateCanvasSize() {
        if (mode == Mode.CIRCULAR_QUEUE) {
            canvas.setPrefWidth(1400);
            canvas.setPrefHeight(900);
            return;
        }

        int count;
        if (mode == Mode.PRIORITY_QUEUE) {
            count = priorityData.size();
        } else {
            count = dequeData.size();
        }

        double neededWidth = Math.max(1400, startX + count * gap + 300);
        double neededHeight = 900;

        canvas.setPrefWidth(neededWidth);
        canvas.setPrefHeight(neededHeight);
    }
    private void setMode(Mode m) {
        mode = m;

        boolean priorityMode = mode == Mode.PRIORITY_QUEUE;
        boolean dequeMode = mode == Mode.DEQUE;
        boolean circularMode = mode == Mode.CIRCULAR_QUEUE;

        show(priorityLabel, priorityMode);
        show(priorityField, priorityMode);
        show(priorityTypeBox, priorityMode);

        show(capacityLabel, circularMode);
        show(capacityField, circularMode);
        show(setCapacityBtn, circularMode);

        show(enqueueBtn, !dequeMode);
        show(dequeueBtn, !dequeMode);

        show(pushFrontBtn, dequeMode);
        show(pushRearBtn, dequeMode);
        show(popFrontBtn, dequeMode);
        show(popRearBtn, dequeMode);

        if (mode == Mode.QUEUE) {
            peekBtn.setText("Front");
            modeHintLabel.setText("Queue: insert at rear, remove from front.");
        } else if (mode == Mode.DEQUE) {
            peekBtn.setText("Front");
            modeHintLabel.setText("Deque: you can push/pop from both front and rear.");
        } else if (mode == Mode.PRIORITY_QUEUE) {
            peekBtn.setText("Peek");
            modeHintLabel.setText("Priority Queue: element order depends on priority.");
        } else {
            peekBtn.setText("Front");
            modeHintLabel.setText("Circular Queue: fixed-size queue using wrap-around indexing.");
        }

        redraw();
        setStatus("Mode set to " + modeToText());
    }

    @FXML
    private void onPriorityTypeChanged() {
        isMaxPriority = rbMaxPriority.isSelected();
        sortPriorityQueue();
        redraw();
        setStatus("Priority type: " + (isMaxPriority ? "Max" : "Min"));
    }

    private String modeToText() {
        if (mode == Mode.QUEUE) return "Queue";
        if (mode == Mode.DEQUE) return "Deque";
        if (mode == Mode.PRIORITY_QUEUE) return "Priority Queue";
        return "Circular Queue";
    }

    private void show(Control c, boolean visible) {
        c.setVisible(visible);
        c.setManaged(visible);
    }

    private void show(HBox c, boolean visible) {
        c.setVisible(visible);
        c.setManaged(visible);
    }

    // ================= NORMAL QUEUE =================
    @FXML
    private void onEnqueue() {
        if (mode == Mode.PRIORITY_QUEUE) {
            enqueuePriority();
            return;
        }
        if (mode == Mode.CIRCULAR_QUEUE) {
            enqueueCircular();
            return;
        }

        Integer value = parseInt(valueField.getText());
        if (value == null) return;

        dequeData.add(value);
        redraw();
        setStatus("Enqueued: " + value);
    }

    @FXML
    private void onDequeue() {
        if (mode == Mode.PRIORITY_QUEUE) {
            dequeuePriority();
            return;
        }
        if (mode == Mode.CIRCULAR_QUEUE) {
            dequeueCircular();
            return;
        }

        if (dequeData.isEmpty()) {
            setStatus("Queue is empty.");
            return;
        }

        int removed = dequeData.remove(0);
        redraw();
        setStatus("Dequeued: " + removed);
    }

    @FXML
    private void onFront() {
        if (mode == Mode.PRIORITY_QUEUE) {
            if (priorityData.isEmpty()) {
                setStatus("Priority Queue is empty.");
                return;
            }
            PQNode first = priorityData.get(0);
            setStatus("Peek: value " + first.value + ", priority " + first.priority);
            return;
        }

        if (mode == Mode.CIRCULAR_QUEUE) {
            if (circularSize == 0) {
                setStatus("Circular Queue is empty.");
                return;
            }
            setStatus("Front element: " + circularArray[front]);
            return;
        }

        if (dequeData.isEmpty()) {
            setStatus("Structure is empty.");
            return;
        }

        setStatus("Front element: " + dequeData.get(0));
    }

    // ================= DEQUE =================
    @FXML
    private void onPushFront() {
        Integer value = parseInt(valueField.getText());
        if (value == null) return;

        dequeData.add(0, value);
        redraw();
        setStatus("Pushed front: " + value);
    }

    @FXML
    private void onPushRear() {
        Integer value = parseInt(valueField.getText());
        if (value == null) return;

        dequeData.add(value);
        redraw();
        setStatus("Pushed rear: " + value);
    }

    @FXML
    private void onPopFront() {
        if (dequeData.isEmpty()) {
            setStatus("Deque is empty.");
            return;
        }

        int removed = dequeData.remove(0);
        redraw();
        setStatus("Popped front: " + removed);
    }

    @FXML
    private void onPopRear() {
        if (dequeData.isEmpty()) {
            setStatus("Deque is empty.");
            return;
        }

        int removed = dequeData.remove(dequeData.size() - 1);
        redraw();
        setStatus("Popped rear: " + removed);
    }

    // ================= PRIORITY QUEUE =================
    private void enqueuePriority() {
        Integer value = parseInt(valueField.getText());
        Integer priority = parseInt(priorityField.getText());
        if (value == null || priority == null) return;

        priorityData.add(new PQNode(value, priority));
        sortPriorityQueue();
        redraw();
        setStatus("Inserted value " + value + " with priority " + priority);
    }

    private void dequeuePriority() {
        if (priorityData.isEmpty()) {
            setStatus("Priority Queue is empty.");
            return;
        }

        PQNode removed = priorityData.remove(0);
        redraw();
        setStatus("Removed value " + removed.value + " with priority " + removed.priority);
    }

    private void sortPriorityQueue() {
        Collections.sort(priorityData, new Comparator<PQNode>() {
            @Override
            public int compare(PQNode a, PQNode b) {
                if (isMaxPriority) {
                    return Integer.compare(b.priority, a.priority);
                } else {
                    return Integer.compare(a.priority, b.priority);
                }
            }
        });
    }

    // ================= CIRCULAR QUEUE =================
    @FXML
    private void onSetCapacity() {
        Integer cap = parseInt(capacityField.getText());
        if (cap == null) return;

        if (cap <= 0) {
            setStatus("Capacity must be positive.");
            return;
        }

        circularCapacity = cap;
        circularArray = new int[circularCapacity];
        front = -1;
        rear = -1;
        circularSize = 0;

        redraw();
        setStatus("Circular Queue capacity set to " + cap);
    }

    private void enqueueCircular() {
        if (circularCapacity == 0) {
            setStatus("Set capacity first.");
            return;
        }

        if (circularSize == circularCapacity) {
            setStatus("Circular Queue overflow.");
            return;
        }

        Integer value = parseInt(valueField.getText());
        if (value == null) return;

        if (circularSize == 0) {
            front = 0;
            rear = 0;
        } else {
            rear = (rear + 1) % circularCapacity;
        }

        circularArray[rear] = value;
        circularSize++;

        redraw();
        setStatus("Enqueued into circular queue: " + value);
    }

    private void dequeueCircular() {
        if (circularSize == 0) {
            setStatus("Circular Queue underflow.");
            return;
        }

        int removed = circularArray[front];

        if (circularSize == 1) {
            front = -1;
            rear = -1;
        } else {
            front = (front + 1) % circularCapacity;
        }

        circularSize--;
        redraw();
        setStatus("Dequeued from circular queue: " + removed);
    }

    // ================= COMMON =================
    @FXML
    private void onClear() {
        dequeData.clear();
        priorityData.clear();

        front = -1;
        rear = -1;
        circularSize = 0;
        if (circularCapacity > 0) {
            circularArray = new int[circularCapacity];
        }

        redraw();
        setStatus(modeToText() + " cleared.");
    }

    // ================= DRAW =================
    private void redraw() {
        updateCanvasSize();
        canvas.getChildren().clear();

        if (mode == Mode.PRIORITY_QUEUE) {
            drawPriorityQueue();
        } else if (mode == Mode.CIRCULAR_QUEUE) {
            drawCircularQueue();
        } else {
            drawDequeLikeStructure();
        }
    }

    private void drawDequeLikeStructure() {
        for (int i = 0; i < dequeData.size(); i++) {
            double x = startX + i * gap;

            Rectangle box = createCard(x, y, boxW, boxH, "#ffffff", "#93c5fd");

            Text valueText = makeText(x + 40, y + 38, String.valueOf(dequeData.get(i)), 18);
            Text indexText = makeText(x + 36, y - 12, "[" + i + "]", 12);

            canvas.getChildren().addAll(box, valueText, indexText);

            if (i == 0) {
                Text frontText = makeText(x + 24, y + boxH + 26, "FRONT", 13);
                frontText.setFill(Color.web("#0f766e"));
                canvas.getChildren().add(frontText);
            }

            if (i == dequeData.size() - 1) {
                Text rearText = makeText(x + 30, y + boxH + 48, "REAR", 13);
                rearText.setFill(Color.web("#7c2d12"));
                canvas.getChildren().add(rearText);
            }

            if (i < dequeData.size() - 1) {
                Line arrow = new Line(x + boxW, y + boxH / 2.0, x + gap - 20, y + boxH / 2.0);
                arrow.setStroke(Color.web("#64748b"));
                arrow.setStrokeWidth(2.2);
                canvas.getChildren().add(arrow);
            }
        }

        if (dequeData.isEmpty()) {
            Text emptyText = makeText(320, 180, mode == Mode.DEQUE ? "Deque is empty" : "Queue is empty", 22);
            emptyText.setFill(Color.web("#64748b"));
            canvas.getChildren().add(emptyText);
            frontLabel.setText("-1");
            rearLabel.setText("-1");
        } else {
            frontLabel.setText("0");
            rearLabel.setText(String.valueOf(dequeData.size() - 1));
        }
    }

    private void drawPriorityQueue() {
        for (int i = 0; i < priorityData.size(); i++) {
            double x = startX + i * gap;
            PQNode node = priorityData.get(i);

            Rectangle box = createCard(x, y, boxW, boxH + 12, "#ffffff", "#c4b5fd");

            Text valueText = makeText(x + 18, y + 26, "V: " + node.value, 15);
            Text priorityText = makeText(x + 18, y + 50, "P: " + node.priority, 15);
            Text indexText = makeText(x + 34, y - 12, "[" + i + "]", 12);

            canvas.getChildren().addAll(box, valueText, priorityText, indexText);

            if (i == 0) {
                Text topText = makeText(x + 4, y + boxH + 42,
                        isMaxPriority ? "HIGHEST PRIORITY" : "LOWEST PRIORITY", 12);
                topText.setFill(Color.web("#6d28d9"));
                canvas.getChildren().add(topText);
            }

            if (i < priorityData.size() - 1) {
                Line arrow = new Line(x + boxW, y + (boxH / 2.0), x + gap - 20, y + (boxH / 2.0));
                arrow.setStroke(Color.web("#8b5cf6"));
                arrow.setStrokeWidth(2.2);
                canvas.getChildren().add(arrow);
            }
        }

        if (priorityData.isEmpty()) {
            Text emptyText = makeText(300, 180, "Priority Queue is empty", 22);
            emptyText.setFill(Color.web("#64748b"));
            canvas.getChildren().add(emptyText);
            frontLabel.setText("-1");
            rearLabel.setText("-1");
        } else {
            frontLabel.setText("0");
            rearLabel.setText(String.valueOf(priorityData.size() - 1));
        }
    }

    private void drawCircularQueue() {
        double centerX = 650;
        double centerY = 350;
        double radius = 180;

        if (circularCapacity <= 0) {
            Text txt = makeText(260, 180, "Set circular queue capacity first", 22);
            txt.setFill(Color.web("#64748b"));
            canvas.getChildren().add(txt);
            frontLabel.setText("-1");
            rearLabel.setText("-1");
            return;
        }

        for (int i = 0; i < circularCapacity; i++) {
            double angle = 2 * Math.PI * i / circularCapacity - Math.PI / 2;
            double x = centerX + radius * Math.cos(angle);
            double yy = centerY + radius * Math.sin(angle);

            Circle circle = new Circle(x, yy, 36);
            circle.setFill(Color.WHITE);
            circle.setStroke(Color.web("#60a5fa"));
            circle.setStrokeWidth(3);

            String textValue = "-";
            if (isCircularIndexOccupied(i)) {
                textValue = String.valueOf(circularArray[i]);
                circle.setFill(Color.web("#dbeafe"));
            }

            Text valueText = makeText(x - 10, yy + 6, textValue, 16);
            Text idxText = makeText(x - 8, yy - 48, String.valueOf(i), 12);

            canvas.getChildren().addAll(circle, valueText, idxText);

            if (i == front && circularSize > 0) {
                Text f = makeText(x - 18, yy + 62, "FRONT", 12);
                f.setFill(Color.web("#0f766e"));
                canvas.getChildren().add(f);
            }

            if (i == rear && circularSize > 0) {
                Text r = makeText(x - 14, yy + 78, "REAR", 12);
                r.setFill(Color.web("#dc2626"));
                canvas.getChildren().add(r);
            }
        }

        Circle ring = new Circle(centerX, centerY, radius);
        ring.setFill(Color.TRANSPARENT);
        ring.setStroke(Color.web("#cbd5e1"));
        ring.setStrokeWidth(2);
        canvas.getChildren().add(0, ring);

        if (circularSize == 0) {
            Text emptyText = makeText(560, 610, "Circular Queue is empty", 18);
            emptyText.setFill(Color.web("#64748b"));
            canvas.getChildren().add(emptyText);
            frontLabel.setText("-1");
            rearLabel.setText("-1");
        } else {
            frontLabel.setText(String.valueOf(front));
            rearLabel.setText(String.valueOf(rear));
        }
    }

    private boolean isCircularIndexOccupied(int idx) {
        if (circularSize == 0) return false;

        int count = 0;
        int i = front;
        while (count < circularSize) {
            if (i == idx) return true;
            i = (i + 1) % circularCapacity;
            count++;
        }
        return false;
    }

    // ================= DRAW HELPERS =================
    private Rectangle createCard(double x, double y, double w, double h, String fill, String stroke) {
        Rectangle box = new Rectangle(x, y, w, h);
        box.setArcWidth(18);
        box.setArcHeight(18);
        box.setFill(Color.web(fill));
        box.setStroke(Color.web(stroke));
        box.setStrokeWidth(2.4);
        return box;
    }

    private Text makeText(double x, double y, String value, int size) {
        Text t = new Text(x, y, value);
        t.setFont(Font.font(size));
        t.setFill(Color.web("#1e293b"));
        return t;
    }

    // ================= HELPERS =================
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