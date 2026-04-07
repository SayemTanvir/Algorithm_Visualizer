package org.example.VisuAlgorithm;

import java.util.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.QuadCurve;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

public class CircularLinkedListController {

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

    // ── old linear-layout constants kept so nothing else breaks ──
    private final double startX = 60;
    private final double startY = 260;
    private final double boxW = 80;
    private final double boxH = 50;
    private final double gap = 140;

    private final Random random = new Random();
    private Timeline currentTimeline;
    private boolean isPaused = false;
    private int currentStepIndex = 0;

    private int getRandomValue() {
        return random.nextInt(90) + 10;
    }

    @FXML
    private void onRandom() {
        head = null;
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
                            node.next = head;
                        } else {
                            Node tail = getTail();
                            tail.next = node;
                            node.next = head;
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

        if (head == null) {
            head = node;
            node.next = head;
        } else {
            Node tail = getTail();
            node.next = head;
            tail.next = node;
            head = node;
        }

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
            node.next = head;
        } else {
            Node tail = getTail();
            tail.next = node;
            node.next = head;
        }

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
            if (head == null) {
                head = node;
                node.next = head;
            } else {
                Node tail = getTail();
                node.next = head;
                tail.next = node;
                head = node;
            }
            return;
        }

        Node temp = head;
        int i = 0;
        while (i < index - 1 && temp.next != head) {
            temp = temp.next;
            i++;
        }

        if (i != index - 1) return;

        Node node = new Node(value);
        node.next = temp.next;
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
                    if (head.next == head) {
                        head = null;
                    } else {
                        Node tail = getTail();
                        head = head.next;
                        tail.next = head;
                    }
                    redraw(-1, -1);
                    setStatus("Deleted head");
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
            if (head.next == head) head = null;
            else {
                Node tail = getTail();
                head = head.next;
                tail.next = head;
            }
            return;
        }

        Node temp = head;
        int i = 0;
        while (i < index - 1 && temp.next != head) {
            temp = temp.next;
            i++;
        }

        if (temp.next == head || i != index - 1) return;
        temp.next = temp.next.next;
    }

    @FXML
    private void onSearch() {
        Integer value = parseInt(valueField.getText());
        if (value == null) return;

        if (head == null) {
            setStatus("List is empty");
            showPopup("Empty List", "Cannot search because the circular linked list is empty.");
            return;
        }

        Timeline timeline = new Timeline();
        Node temp = head;
        int index = 0;
        int found = -1;

        do {
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
        } while (temp != head);

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
                        showPopup("Search Result", "Value not found in the circular linked list.");
                    })
            );
        }

        timeline.play();
    }

    @FXML
    private void onTraverse() {
        if (head == null) {
            setStatus("List is empty");
            showPopup("Empty List", "Cannot traverse because the circular linked list is empty.");
            return;
        }

        Timeline timeline = new Timeline();
        Node temp = head;
        int index = 0;

        do {
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
        } while (temp != head);

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
        if (head == null || head.next == head) {
            setStatus("Nothing to sort");
            return;
        }

        Timeline timeline = new Timeline();
        double time = 0.0;
        int n = getSize();

        for (int i = 0; i < n - 1; i++) {
            Node a = head;
            int index = 0;

            for (int j = 0; j < n - 1; j++) {
                Node b = a.next;
                int idxA = index;
                int idxB = (index + 1) % n;

                timeline.getKeyFrames().add(
                        new KeyFrame(Duration.seconds(time), e -> {
                            redraw(idxA, idxB);
                            setStatus("Comparing index " + idxA + " and " + idxB);
                        })
                );
                time += 0.5;

                if (a.data > b.data) {
                    int tempVal = a.data;
                    a.data = b.data;
                    b.data = tempVal;

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

    // ──────────────────────────────────────────────────────────────
    //  HELPERS
    // ──────────────────────────────────────────────────────────────
    private Node getTail() {
        Node temp = head;
        while (temp.next != head) temp = temp.next;
        return temp;
    }

    private int getSize() {
        if (head == null) return 0;
        int count = 0;
        Node temp = head;
        do { count++; temp = temp.next; } while (temp != head);
        return count;
    }


    // ──────────────────────────────────────────────────────────────
    private void redraw(int primaryIndex, int secondaryIndex) {
        canvas.getChildren().clear();
        canvas.setPrefWidth(1400);
        canvas.setPrefHeight(900);

        int size = getSize();

        if (head == null) {
            Text empty = makeText(430, 400, "Circular Linked List is empty", 22);
            empty.setFill(Color.web("#64748b"));
            canvas.getChildren().add(empty);
            return;
        }

        double centerX = 600;
        double centerY = 380;
        double radius  = 200;
        double nodeR   = 38;

        // background guide ring
        Circle ring = new Circle(centerX, centerY, radius);
        ring.setFill(Color.TRANSPARENT);
        ring.setStroke(Color.web("#cbd5e1"));
        ring.setStrokeWidth(2);
        canvas.getChildren().add(ring);

        double[] nx = new double[size];
        double[] ny = new double[size];
        for (int i = 0; i < size; i++) {
            double angle = 2 * Math.PI * i / size - Math.PI / 2;
            nx[i] = centerX + radius * Math.cos(angle);
            ny[i] = centerY + radius * Math.sin(angle);
        }

        // ── draw arrows first (so circles sit on top) ──
        if (size == 1) {
            // single-node self-loop: small arc below the node
            QuadCurve loop = new QuadCurve();
            loop.setStartX(nx[0] + nodeR * 0.6);
            loop.setStartY(ny[0] + nodeR * 0.6);
            loop.setControlX(nx[0] + 80);
            loop.setControlY(ny[0] + 90);
            loop.setEndX(nx[0] - nodeR * 0.6);
            loop.setEndY(ny[0] + nodeR * 0.6);
            loop.setFill(null);
            loop.setStroke(Color.web("#7c3aed"));
            loop.setStrokeWidth(2.5);
            canvas.getChildren().add(loop);
        } else {
            for (int i = 0; i < size; i++) {
                int next = (i + 1) % size;
                drawArrowBetweenNodes(nx[i], ny[i], nx[next], ny[next], nodeR);
            }
        }

        // ── draw nodes ──
        Node temp = head;
        for (int i = 0; i < size; i++) {
            double x = nx[i];
            double y = ny[i];

            Color fill = Color.WHITE;
            if (i == secondaryIndex) fill = Color.web("#93c5fd");
            if (i == primaryIndex)   fill = Color.web("#fde68a");

            Circle circle = new Circle(x, y, nodeR);
            circle.setFill(fill);
            circle.setStroke(Color.web("#60a5fa"));
            circle.setStrokeWidth(2.5);

            // value text centred inside circle
            String valStr = String.valueOf(temp.data);
            Text dataText = makeText(x - (valStr.length() > 1 ? 10 : 6), y + 7, valStr, 18);

            canvas.getChildren().addAll(circle, dataText);

            // ── labels pushed outside the ring ──
            double angle    = 2 * Math.PI * i / size - Math.PI / 2;
            double labelDist = radius + nodeR + 24;
            double lx = centerX + labelDist * Math.cos(angle);
            double ly = centerY + labelDist * Math.sin(angle);

            // index  [i]
            Text idxText = makeText(lx - 8, ly + 5, "[" + i + "]", 12);
            idxText.setFill(Color.web("#475569"));
            canvas.getChildren().add(idxText);

            // HEAD label
            if (i == 0) {
                Text headText = makeText(lx - 20, ly - 14, "HEAD", 13);
                headText.setFill(Color.web("#0f766e"));
                canvas.getChildren().add(headText);
            }

            // TAIL label
            if (i == size - 1 && size > 1) {
                Text tailText = makeText(lx - 16, ly + 24, "TAIL", 13);
                tailText.setFill(Color.web("#dc2626"));
                canvas.getChildren().add(tailText);
            }

            // single-node is both HEAD and TAIL
            if (size == 1) {
                Text tailText = makeText(lx - 16, ly + 24, "TAIL", 13);
                tailText.setFill(Color.web("#dc2626"));
                canvas.getChildren().add(tailText);
            }

            temp = temp.next;
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  DRAW ARROW  ← REPLACES the old createArrowHead() method.
    //  Draws a straight line with arrowhead between two circle edges.
    // ──────────────────────────────────────────────────────────────
    private void drawArrowBetweenNodes(double x1, double y1,
                                       double x2, double y2,
                                       double nodeRadius) {
        double dx   = x2 - x1;
        double dy   = y2 - y1;
        double dist = Math.sqrt(dx * dx + dy * dy);
        double ux   = dx / dist;
        double uy   = dy / dist;

        // start/end at circle edges, not centres
        double sx = x1 + ux * nodeRadius;
        double sy = y1 + uy * nodeRadius;
        double ex = x2 - ux * nodeRadius;
        double ey = y2 - uy * nodeRadius;

        Line line = new Line(sx, sy, ex, ey);
        line.setStroke(Color.web("#64748b"));
        line.setStrokeWidth(2.5);
        canvas.getChildren().add(line);

        // arrowhead triangle at (ex, ey) pointing in direction (ux, uy)
        double aLen = 11;
        double aWid = 5.5;
        // perpendicular unit vector
        double px = -uy;
        double py =  ux;

        Polygon arrow = new Polygon();
        arrow.getPoints().addAll(
                ex,                              ey,
                ex - ux * aLen + px * aWid,     ey - uy * aLen + py * aWid,
                ex - ux * aLen - px * aWid,     ey - uy * aLen - py * aWid
        );
        arrow.setFill(Color.web("#475569"));
        canvas.getChildren().add(arrow);
    }

    // ──────────────────────────────────────────────────────────────
    //  UNCHANGED HELPERS below this line
    // ──────────────────────────────────────────────────────────────
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

        if (canvas != null && canvas.getScene() != null) {
            Stage stage = (Stage) canvas.getScene().getWindow();
            alert.initOwner(stage);
        }

        DialogPane dialogPane = alert.getDialogPane();
        try {
            dialogPane.getStylesheets().add(
                    getClass().getResource("/org/example/VisuAlgorithm/styles/main.css").toExternalForm()
            );
            dialogPane.getStyleClass().add("custom-alert");
        } catch (Exception e) {
            System.out.println("Could not load CSS for Alert: " + e.getMessage());
        }

        alert.showAndWait();
    }

    private void setStatus(String msg) {
        statusLabel.setText(msg);
        headerStatusLabel.setText(msg);
    }
}