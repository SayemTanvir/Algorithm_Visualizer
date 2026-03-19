package org.example.VisuAlgorithm;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Node;
import javafx.stage.Stage;
import java.io.IOException;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.*;

public class graphController {

    @FXML private Pane canvasPane;
    @FXML private ToggleButton nodeTool;
    @FXML private ToggleButton edgeTool;
    @FXML private CheckBox directedCheck;
    @FXML private CheckBox weightedCheck;
    @FXML private TextField weightField;
    @FXML private CheckBox customNodeCheck;
    @FXML private TextField nodeValueField;

    private int nodeCounter = 1;
    private GraphNode firstEdgeNode = null;
    private GraphNode selectedNode = null;
    private GraphEdge selectedEdge = null;

    private final List<GraphNode> nodes = new ArrayList<>();
    private final List<GraphEdge> edges = new ArrayList<>();
    private final Stack<UndoCommand> undoStack = new Stack<>();

    @FXML
    public void initialize() {
        nodeTool.setSelected(true);

        nodeTool.setOnAction(e -> clearSelection());
        edgeTool.setOnAction(e -> clearSelection());

        Platform.runLater(() -> {
            if (canvasPane.getScene() != null) {
                canvasPane.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.isShortcutDown() && event.getCode() == KeyCode.Z) {
                        handleUndo();
                        event.consume();
                    }
                });
            }
        });

        if (algoComboBox != null) {
            algoComboBox.getItems().addAll(
                    "BFS (Breadth-First Search)",
                    "DFS (Depth-First Search)",
                    "Dijkstra's Shortest Path",
                    "Prim's MST",
                    "Kruskal's MST",
                    "Topological Sorting (DAG)"
            );

            algoComboBox.setCellFactory(listView -> new ListCell<String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setDisable(false);
                        setStyle("");
                    } else {
                        setText(item);
                        boolean requiresWeight = item.contains("Dijkstra") ||
                                item.contains("Prim") ||
                                item.contains("Kruskal");
                        boolean hasUnweightedEdges = edges.stream().anyMatch(e -> !e.isWeighted);
                        boolean requiresDAG = item.contains("Topological");
                        boolean notADAG = !isDAG();

                        if ((requiresWeight && hasUnweightedEdges) || (requiresDAG && notADAG)) {
                            setDisable(true);
                            setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
                        } else {
                            setDisable(false);
                            setStyle("-fx-text-fill: black;");
                        }
                    }
                }
            });
        }
        if (resultLabel != null) resultLabel.setText("");
    }

    // ===============================
    // UNDO COMMANDS
    // ===============================
    private interface UndoCommand { void undo(); }

    private class AddNodeCommand implements UndoCommand {
        GraphNode node;
        AddNodeCommand(GraphNode n) { this.node = n; }
        public void undo() { removeNodeInternal(node); }
    }

    private class AddEdgeCommand implements UndoCommand {
        GraphEdge edge;
        AddEdgeCommand(GraphEdge e) { this.edge = e; }
        public void undo() { removeEdgeInternal(edge); }
    }

    private class DeleteCommand implements UndoCommand {
        GraphNode node;
        List<GraphEdge> associatedEdges;
        GraphEdge singleEdge;

        DeleteCommand(GraphNode n, List<GraphEdge> e) { this.node = n; this.associatedEdges = e; }
        DeleteCommand(GraphEdge e) { this.singleEdge = e; }

        public void undo() {
            if (node != null) {
                restoreNodeInternal(node);
                for (GraphEdge e : associatedEdges) restoreEdgeInternal(e);
            } else if (singleEdge != null) {
                restoreEdgeInternal(singleEdge);
            }
        }
    }

    // ===============================
    // CORE LOGIC
    // ===============================

    @FXML
    private void handleCanvasClick(MouseEvent event) {
        if (isAlgorithmMode) return;
        if (event.getTarget() != canvasPane) return;

        if (nodeTool.isSelected()) {
            createNode(event.getX(), event.getY());
        } else {
            clearSelection();
        }
    }

    private void clearSelection() {
        if (selectedNode != null) selectedNode.circle.setStroke(Color.BLACK);
        if (selectedEdge != null) selectedEdge.line.setStroke(Color.BLACK);
        selectedNode = null;
        selectedEdge = null;
        if (firstEdgeNode != null) {
            firstEdgeNode.circle.setStroke(Color.BLACK);
            firstEdgeNode = null;
        }
    }

    class GraphNode {
        Circle circle;
        Text label;
        double offsetX, offsetY;
        List<GraphEdge> connectedEdges = new ArrayList<>();
        Text distLabel;

        GraphNode(double x, double y, String value) {
            circle = new Circle(x, y, 20, Color.LIGHTBLUE);
            circle.setStroke(Color.BLACK);
            circle.setStrokeWidth(2);

            label = new Text(value);
            label.setMouseTransparent(true);

            distLabel = new Text("∞");
            distLabel.setFill(Color.DARKRED);
            distLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
            distLabel.setMouseTransparent(true);
            distLabel.setVisible(false);

            // FIX 4: Use a ChangeListener so the offset is recalculated after the
            // label is laid out and has a real, non-zero width.
            Platform.runLater(() -> {
                label.xProperty().bind(
                        circle.centerXProperty().subtract(label.getLayoutBounds().getWidth() / 2));
                label.yProperty().bind(
                        circle.centerYProperty().add(label.getLayoutBounds().getHeight() / 4));

                // distLabel: recompute offset whenever its text (and thus width) changes
                distLabel.textProperty().addListener((obs, oldVal, newVal) -> {
                    distLabel.xProperty().unbind();
                    distLabel.xProperty().bind(
                            circle.centerXProperty().subtract(distLabel.getLayoutBounds().getWidth() / 2));
                });
                // Initial bind after first layout pass
                distLabel.xProperty().bind(
                        circle.centerXProperty().subtract(distLabel.getLayoutBounds().getWidth() / 2));
                distLabel.yProperty().bind(
                        circle.centerYProperty().subtract(circle.getRadius() + 5));
            });

            enableDrag();
        }

        void enableDrag() {
            circle.setOnMousePressed(e -> {
                // FIX 2: Block all interaction while an algorithm is running/paused.
                if (isAlgorithmMode) return;

                offsetX = circle.getCenterX() - e.getSceneX();
                offsetY = circle.getCenterY() - e.getSceneY();
                if (!edgeTool.isSelected()) {
                    selectNode(this);
                }
                e.consume();
            });

            circle.setOnMouseDragged(e -> {
                // FIX 3: Prevent dragging nodes while algorithm mode is active.
                if (isAlgorithmMode) return;

                circle.setCenterX(e.getSceneX() + offsetX);
                circle.setCenterY(e.getSceneY() + offsetY);
                updateConnectedEdges();
                e.consume();
            });

            circle.setOnMouseClicked(e -> {
                handleNodeClick(this);
                e.consume();
            });
        }

        void updateConnectedEdges() {
            for (GraphEdge edge : connectedEdges) {
                edge.update();
            }
        }
    }

    class GraphEdge {
        GraphNode from, to;
        Line line;
        Polygon arrowHead;
        Text weightText;
        boolean isDirected;
        boolean isWeighted;

        GraphEdge(GraphNode from, GraphNode to, int weight, boolean directed, boolean weighted) {
            this.from = from;
            this.to = to;
            this.isDirected = directed;
            this.isWeighted = weighted;

            line = new Line();
            line.setStrokeWidth(3);
            line.setStroke(Color.BLACK);

            weightText = new Text(String.valueOf(weight));
            weightText.setMouseTransparent(true);

            arrowHead = new Polygon();
            arrowHead.setFill(Color.BLACK);

            line.setOnMouseClicked(e -> {
                selectEdge(this);
                e.consume();
            });
        }

        void update() {
            double sx = from.circle.getCenterX();
            double sy = from.circle.getCenterY();
            double ex = to.circle.getCenterX();
            double ey = to.circle.getCenterY();

            double dx = ex - sx;
            double dy = ey - sy;
            double distance = Math.sqrt(dx * dx + dy * dy);

            if (distance < 1) return;

            double radius = from.circle.getRadius();
            double startX = sx + (dx / distance) * radius;
            double startY = sy + (dy / distance) * radius;
            double endX = ex - (dx / distance) * radius;
            double endY = ey - (dy / distance) * radius;

            line.setStartX(startX);
            line.setStartY(startY);
            line.setEndX(endX);
            line.setEndY(endY);

            if (isWeighted) {
                weightText.setX((startX + endX) / 2 + 5);
                weightText.setY((startY + endY) / 2 - 5);
            }

            if (isDirected) {
                double angle = Math.atan2(dy, dx);
                double arrowLength = 12;
                arrowHead.getPoints().setAll(
                        endX, endY,
                        endX - arrowLength * Math.cos(angle - Math.PI / 8),
                        endY - arrowLength * Math.sin(angle - Math.PI / 8),
                        endX - arrowLength * Math.cos(angle + Math.PI / 8),
                        endY - arrowLength * Math.sin(angle + Math.PI / 8)
                );
            }
        }
    }

    // ===============================
    // HELPER METHODS
    // ===============================

    private void createNode(double x, double y) {
        String value = (customNodeCheck.isSelected() && !nodeValueField.getText().isEmpty())
                ? nodeValueField.getText() : String.valueOf(nodeCounter++);
        GraphNode node = new GraphNode(x, y, value);
        restoreNodeInternal(node);
        undoStack.push(new AddNodeCommand(node));
    }

    private void handleNodeClick(GraphNode node) {
        // Guard: never let a click affect graph state while an algorithm is playing
        if (isAlgorithmMode) return;

        if (edgeTool.isSelected()) {
            if (firstEdgeNode == null) {
                firstEdgeNode = node;
                node.circle.setStroke(Color.ORANGE);
            } else {
                if (firstEdgeNode != node) createEdge(firstEdgeNode, node);
                firstEdgeNode.circle.setStroke(Color.BLACK);
                firstEdgeNode = null;
            }
        } else {
            selectNode(node);
        }
    }

    private void createEdge(GraphNode from, GraphNode to) {
        if (edges.stream().anyMatch(e -> e.from == from && e.to == to)) return;

        int weight = 1;
        try { weight = Integer.parseInt(weightField.getText()); } catch (NumberFormatException ignored) {}

        GraphEdge edge = new GraphEdge(from, to, weight, directedCheck.isSelected(), weightedCheck.isSelected());
        restoreEdgeInternal(edge);
        undoStack.push(new AddEdgeCommand(edge));
    }

    private void selectNode(GraphNode node) {
        clearSelection();
        selectedNode = node;
        node.circle.setStroke(Color.RED);
    }

    private void selectEdge(GraphEdge edge) {
        clearSelection();
        selectedEdge = edge;
        edge.line.setStroke(Color.RED);
    }

    @FXML
    private void deleteSelected() {
        GraphNode nodeToDelete = selectedNode != null ? selectedNode : firstEdgeNode;

        if (nodeToDelete != null) {
            List<GraphEdge> toRemove = new ArrayList<>(nodeToDelete.connectedEdges);
            undoStack.push(new DeleteCommand(nodeToDelete, new ArrayList<>(toRemove)));
            toRemove.forEach(this::removeEdgeInternal);
            removeNodeInternal(nodeToDelete);
            selectedNode = null;
            firstEdgeNode = null;
        } else if (selectedEdge != null) {
            undoStack.push(new DeleteCommand(selectedEdge));
            removeEdgeInternal(selectedEdge);
            selectedEdge = null;
        }
    }

    private void handleUndo() {
        if (!undoStack.isEmpty()) {
            undoStack.pop().undo();
            clearSelection();
        }
    }

    private void removeNodeInternal(GraphNode node) {
        nodes.remove(node);
        canvasPane.getChildren().removeAll(node.circle, node.label, node.distLabel);
    }

    private void restoreNodeInternal(GraphNode node) {
        if (!nodes.contains(node)) nodes.add(node);
        if (!canvasPane.getChildren().contains(node.circle)) {
            canvasPane.getChildren().addAll(node.circle, node.label, node.distLabel);
        }
    }

    private void removeEdgeInternal(GraphEdge edge) {
        edges.remove(edge);
        edge.from.connectedEdges.remove(edge);
        edge.to.connectedEdges.remove(edge);
        canvasPane.getChildren().removeAll(edge.line, edge.arrowHead, edge.weightText);
    }

    private void restoreEdgeInternal(GraphEdge edge) {
        if (!edges.contains(edge)) edges.add(edge);
        if (!edge.from.connectedEdges.contains(edge)) edge.from.connectedEdges.add(edge);
        if (!edge.to.connectedEdges.contains(edge)) edge.to.connectedEdges.add(edge);

        if (!canvasPane.getChildren().contains(edge.line)) {
            int index = 0;
            canvasPane.getChildren().add(index++, edge.line);
            if (edge.isDirected) canvasPane.getChildren().add(index++, edge.arrowHead);
            if (edge.isWeighted) canvasPane.getChildren().add(index, edge.weightText);
        }

        edge.update();
    }

    @FXML
    public void generateRandomGraph() {
        clearGraph();

        Random random = new Random();
        double width = canvasPane.getWidth() > 0 ? canvasPane.getWidth() : 600;
        double height = canvasPane.getHeight() > 0 ? canvasPane.getHeight() : 400;
        int numNodes = random.nextInt(4) + 5;
        double centerX = width / 2;
        double centerY = height / 2;
        double radius = Math.min(centerX, centerY) - 50;
        double angleStep = 2 * Math.PI / numNodes;

        for (int i = 0; i < numNodes; i++) {
            double x = centerX + radius * Math.cos(i * angleStep);
            double y = centerY + radius * Math.sin(i * angleStep);
            GraphNode node = new GraphNode(x, y, String.valueOf(nodeCounter++));
            restoreNodeInternal(node);
        }

        boolean isDirected = directedCheck.isSelected();
        boolean isWeighted = weightedCheck.isSelected();

        List<GraphNode> connected = new ArrayList<>();
        List<GraphNode> unconnected = new ArrayList<>(nodes);
        connected.add(unconnected.remove(random.nextInt(unconnected.size())));

        while (!unconnected.isEmpty()) {
            GraphNode from = connected.get(random.nextInt(connected.size()));
            GraphNode to = unconnected.remove(random.nextInt(unconnected.size()));
            int weight = isWeighted ? random.nextInt(20) + 1 : 1;
            GraphEdge edge = new GraphEdge(from, to, weight, isDirected, isWeighted);
            restoreEdgeInternal(edge);
            connected.add(to);
        }

        int extraEdges = random.nextInt(3);
        for (int i = 0; i < extraEdges; i++) {
            GraphNode from = nodes.get(random.nextInt(nodes.size()));
            GraphNode to = nodes.get(random.nextInt(nodes.size()));
            if (from != to) {
                boolean exists = edges.stream().anyMatch(e ->
                        (e.from == from && e.to == to) ||
                                (!isDirected && e.from == to && e.to == from));
                if (!exists) {
                    int weight = isWeighted ? random.nextInt(20) + 1 : 1;
                    GraphEdge edge = new GraphEdge(from, to, weight, isDirected, isWeighted);
                    restoreEdgeInternal(edge);
                }
            }
        }
    }

    @FXML
    public void clearGraph() {
        nodes.clear();
        edges.clear();
        canvasPane.getChildren().clear();
        undoStack.clear();
        nodeCounter = 1;
        clearSelection();
    }

    @FXML private Button backButton;

    @FXML
    private void handleBackButton(ActionEvent event) throws IOException {
        // FIX 1: Actually stop the running timeline before leaving the scene.
        stopAll();

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("hello-view.fxml"));
        Parent root = fxmlLoader.load();
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.getScene().setRoot(root);
    }

    private void stopAll() {
        // FIX 1: Was an empty stub — must stop the animation timeline.
        resetAlgorithmState();
        clearSelection();
    }

    // --- UI Mode Panels ---
    @FXML private ToolBar buildToolbar;
    @FXML private ToolBar algoToolbar;
    @FXML private ToolBar playbackToolbar;

    @FXML private ComboBox<String> algoComboBox;
    @FXML private TextField startNodeField;
    @FXML private TextField endNodeField;
    @FXML private Slider speedSlider;
    @FXML private Button playPauseButton;

    private boolean isAlgorithmMode = false;

    @FXML
    private void switchToAlgoMode() {
        if (nodes.isEmpty()) {
            System.out.println("Graph is empty! Build a graph first.");
            return;
        }

        isAlgorithmMode = true;
        clearSelection();

        // --- NEW: Give the user a prompt when entering Algo Mode ---
        resultLabel.setText("Select an algorithm and press Play!");

        boolean hasUnweightedEdges = edges.stream().anyMatch(e -> !e.isWeighted);

        String savedSelection = algoComboBox.getValue();
        List<String> currentItems = new ArrayList<>(algoComboBox.getItems());
        algoComboBox.getItems().clear();
        algoComboBox.getItems().addAll(currentItems);

        if (savedSelection != null && (
                (hasUnweightedEdges && (savedSelection.contains("Dijkstra") ||
                        savedSelection.contains("Prim") ||
                        savedSelection.contains("Kruskal")))
                        || (savedSelection.contains("Topological") && !isDAG())
        )) {
            algoComboBox.setValue(null);
        } else {
            algoComboBox.setValue(savedSelection);
        }

        buildToolbar.setVisible(false);
        algoToolbar.setVisible(true);
        playbackToolbar.setVisible(true);
        playbackToolbar.setManaged(true);
    }

    @FXML
    private void switchToBuildMode() {
        isAlgorithmMode = false;
        resetAlgorithmState();
        algoToolbar.setVisible(false);
        playbackToolbar.setVisible(false);
        playbackToolbar.setManaged(false);
        buildToolbar.setVisible(true);
    }

    @FXML
    private void resetGraphColors() {
        for (GraphNode n : nodes) {
            n.circle.setFill(Color.LIGHTBLUE);
            n.circle.setStroke(Color.BLACK);
            n.distLabel.setText("∞");
            n.distLabel.setFill(Color.DARKRED);
            n.distLabel.setVisible(false);
        }
        for (GraphEdge e : edges) {
            e.line.setStroke(Color.BLACK);
            e.line.setStrokeWidth(3);
        }
    }

    @FXML private Label resultLabel;

    private Timeline timeline;
    private final List<Runnable> algorithmSteps = new ArrayList<>();
    private int currentStep = 0;

    private GraphNode findNodeByValue(String value) {
        if (value == null || value.isEmpty()) return null;
        for (GraphNode node : nodes) {
            if (node.label.getText().equals(value)) return node;
        }
        return null;
    }

    private void initializeAlgorithm() {
        if (timeline != null) {
            timeline.stop();
            // FIX 6: Null out the old timeline so setupTimeline() always creates a
            // fresh one with a current rate-binding. Without this the binding to the
            // speed slider is established only once and then lost on replays.
            timeline = null;
        }
        resetGraphColors();
        resultLabel.setText("Starting Algorithm...");
        algorithmSteps.clear();
        currentStep = 0;

        GraphNode startNode = findNodeByValue(startNodeField.getText());
        if (startNode == null) startNode = nodes.get(0);
        GraphNode endNode = findNodeByValue(endNodeField.getText());

        String selectedAlgo = algoComboBox.getValue();
        if (selectedAlgo != null) {
            if (selectedAlgo.startsWith("BFS")) {
                recordBFS(startNode);
            } else if (selectedAlgo.startsWith("DFS")) {
                recordDFS(startNode);
            } else if (selectedAlgo.startsWith("Prim")) {
                if (edges.stream().anyMatch(e -> !e.isWeighted)) {
                    resultLabel.setText("Error: Prim's MST requires a fully weighted graph!");
                    return;
                }
                recordPrim(startNode);
            } else if (selectedAlgo.startsWith("Kruskal")) {
                if (edges.stream().anyMatch(e -> !e.isWeighted)) {
                    resultLabel.setText("Error: Kruskal's MST requires a fully weighted graph!");
                    return;
                }
                recordKruskal();
            } else if (selectedAlgo.startsWith("Dijkstra")) {
                if (edges.stream().anyMatch(e -> !e.isWeighted)) {
                    resultLabel.setText("Error: Dijkstra requires a fully weighted graph!");
                    return;
                }
                recordDijkstra(startNode, endNode);
            } else if (selectedAlgo.startsWith("Topological")) {
            if (!isDAG()) {
                resultLabel.setText("Error: Graph must be a directed acyclic graph (DAG)!");
                return;
            }
            recordTopologicalSort();
        }
        }
    }

    private void setupTimeline() {
        timeline = new Timeline(new KeyFrame(Duration.seconds(1.0), event -> {
            if (currentStep < algorithmSteps.size()) {
                algorithmSteps.get(currentStep).run();
                currentStep++;
            } else {
                timeline.stop();
                playPauseButton.setText("↺ Restart");
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.rateProperty().bind(speedSlider.valueProperty());
    }

    @FXML
    private void togglePlayPause() {
        if (nodes.isEmpty() || algoComboBox.getValue() == null) return;

        if (timeline != null && timeline.getStatus() == javafx.animation.Animation.Status.RUNNING) {
            timeline.pause();
            playPauseButton.setText("▶ Play");
            return;
        }

        if (algorithmSteps.isEmpty() || currentStep >= algorithmSteps.size()) {
            initializeAlgorithm();
        }

        // FIX 6 (continued): Always create a fresh timeline after initializeAlgorithm()
        // nulls the old one, ensuring the speed-slider binding is always live.
        if (timeline == null) {
            setupTimeline();
        }

        timeline.play();
        playPauseButton.setText("⏸ Pause");
    }

    @FXML
    private void stepForward() {
        if (nodes.isEmpty() || algoComboBox.getValue() == null) return;

        if (timeline != null) {
            timeline.pause();
            playPauseButton.setText("▶ Play");
        }

        if (algorithmSteps.isEmpty() || currentStep >= algorithmSteps.size()) {
            initializeAlgorithm();
            // Also set up a timeline here so Play still works after manual stepping
            if (timeline == null) setupTimeline();
        }

        if (currentStep < algorithmSteps.size()) {
            algorithmSteps.get(currentStep).run();
            currentStep++;
        }
    }

    @FXML
    private void stepBackward() {
        if (algorithmSteps.isEmpty() || currentStep <= 0) return;

        if (timeline != null) {
            timeline.pause();
            playPauseButton.setText("▶ Play");
        }

        currentStep--;
        resetGraphColors();
        resultLabel.setText("Traversal Order: ");
        for (int i = 0; i < currentStep; i++) {
            algorithmSteps.get(i).run();
        }
    }

    @FXML
    private void resetAlgorithmState() {
        if (timeline != null) {
            timeline.stop();
            timeline = null; // FIX 6: null out so next play gets a fresh binding
        }
        if (playPauseButton != null) playPauseButton.setText("▶ Play");
        resetGraphColors();

        // --- NEW: Conditionally set the text based on the mode ---
        if (isAlgorithmMode) {
            resultLabel.setText("Select an algorithm and press Play!");
        } else {
            resultLabel.setText(""); // Clear it for Build Mode
        }

        algorithmSteps.clear();
        currentStep = 0;
    }

    // --- BFS ---
    private void recordBFS(GraphNode startNode) {
        Set<GraphNode> visited = new HashSet<>();
        Queue<GraphNode> queue = new LinkedList<>();
        List<String> visitedOrder = new ArrayList<>();

        queue.add(startNode);
        visited.add(startNode);

        algorithmSteps.add(() -> startNode.circle.setFill(Color.YELLOW));

        while (!queue.isEmpty()) {
            GraphNode current = queue.poll();
            visitedOrder.add(current.label.getText());
            final String currentPath = "Traversal Order: " + String.join(" ➔ ", visitedOrder);
            final GraphNode exploringNode = current;

            algorithmSteps.add(() -> {
                exploringNode.circle.setFill(Color.MAGENTA);
                resultLabel.setText(currentPath);
            });

            for (GraphEdge edge : current.connectedEdges) {
                GraphNode neighbor = null;
                if (edge.from == current) {
                    neighbor = edge.to;
                } else if (!edge.isDirected && edge.to == current) {
                    neighbor = edge.from;
                }

                if (neighbor != null && !visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);

                    final GraphEdge traversedEdge = edge;
                    final GraphNode nextNode = neighbor;

                    algorithmSteps.add(() -> {
                        traversedEdge.line.setStroke(Color.ORANGE);
                        nextNode.circle.setFill(Color.YELLOW);
                    });
                }
            }

            algorithmSteps.add(() -> exploringNode.circle.setFill(Color.GREEN));
        }
    }

    // --- DFS ---
    private void recordDFS(GraphNode startNode) {
        Set<GraphNode> visited = new HashSet<>();
        List<String> visitedOrder = new ArrayList<>();
        dfsHelper(startNode, null, visited, visitedOrder);
    }

    private void dfsHelper(GraphNode current, GraphEdge edgeToReach,
                           Set<GraphNode> visited, List<String> visitedOrder) {
        visited.add(current);

        if (edgeToReach != null) {
            final GraphEdge traversedEdge = edgeToReach;
            final GraphNode nextNode = current;
            algorithmSteps.add(() -> {
                traversedEdge.line.setStroke(Color.ORANGE);
                nextNode.circle.setFill(Color.YELLOW);
            });
        } else {
            algorithmSteps.add(() -> current.circle.setFill(Color.YELLOW));
        }

        visitedOrder.add(current.label.getText());
        final String currentPath = "Traversal Order: " + String.join(" ➔ ", visitedOrder);
        final GraphNode exploringNode = current;

        algorithmSteps.add(() -> {
            exploringNode.circle.setFill(Color.MAGENTA);
            resultLabel.setText(currentPath);
        });

        for (GraphEdge edge : current.connectedEdges) {
            GraphNode neighbor = null;
            if (edge.from == current) {
                neighbor = edge.to;
            } else if (!edge.isDirected && edge.to == current) {
                neighbor = edge.from;
            }

            if (neighbor != null && !visited.contains(neighbor)) {
                dfsHelper(neighbor, edge, visited, visitedOrder);
            }
        }

        algorithmSteps.add(() -> exploringNode.circle.setFill(Color.GREEN));
    }

    // --- Prim's MST ---
    private void recordPrim(GraphNode startNode) {
        Set<GraphNode> visited = new HashSet<>();
        PriorityQueue<GraphEdge> pq = new PriorityQueue<>(Comparator.comparingInt(e -> {
            if (!e.isWeighted) return 1;
            try { return Integer.parseInt(e.weightText.getText()); }
            catch (NumberFormatException ex) { return 1; }
        }));

        visited.add(startNode);
        algorithmSteps.add(() -> {
            startNode.circle.setFill(Color.YELLOW);
            resultLabel.setText("Prim's MST: Started at " + startNode.label.getText() + " (Weight: 0)");
        });

        for (GraphEdge edge : startNode.connectedEdges) pq.add(edge);

        int[] totalWeight = {0};

        while (!pq.isEmpty() && visited.size() < nodes.size()) {
            GraphEdge minEdge = pq.poll();

            GraphNode unvisitedNode = null;
            if (visited.contains(minEdge.from) && !visited.contains(minEdge.to)) {
                unvisitedNode = minEdge.to;
            } else if (!minEdge.isDirected && visited.contains(minEdge.to) && !visited.contains(minEdge.from)) {
                unvisitedNode = minEdge.from;
            }

            if (unvisitedNode != null) {
                visited.add(unvisitedNode);
                int edgeWeight = minEdge.isWeighted ? Integer.parseInt(minEdge.weightText.getText()) : 1;
                totalWeight[0] += edgeWeight;

                final GraphNode nextNode = unvisitedNode;
                final GraphEdge mstEdge = minEdge;
                final int currentTotal = totalWeight[0];

                algorithmSteps.add(() -> {
                    mstEdge.line.setStroke(Color.ORANGE);
                    mstEdge.line.setStrokeWidth(5);
                    nextNode.circle.setFill(Color.YELLOW);
                    resultLabel.setText("Prim's MST Total Weight: " + currentTotal);
                });

                for (GraphEdge edge : nextNode.connectedEdges) {
                    GraphNode neighbor = (edge.from == nextNode) ? edge.to
                            : (!edge.isDirected && edge.to == nextNode) ? edge.from : null;
                    if (neighbor != null && !visited.contains(neighbor)) pq.add(edge);
                }
            }
        }

        algorithmSteps.add(() -> {
            for (GraphNode node : visited) node.circle.setFill(Color.GREEN);
            resultLabel.setText(resultLabel.getText() + " (Complete)");
        });
    }

    // --- Kruskal's MST ---
    private void recordKruskal() {
        Map<GraphNode, GraphNode> parent = new HashMap<>();
        for (GraphNode node : nodes) parent.put(node, node);

        java.util.function.Function<GraphNode, GraphNode> find = new java.util.function.Function<>() {
            @Override
            public GraphNode apply(GraphNode node) {
                if (parent.get(node) == node) return node;
                GraphNode root = apply(parent.get(node));
                parent.put(node, root);
                return root;
            }
        };

        List<GraphEdge> sortedEdges = new ArrayList<>(edges);
        sortedEdges.sort(Comparator.comparingInt(e -> {
            if (!e.isWeighted) return 1;
            try { return Integer.parseInt(e.weightText.getText()); }
            catch (NumberFormatException ex) { return 1; }
        }));

        algorithmSteps.add(() -> resultLabel.setText("Kruskal's MST: Sorting all edges by weight..."));

        int[] totalWeight = {0};
        Set<GraphNode> mstNodes = new HashSet<>();
        int edgesAdded = 0;

        for (GraphEdge edge : sortedEdges) {
            if (edgesAdded >= nodes.size() - 1) break;

            GraphNode root1 = find.apply(edge.from);
            GraphNode root2 = find.apply(edge.to);

            if (root1 != root2) {
                parent.put(root1, root2);
                edgesAdded++;
                mstNodes.add(edge.from);
                mstNodes.add(edge.to);

                int edgeWeight = edge.isWeighted ? Integer.parseInt(edge.weightText.getText()) : 1;
                totalWeight[0] += edgeWeight;

                final int currentTotal = totalWeight[0];
                final GraphEdge mstEdge = edge;
                final GraphNode u = edge.from;
                final GraphNode v = edge.to;

                algorithmSteps.add(() -> {
                    mstEdge.line.setStroke(Color.ORANGE);
                    mstEdge.line.setStrokeWidth(5);
                    u.circle.setFill(Color.YELLOW);
                    v.circle.setFill(Color.YELLOW);
                    resultLabel.setText("Kruskal's MST Total Weight: " + currentTotal);
                });
            }
        }

        algorithmSteps.add(() -> {
            for (GraphNode node : mstNodes) node.circle.setFill(Color.GREEN);
            resultLabel.setText(resultLabel.getText() + " (Complete)");
        });
    }

    // --- Dijkstra's Shortest Path ---
    private void recordDijkstra(GraphNode startNode, GraphNode endNode) {
        Map<GraphNode, Integer> distances = new HashMap<>();
        Map<GraphNode, GraphEdge> edgeTo = new HashMap<>();
        Set<GraphNode> settled = new HashSet<>();

        class NodeDist implements Comparable<NodeDist> {
            GraphNode node; int dist;
            NodeDist(GraphNode n, int d) { node = n; dist = d; }
            public int compareTo(NodeDist o) { return Integer.compare(this.dist, o.dist); }
        }

        PriorityQueue<NodeDist> pq = new PriorityQueue<>();

        for (GraphNode node : nodes) distances.put(node, Integer.MAX_VALUE);
        distances.put(startNode, 0);
        pq.add(new NodeDist(startNode, 0));

        algorithmSteps.add(() -> {
            for (GraphNode node : nodes) node.distLabel.setVisible(true);
            startNode.circle.setFill(Color.YELLOW);
            startNode.distLabel.setText("0");
            startNode.distLabel.setFill(Color.GREEN);
            resultLabel.setText("Dijkstra: Starting at " + startNode.label.getText());
        });

        while (!pq.isEmpty()) {
            NodeDist current = pq.poll();
            GraphNode u = current.node;

            if (settled.contains(u)) continue;
            settled.add(u);

            final GraphNode exploringNode = u;
            final int currentDist = current.dist;

            algorithmSteps.add(() -> {
                if (exploringNode != startNode) exploringNode.circle.setFill(Color.MAGENTA);
                exploringNode.distLabel.setFill(Color.DARKBLUE);
                resultLabel.setText("Dijkstra: Exploring " + exploringNode.label.getText()
                        + " (dist: " + currentDist + ")");
            });

            if (endNode != null && u == endNode) break;

            for (GraphEdge edge : u.connectedEdges) {
                GraphNode v = null;
                if (edge.from == u) v = edge.to;
                else if (!edge.isDirected && edge.to == u) v = edge.from;

                if (v != null && !settled.contains(v)) {
                    int weight = edge.isWeighted ? Integer.parseInt(edge.weightText.getText()) : 1;
                    int newDist = current.dist + weight;

                    if (newDist < distances.get(v)) {
                        distances.put(v, newDist);
                        edgeTo.put(v, edge);
                        pq.add(new NodeDist(v, newDist));

                        final GraphNode neighbor = v;
                        final GraphEdge traversedEdge = edge;
                        final int neighborDist = newDist;

                        algorithmSteps.add(() -> {
                            traversedEdge.line.setStroke(Color.ORANGE);
                            if (neighbor != startNode) neighbor.circle.setFill(Color.YELLOW);
                            neighbor.distLabel.setText(String.valueOf(neighborDist));
                            neighbor.distLabel.setFill(Color.DARKRED);
                            resultLabel.setText("Dijkstra: Updated "
                                    + neighbor.label.getText() + " → dist " + neighborDist);
                        });
                    }
                }
            }

            algorithmSteps.add(() -> {
                if (exploringNode != startNode && exploringNode != endNode) {
                    exploringNode.circle.setFill(Color.LIGHTGREEN);
                    exploringNode.distLabel.setFill(Color.DARKGREEN);
                }
            });
        }

        if (endNode != null) {
            if (distances.get(endNode) == Integer.MAX_VALUE) {
                algorithmSteps.add(() -> resultLabel.setText(
                        "Dijkstra: No reachable path to " + endNode.label.getText() + "!"));
            } else {
                algorithmSteps.add(() -> resultLabel.setText(
                        "Dijkstra: Shortest path found! Total Dist: " + distances.get(endNode)));

                GraphNode curr = endNode;
                List<Runnable> pathAnimations = new ArrayList<>();

                while (curr != startNode && edgeTo.containsKey(curr)) {
                    GraphEdge e = edgeTo.get(curr);
                    final GraphEdge pathEdge = e;
                    final GraphNode pathNode = curr;

                    pathAnimations.add(() -> {
                        pathEdge.line.setStroke(Color.GREEN);
                        pathEdge.line.setStrokeWidth(5);
                        pathNode.circle.setFill(Color.GREEN);
                        pathNode.distLabel.setFill(Color.WHITE);
                    });

                    // FIX 7: Determine the predecessor correctly.
                    // edgeTo.put(v, edge) always stores the edge that was used to
                    // *reach* v. Therefore the predecessor of curr is whichever
                    // endpoint of the edge is NOT curr.
                    curr = (e.to == curr) ? e.from : e.to;
                }

                Collections.reverse(pathAnimations);
                algorithmSteps.addAll(pathAnimations);

                algorithmSteps.add(() -> {
                    startNode.circle.setFill(Color.GREEN);
                    startNode.distLabel.setFill(Color.WHITE);
                });
            }
        } else {
            algorithmSteps.add(() -> resultLabel.setText(
                    "Dijkstra: All reachable nodes processed. (No end node specified)"));
        }
    }

    // --- DAG Detection ---

    private boolean isDAG() {
        // An empty graph with the directed checkbox on counts as a valid DAG
        if (edges.isEmpty()) return directedCheck.isSelected();
        // Every edge must be directed
        if (edges.stream().anyMatch(e -> !e.isDirected)) return false;
        // Must have no cycles
        Set<GraphNode> visited  = new HashSet<>();
        Set<GraphNode> recStack = new HashSet<>();
        for (GraphNode node : nodes) {
            if (!visited.contains(node) && hasCycleDFS(node, visited, recStack)) return false;
        }
        return true;
    }

    private boolean hasCycleDFS(GraphNode node,
                                Set<GraphNode> visited,
                                Set<GraphNode> recStack) {
        visited.add(node);
        recStack.add(node);
        for (GraphEdge edge : node.connectedEdges) {
            if (edge.from != node) continue;          // follow directed edges only
            GraphNode neighbor = edge.to;
            if (!visited.contains(neighbor)) {
                if (hasCycleDFS(neighbor, visited, recStack)) return true;
            } else if (recStack.contains(neighbor)) {
                return true;                          // back-edge → cycle
            }
        }
        recStack.remove(node);
        return false;
    }

// --- Topological Sort (DFS) ---

    private void recordTopologicalSort() {
        Set<GraphNode> visited   = new HashSet<>();
        List<GraphNode> finished = new ArrayList<>();   // finish order → reversed = topo order

        algorithmSteps.add(() ->
                resultLabel.setText("Topological Sort: Running DFS to determine finish order..."));

        for (GraphNode node : nodes) {
            if (!visited.contains(node)) {
                topoSortHelper(node, visited, finished);
            }
        }

        // Reverse finish order → topological order
        Collections.reverse(finished);
        List<String> labels = new ArrayList<>();
        for (GraphNode n : finished) labels.add(n.label.getText());
        final String finalOrder = String.join(" → ", labels);

        // Highlight nodes left-to-right in topo order
        for (int i = 0; i < finished.size(); i++) {
            final GraphNode n        = finished.get(i);
            final String   orderSoFar = String.join(" → ", labels.subList(0, i + 1));
            algorithmSteps.add(() -> {
                n.circle.setFill(Color.ORANGE);
                resultLabel.setText("Topological Order: " + orderSoFar);
            });
        }

        algorithmSteps.add(() -> {
            for (GraphNode n : finished) n.circle.setFill(Color.GREEN);
            for (GraphEdge e : edges) e.line.setStroke(Color.BLACK);
            resultLabel.setText("Topological Order: " + finalOrder + " (Complete)");
        });
    }

    private void topoSortHelper(GraphNode node,
                                Set<GraphNode> visited,
                                List<GraphNode> finished) {
        visited.add(node);

        final GraphNode visiting = node;
        algorithmSteps.add(() -> {
            visiting.circle.setFill(Color.YELLOW);
            resultLabel.setText("Topological Sort: Visiting " + visiting.label.getText());
        });

        for (GraphEdge edge : node.connectedEdges) {
            if (edge.from != node) continue;           // directed edges only
            GraphNode neighbor = edge.to;
            if (!visited.contains(neighbor)) {
                final GraphEdge treeEdge = edge;
                algorithmSteps.add(() -> treeEdge.line.setStroke(Color.ORANGE));
                topoSortHelper(neighbor, visited, finished);
            }
        }

        // Node is fully explored → push onto result stack (represented as finished list)
        finished.add(node);
        algorithmSteps.add(() -> {
            visiting.circle.setFill(Color.MAGENTA);
            resultLabel.setText("Topological Sort: Finished " + visiting.label.getText()
                    + " → pushed to stack");
        });
    }
}