package org.example.VisuAlgorithm;

import javafx.scene.control.Alert;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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

public class SinglyLinkedListController {

    @FXML private Pane canvas;
    @FXML private TextField valueField;
    @FXML private TextField indexField;
    @FXML private Label statusLabel;
    @FXML private Label headerStatusLabel;

    private static class Node {
        int data;
        Node next;
        Node(int data) { this.data = data; }
    }

    private Node head;

    private final double startX = 60;
    private final double startY = 250;
    private final double boxW = 80;
    private final double boxH = 50;
    private final double gap = 140;

    @FXML
    public void initialize() {
        redraw(-1, -1);
    }
    private void showPopup(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    @FXML
    private void onBack() {
        goTo("linked-list-view.fxml");
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
        head = node;

        redraw(0, -1);
        setStatus("Inserted " + value + " at head");
    }

    @FXML
    private void onInsertTail() {
        Integer value = parseInt(valueField.getText());
        if (value == null) return;

        Node node = new Node(value);

        if (head == null) {
            head = node;
            redraw(0, -1);
            setStatus("Inserted " + value + " at tail");
            return;
        }

        Node temp = head;
        while (temp.next != null) temp = temp.next;
        temp.next = node;

        redraw(getSize() - 1, -1);
        setStatus("Inserted " + value + " at tail");
    }

    @FXML
    private void onInsertAt() {
        Integer value = parseInt(valueField.getText());
        Integer index = parseInt(indexField.getText());
        if (value == null || index == null) return;

        if (index < 0) {
            setStatus("Invalid index");
            showPopup("Invalid Index", "Index cannot be negative.");
            return;
        }

        playInsertAnimation(index, value);
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

    @FXML
    private void onSearch() {
        Integer value = parseInt(valueField.getText());
        if (value == null) return;
        playSearchAnimation(value);
    }

    @FXML
    private void onTraverse() {
        playTraversalAnimation();
    }

    @FXML
    private void onSort() {
        playBubbleSortAnimation();
    }

    @FXML
    private void onClear() {
        head = null;
        redraw(-1, -1);
        setStatus("List cleared");
    }

    private void playInsertAnimation(int index, int value) {
        if (index > getSize()) {
            setStatus("Index out of range");
            showPopup("Index Out of Range", "Insertion index is outside the valid range.");
            return;
        }

        Timeline timeline = new Timeline();
        int highlight = Math.max(0, index - 1);

        timeline.getKeyFrames().add(
                new KeyFrame(Duration.seconds(0.0), e -> {
                    redraw(-1, highlight);
                    setStatus("Locating insertion point...");
                })
        );

        timeline.getKeyFrames().add(
                new KeyFrame(Duration.seconds(0.7), e -> {
                    insertAtLogic(index, value);
                    redraw(index, -1);
                    setStatus("Inserted " + value + " at index " + index);
                })
        );

        timeline.play();
    }

    private void insertAtLogic(int index, int value) {
        if (index == 0) {
            Node node = new Node(value);
            node.next = head;
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
        temp.next = node;
    }

    private void playDeleteAnimation(int index) {
        Timeline timeline = new Timeline();

        timeline.getKeyFrames().add(
                new KeyFrame(Duration.seconds(0.0), e -> {
                    redraw(index, -1);
                    setStatus("Deleting node at index " + index);
                })
        );

        timeline.getKeyFrames().add(
                new KeyFrame(Duration.seconds(0.7), e -> {
                    deleteAtLogic(index);
                    redraw(-1, -1);
                    setStatus("Deleted node at index " + index);
                })
        );

        timeline.play();
    }

    private void deleteAtLogic(int index) {
        if (head == null) return;

        if (index == 0) {
            head = head.next;
            return;
        }

        Node temp = head;
        int i = 0;

        while (temp.next != null && i < index - 1) {
            temp = temp.next;
            i++;
        }

        if (temp.next == null) return;
        temp.next = temp.next.next;
    }

    private void playSearchAnimation(int target) {
        if (head == null) {
            setStatus("List is empty");
            showPopup("Empty List", "Cannot search because the list is empty.");
            return;
        }

        Timeline timeline = new Timeline();
        Node temp = head;
        int index = 0;
        int foundIndex = -1;

        while (temp != null) {
            int currentIndex = index;
            int currentValue = temp.data;

            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(index * 0.6), e -> {
                        redraw(-1, currentIndex);
                        setStatus("Checking index " + currentIndex);
                    })
            );

            if (currentValue == target) {
                foundIndex = index;
                break;
            }

            temp = temp.next;
            index++;
        }

        if (foundIndex != -1) {
            int finalFound = foundIndex;
            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds((foundIndex + 1) * 0.6), e -> {
                        redraw(finalFound, -1);
                        setStatus("Found at index " + finalFound);
                    })
            );
        } else {
            int finalTimeIndex = Math.max(1, getSize());
            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(finalTimeIndex * 0.6), e -> {
                        redraw(-1, -1);
                        setStatus("Value not found");
                        showPopup("Search Result", "Value not found in the linked list.");
                    })
            );
        }

        timeline.play();
    }

    private void playTraversalAnimation() {
        if (head == null) {
            setStatus("List is empty");
            showPopup("Empty List", "Cannot traverse because the list is empty.");
            return;
        }

        Timeline timeline = new Timeline();
        Node temp = head;
        int index = 0;

        while (temp != null) {
            int currentIndex = index;
            int currentValue = temp.data;

            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(index * 0.5), e -> {
                        redraw(-1, currentIndex);
                        setStatus("Visited node " + currentValue + " at index " + currentIndex);
                    })
            );

            temp = temp.next;
            index++;
        }

        int end = index;
        timeline.getKeyFrames().add(
                new KeyFrame(Duration.seconds(end * 0.5), e -> {
                    redraw(-1, -1);
                    setStatus("Traversal complete");
                })
        );

        timeline.play();
    }

    private void playBubbleSortAnimation() {
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

    private void redraw(int primaryIndex, int secondaryIndex) {
        canvas.getChildren().clear();

        int size = getSize();
        double neededWidth = Math.max(1600, startX + size * gap + 300);
        canvas.setPrefWidth(neededWidth);
        canvas.setPrefHeight(720);

        if (head == null) {
            Text text = makeText(250, 180, "Singly Linked List is empty", 22);
            canvas.getChildren().add(text);
            return;
        }

        Node temp = head;
        int index = 0;

        while (temp != null) {
            double x = startX + index * gap;
            double y = startY;

            Color fillColor = Color.WHITE;
            if (index == secondaryIndex) fillColor = Color.web("#93c5fd");
            if (index == primaryIndex) fillColor = Color.web("#fde68a");

            Rectangle dataBox = new Rectangle(x, y, boxW, boxH);
            dataBox.setArcWidth(16);
            dataBox.setArcHeight(16);
            dataBox.setFill(fillColor);
            dataBox.setStroke(Color.web("#60a5fa"));
            dataBox.setStrokeWidth(2.5);

            Rectangle nextBox = new Rectangle(x + boxW, y, 40, boxH);
            nextBox.setArcWidth(16);
            nextBox.setArcHeight(16);
            nextBox.setFill(Color.WHITE);
            nextBox.setStroke(Color.web("#60a5fa"));
            nextBox.setStrokeWidth(2.5);

            Text dataText = makeText(x + 28, y + 32, String.valueOf(temp.data), 18);
            Text idxText = makeText(x + 45, y - 12, "[" + index + "]", 12);

            canvas.getChildren().addAll(dataBox, nextBox, dataText, idxText);

            if (temp.next != null) {
                double x1 = x + boxW + 40;
                double y1 = y + boxH / 2.0;
                double x2 = x + gap;
                double y2 = y + boxH / 2.0;

                Line line = new Line(x1, y1, x2, y2);
                line.setStrokeWidth(2.5);
                line.setStroke(Color.web("#64748b"));

                Polygon arrow = createArrowHead(x2, y2);
                canvas.getChildren().addAll(line, arrow);
            } else {
                Text nullText = makeText(x + boxW + 48, y + 30, "NULL", 14);
                nullText.setFill(Color.web("#dc2626"));
                canvas.getChildren().add(nullText);

                Text tailText = makeText(x + 22, y + boxH + 45, "TAIL", 14);
                tailText.setFill(Color.web("#dc2626"));
                canvas.getChildren().add(tailText);
            }

            if (index == 0) {
                Text headText = makeText(x + 18, y - 45, "HEAD", 14);
                headText.setFill(Color.web("#0f766e"));
                canvas.getChildren().add(headText);
            }

            temp = temp.next;
            index++;
        }
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

    private Polygon createArrowHead(double x, double y) {
        Polygon arrow = new Polygon();
        arrow.getPoints().addAll(
                x, y,
                x - 10, y - 6,
                x - 10, y + 6
        );
        arrow.setFill(Color.web("#64748b"));
        return arrow;
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