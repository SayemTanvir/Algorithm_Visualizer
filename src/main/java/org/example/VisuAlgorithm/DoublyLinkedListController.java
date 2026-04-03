package org.example.VisuAlgorithm;

import java.util.Random;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

public class DoublyLinkedListController {

    @FXML private Pane canvas;
    @FXML private TextField valueField;
    @FXML private TextField indexField;
    @FXML private Label statusLabel;
    @FXML private Label headerStatusLabel;

    private static class Node {
        int data;
        Node prev, next;
        Node(int data) { this.data = data; }
    }

    private Node head;

    private final double startX = 60;
    private final double startY = 250;
    private final double boxH = 50;
    private final double gap = 180;
    private final Random random = new Random();
    private Timeline currentTimeline;
    private boolean isPaused = false;
    private int currentStepIndex = 0;
    private int getRandomValue() {
        return random.nextInt(90) + 10; // 10–99 (clean UI numbers)
    }
    @FXML
    private void onRandom() {
        head = null; // or stack.clear()
        int count = random.nextInt(5) + 3;

        Timeline timeline = new Timeline();

        for (int i = 0; i < count; i++) {
            int value = getRandomValue();
            int step = i;

            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(step * 0.6), e -> {

                        Node node = new Node(value);

                        if (head == null) {
                            head = node;
                        } else {
                            Node temp = head;
                            while (temp.next != null) temp = temp.next;

                            temp.next = node;
                            node.prev = temp;
                        }

                        redraw(-1, -1);
                        setStatus("Inserted random: " + value);
                    })
            );
        }

        timeline.play();
    }
    @FXML
    public void initialize() {
        redraw(-1, -1);
    }

    @FXML
    private void onBack() {
        Launcher.switchScene("linked-list-view.fxml");
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
    private void onInsertHead() {
        Integer value = parseInt(valueField.getText());
        if (value == null) return;

        Node node = new Node(value);
        node.next = head;
        if (head != null) head.prev = node;
        head = node;

        redraw(0, -1);
        setStatus("Inserted at head");
    }

    @FXML
    private void onInsertTail() {
        Integer value = parseInt(valueField.getText());
        if (value == null) return;

        Node node = new Node(value);

        if (head == null) {
            head = node;
            redraw(0, -1);
            setStatus("Inserted at tail");
            return;
        }

        Node temp = head;
        while (temp.next != null) temp = temp.next;
        temp.next = node;
        node.prev = temp;

        redraw(getSize() - 1, -1);
        setStatus("Inserted at tail");
    }

    @FXML
    private void onInsertAt() {
        Integer value = parseInt(valueField.getText());
        Integer index = parseInt(indexField.getText());
        if (value == null || index == null) return;

        if (index < 0 || index > getSize()) {
            setStatus("Index out of range");
            showPopup("Index Out of Range", "Insertion index is outside the valid range.");
            return;
        }

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(0.0), e -> {
                    redraw(-1, Math.max(0, index - 1));
                    setStatus("Locating insertion point...");
                }),
                new KeyFrame(Duration.seconds(0.7), e -> {
                    insertAtLogic(index, value);
                    redraw(index, -1);
                    setStatus("Inserted at index " + index);
                })
        );
        timeline.play();
    }

    private void insertAtLogic(int index, int value) {
        if (index == 0) {
            Node node = new Node(value);
            node.next = head;
            if (head != null) head.prev = node;
            head = node;
            return;
        }

        Node temp = head;
        int i = 0;
        while (temp != null && i < index - 1) {
            temp = temp.next;
            i++;
        }

        if (temp == null) return;

        Node node = new Node(value);
        node.next = temp.next;
        node.prev = temp;

        if (temp.next != null) temp.next.prev = node;
        temp.next = node;
    }

    @FXML
    private void onDeleteHead() {
        if (head == null) {
            setStatus("List is empty");
            showPopup("Empty List", "Cannot delete head because the list is empty.");
            return;
        }

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(0.0), e -> {
                    redraw(0, -1);
                    setStatus("Deleting head...");
                }),
                new KeyFrame(Duration.seconds(0.6), e -> {
                    int removed = head.data;
                    head = head.next;
                    if (head != null) head.prev = null;
                    redraw(-1, -1);
                    setStatus("Deleted head: " + removed);
                })
        );
        timeline.play();
    }

    @FXML
    private void onDeleteTail() {
        if (head == null) {
            setStatus("List is empty");
            showPopup("Empty List", "Cannot delete tail because the list is empty.");
            return;
        }
        playDeleteAnimation(getSize() - 1);
    }

    @FXML
    private void onDeleteAt() {
        Integer index = parseInt(indexField.getText());
        if (index == null) return;

        if (index < 0 || index >= getSize()) {
            setStatus("Index out of range");
            showPopup("Index Out of Range", "Delete index is outside the valid range.");
            return;
        }

        playDeleteAnimation(index);
    }

    private void playDeleteAnimation(int index) {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(0.0), e -> {
                    redraw(index, -1);
                    setStatus("Deleting index " + index);
                }),
                new KeyFrame(Duration.seconds(0.7), e -> {
                    deleteAtLogic(index);
                    redraw(-1, -1);
                    setStatus("Deleted index " + index);
                })
        );
        timeline.play();
    }

    private void deleteAtLogic(int index) {
        if (head == null) return;

        if (index == 0) {
            head = head.next;
            if (head != null) head.prev = null;
            return;
        }

        Node temp = head;
        int i = 0;
        while (temp != null && i < index) {
            temp = temp.next;
            i++;
        }

        if (temp == null) return;

        if (temp.next != null) temp.next.prev = temp.prev;
        temp.prev.next = temp.next;
    }

    @FXML
    private void onSearch() {
        Integer value = parseInt(valueField.getText());
        if (value == null) return;

        if (head == null) {
            setStatus("List is empty");
            showPopup("Empty List", "Cannot search because the list is empty.");
            return;
        }

        Timeline timeline = new Timeline();
        Node temp = head;
        int index = 0;
        int found = -1;

        while (temp != null) {
            int current = index;
            int currentValue = temp.data;

            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(index * 0.6), e -> {
                        redraw(-1, current);
                        setStatus("Checking index " + current);
                    })
            );

            if (currentValue == value) {
                found = index;
                break;
            }

            temp = temp.next;
            index++;
        }

        if (found != -1) {
            int finalFound = found;
            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds((found + 1) * 0.6), e -> {
                        redraw(finalFound, -1);
                        setStatus("Found at index " + finalFound);
                    })
            );
        } else {
            int end = Math.max(1, getSize());
            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(end * 0.6), e -> {
                        redraw(-1, -1);
                        setStatus("Value not found");
                        showPopup("Search Result", "Value not found in the doubly linked list.");
                    })
            );
        }

        timeline.play();
    }

    @FXML
    private void onTraverse() {
        if (head == null) {
            setStatus("List is empty");
            showPopup("Empty List", "Cannot traverse because the list is empty.");
            return;
        }

        Timeline timeline = new Timeline();
        Node temp = head;
        int index = 0;

        while (temp != null) {
            int current = index;
            int value = temp.data;

            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(index * 0.5), e -> {
                        redraw(-1, current);
                        setStatus("Visited node " + value + " at index " + current);
                    })
            );

            temp = temp.next;
            index++;
        }

        timeline.getKeyFrames().add(
                new KeyFrame(Duration.seconds(index * 0.5), e -> {
                    redraw(-1, -1);
                    setStatus("Traversal complete");
                })
        );

        timeline.play();
    }

    @FXML
    private void onSort() {
        if (head == null || head.next == null) {
            setStatus("Nothing to sort");
            return;
        }

        Timeline timeline = new Timeline();
        double time = 0.0;
        int n = getSize();

        for (int i = 0; i < n - 1; i++) {
            Node a = head;
            int index = 0;

            while (a != null && a.next != null) {
                Node b = a.next;
                int idxA = index;
                int idxB = index + 1;

                timeline.getKeyFrames().add(
                        new KeyFrame(Duration.seconds(time), e -> {
                            redraw(idxA, idxB);
                            setStatus("Comparing index " + idxA + " and " + idxB);
                        })
                );
                time += 0.5;

                if (a.data > b.data) {
                    int temp = a.data;
                    a.data = b.data;
                    b.data = temp;

                    timeline.getKeyFrames().add(
                            new KeyFrame(Duration.seconds(time), e -> {
                                redraw(idxA, idxB);
                                setStatus("Swapped index " + idxA + " and " + idxB);
                            })
                    );
                    time += 0.5;
                }

                a = a.next;
                index++;
            }
        }

        timeline.getKeyFrames().add(
                new KeyFrame(Duration.seconds(time), e -> {
                    redraw(-1, -1);
                    setStatus("Sorting complete");
                })
        );

        timeline.play();
    }

    @FXML
    private void onClear() {
        head = null;
        redraw(-1, -1);
        setStatus("List cleared");
    }

    private void redraw(int primaryIndex, int secondaryIndex) {
        canvas.getChildren().clear();

        int size = getSize();
        canvas.setPrefWidth(Math.max(1800, startX + size * gap + 300));
        canvas.setPrefHeight(720);

        if (head == null) {
            canvas.getChildren().add(makeText(250, 180, "Doubly Linked List is empty", 22));
            return;
        }

        Node temp = head;
        int index = 0;

        while (temp != null) {
            double x = startX + index * gap;
            double y = startY;

            String fill = "#ffffff";
            if (index == secondaryIndex) fill = "#93c5fd";
            if (index == primaryIndex) fill = "#fde68a";

            Rectangle prevBox = makeBox(x, y, 40, boxH, "#ffffff");
            Rectangle dataBox = makeBox(x + 40, y, 80, boxH, fill);
            Rectangle nextBox = makeBox(x + 120, y, 40, boxH, "#ffffff");

            Text dataText = makeText(x + 68, y + 32, String.valueOf(temp.data), 18);
            Text idxText = makeText(x + 65, y - 12, "[" + index + "]", 12);

            canvas.getChildren().addAll(prevBox, dataBox, nextBox, dataText, idxText);

            if (index == 0) {
                Text headText = makeText(x + 52, y - 42, "HEAD", 14);
                headText.setFill(Color.web("#0f766e"));
                canvas.getChildren().add(headText);
            }

            if (temp.next != null) {
                Line forward = new Line(x + 160, y + 18, x + gap, y + 18);
                forward.setStroke(Color.web("#2563eb"));
                forward.setStrokeWidth(2.2);

                Line backward = new Line(x + gap, y + 34, x + 160, y + 34);
                backward.setStroke(Color.web("#dc2626"));
                backward.setStrokeWidth(2.2);

                canvas.getChildren().addAll(forward, backward,
                        createArrowHead(x + gap, y + 18, true),
                        createArrowHead(x + 160, y + 34, false));
            } else {
                Text nullText = makeText(x + 168, y + 30, "NULL", 14);
                nullText.setFill(Color.web("#dc2626"));
                canvas.getChildren().add(nullText);

                Text tailText = makeText(x + 52, y + boxH + 45, "TAIL", 14);
                tailText.setFill(Color.web("#dc2626"));
                canvas.getChildren().add(tailText);
            }

            temp = temp.next;
            index++;
        }
    }

    private Rectangle makeBox(double x, double y, double w, double h, String fill) {
        Rectangle r = new Rectangle(x, y, w, h);
        r.setArcWidth(16);
        r.setArcHeight(16);
        r.setFill(Color.web(fill));
        r.setStroke(Color.web("#60a5fa"));
        r.setStrokeWidth(2.3);
        return r;
    }

    private Polygon createArrowHead(double x, double y, boolean right) {
        Polygon arrow = new Polygon();
        if (right) arrow.getPoints().addAll(x, y, x - 10, y - 6, x - 10, y + 6);
        else arrow.getPoints().addAll(x, y, x + 10, y - 6, x + 10, y + 6);
        arrow.setFill(Color.web("#475569"));
        return arrow;
    }

    private int getSize() {
        int count = 0;
        Node temp = head;
        while (temp != null) {
            count++;
            temp = temp.next;
        }
        return count;
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
                showPopup("Input Error", "Please enter a value first.");
                return null;
            }
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            setStatus("Invalid number");
            showPopup("Input Error", "Please enter a valid integer.");
            return null;
        }
    }

    private void showPopup(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void setStatus(String msg) {
        statusLabel.setText(msg);
        headerStatusLabel.setText(msg);
    }
}