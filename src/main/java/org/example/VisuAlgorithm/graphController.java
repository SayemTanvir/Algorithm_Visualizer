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

        // Clear selections whenever the user switches between tools
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

        // Setup Algorithm Dropdown
        if (algoComboBox != null) {
            algoComboBox.getItems().addAll(
                    "BFS (Breadth-First Search)",
                    "DFS (Depth-First Search)",
                    "Dijkstra's Shortest Path",
                    "Prim's MST",
                    "Kruskal's MST"
            );
        }
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
        List<GraphEdge> connectedEdges = new ArrayList<>(); // Track only connected edges for performance

        GraphNode(double x, double y, String value) {
            circle = new Circle(x, y, 20, Color.LIGHTBLUE);
            circle.setStroke(Color.BLACK);
            circle.setStrokeWidth(2);

            label = new Text(value);
            label.setMouseTransparent(true);

            // Use JavaFX bindings to keep label centered automatically (eliminates drag lag)
            Platform.runLater(() -> {
                label.xProperty().bind(circle.centerXProperty().subtract(label.getLayoutBounds().getWidth() / 2));
                label.yProperty().bind(circle.centerYProperty().add(label.getLayoutBounds().getHeight() / 4));
            });

            enableDrag();
        }

        void enableDrag() {
            circle.setOnMousePressed(e -> {
                offsetX = circle.getCenterX() - e.getSceneX();
                offsetY = circle.getCenterY() - e.getSceneY();

                // FIX: Do not trigger standard selection/clearing if trying to draw an edge.
                // This prevents the first node from being wiped from memory.
                if (!edgeTool.isSelected()) {
                    selectNode(this);
                }
                e.consume();
            });

            circle.setOnMouseDragged(e -> {
                // Optional: You can wrap this in `if (!edgeTool.isSelected())` if you
                // want to disable dragging nodes while the edge tool is active.
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
        // Target either the fully selected node (red) or the active edge-starting node (orange)
        GraphNode nodeToDelete = selectedNode != null ? selectedNode : firstEdgeNode;

        if (nodeToDelete != null) {
            List<GraphEdge> toRemove = new ArrayList<>(nodeToDelete.connectedEdges);
            undoStack.push(new DeleteCommand(nodeToDelete, new ArrayList<>(toRemove)));
            toRemove.forEach(this::removeEdgeInternal);
            removeNodeInternal(nodeToDelete);

            // Clear both selection states so we don't accidentally draw from a ghost node
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
        canvasPane.getChildren().removeAll(node.circle, node.label);
    }

    private void restoreNodeInternal(GraphNode node) {
        if (!nodes.contains(node)) nodes.add(node);
        if (!canvasPane.getChildren().contains(node.circle)) {
            canvasPane.getChildren().addAll(node.circle, node.label);
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
            // Insert edges at index 0 so they dynamically stay behind nodes
            int index = 0;
            canvasPane.getChildren().add(index++, edge.line);
            if (edge.isDirected) canvasPane.getChildren().add(index++, edge.arrowHead);
            if (edge.isWeighted) canvasPane.getChildren().add(index, edge.weightText);
        }

        edge.update();
    }

    @FXML
    public void generateRandomGraph() {
        // 1. Clear the canvas for a clean slate
        clearGraph();

        Random random = new Random();

        // Use canvas dimensions, or fall back to defaults
        double width = canvasPane.getWidth() > 0 ? canvasPane.getWidth() : 600;
        double height = canvasPane.getHeight() > 0 ? canvasPane.getHeight() : 400;

        // 2. Generate a clean number of nodes (e.g., 5 to 8)
        int numNodes = random.nextInt(4) + 5;

        // --- NEW: Circular Layout Math ---
        double centerX = width / 2;
        double centerY = height / 2;
        // Radius leaves a 50px margin from the edges
        double radius = Math.min(centerX, centerY) - 50;
        double angleStep = 2 * Math.PI / numNodes;

        for (int i = 0; i < numNodes; i++) {
            // Distribute nodes evenly around the circle
            double x = centerX + radius * Math.cos(i * angleStep);
            double y = centerY + radius * Math.sin(i * angleStep);

            GraphNode node = new GraphNode(x, y, String.valueOf(nodeCounter++));
            restoreNodeInternal(node);
        }

        // 3. Generate Edges (Spanning tree to ensure it's fully connected)
        boolean isDirected = directedCheck.isSelected();
        boolean isWeighted = weightedCheck.isSelected();

        List<GraphNode> connected = new ArrayList<>();
        List<GraphNode> unconnected = new ArrayList<>(nodes);

        // Start the tree with a random node
        connected.add(unconnected.remove(random.nextInt(unconnected.size())));

        while (!unconnected.isEmpty()) {
            GraphNode from = connected.get(random.nextInt(connected.size()));
            GraphNode to = unconnected.remove(random.nextInt(unconnected.size()));

            int weight = isWeighted ? random.nextInt(20) + 1 : 1;
            GraphEdge edge = new GraphEdge(from, to, weight, isDirected, isWeighted);
            restoreEdgeInternal(edge);
            connected.add(to);
        }

        // 4. Add a VERY small number of extra edges to keep it clean
        // Max 2 extra edges to prevent the "messy" look
        int extraEdges = random.nextInt(3);
        for (int i = 0; i < extraEdges; i++) {
            GraphNode from = nodes.get(random.nextInt(nodes.size()));
            GraphNode to = nodes.get(random.nextInt(nodes.size()));

            if (from != to) {
                boolean exists = edges.stream().anyMatch(e ->
                        (e.from == from && e.to == to) ||
                                (!isDirected && e.from == to && e.to == from)
                );

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
        undoStack.clear(); // Reset undo history for the new graph
        nodeCounter = 1;
        clearSelection();
    }

    @FXML private Button backButton;

    @FXML
    private void handleBackButton(ActionEvent event) throws IOException {
        stopAll(); // Stop any running algorithms or animations before switching scenes

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("hello-view.fxml"));
        Parent root = fxmlLoader.load();

        // Get the current stage from the button click event
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.getScene().setRoot(root);
    }

    private void stopAll() {
        // TODO: Add your cleanup logic here.
        // For example: stop timeline animations, cancel background threads, or clear data.
        clearSelection();
    }

    //Algorithms
    // --- UI Mode Panels ---
    @FXML private ToolBar buildToolbar;
    @FXML private ToolBar algoToolbar;
    @FXML private ToolBar playbackToolbar;

    // --- Algorithm Controls ---
    // --- Algorithm Controls ---
    @FXML private ComboBox<String> algoComboBox;
    @FXML private TextField startNodeField;
    @FXML private TextField endNodeField;
    @FXML private Slider speedSlider;
    @FXML private Button playPauseButton;

    // A flag to prevent drawing/dragging while algorithms run
    private boolean isAlgorithmMode = false;

    @FXML
    private void switchToAlgoMode() {
        if (nodes.isEmpty()) {
            System.out.println("Graph is empty! Build a graph first.");
            return;
        }

        isAlgorithmMode = true;
        clearSelection();

        // Hide Build tools
        buildToolbar.setVisible(false);

        // Show Top Algo Setup & Bottom Playback Controls
        algoToolbar.setVisible(true);
        playbackToolbar.setVisible(true);
        playbackToolbar.setManaged(true);
    }

    @FXML
    private void switchToBuildMode() {
        isAlgorithmMode = false;

        resetAlgorithmState(); // Stop animations and clear colors

        // Hide Top Algo Setup & Bottom Playback Controls
        algoToolbar.setVisible(false);
        playbackToolbar.setVisible(false);
        playbackToolbar.setManaged(false);

        // Show Build tools
        buildToolbar.setVisible(true);
    }

    @FXML
    private void resetGraphColors() {
        // Restores all nodes and edges to black/lightblue
        for (GraphNode n : nodes) {
            n.circle.setFill(Color.LIGHTBLUE);
            n.circle.setStroke(Color.BLACK);
        }
        for (GraphEdge e : edges) {
            e.line.setStroke(Color.BLACK);
            // If you added color to arrowheads, reset them here too
        }
    }

    //BFS
    @FXML private Label resultLabel;

    // --- Animation State ---
    private Timeline timeline;
    private final List<Runnable> algorithmSteps = new ArrayList<>();
    private int currentStep = 0;

    // ===============================
    // ALGORITHMS & ANIMATION
    // ===============================

    private GraphNode findNodeByValue(String value) {
        if (value == null || value.isEmpty()) return null;
        for (GraphNode node : nodes) {
            if (node.label.getText().equals(value)) {
                return node;
            }
        }
        return null; // Not found
    }

    // ===============================
    // ALGORITHMS & ANIMATION
    // ===============================

    private void initializeAlgorithm() {
        // Stops animation, resets colors, and calculates the whole algorithm in the background
        if (timeline != null) timeline.stop();
        resetGraphColors();
        resultLabel.setText("Traversal Order: ");
        algorithmSteps.clear();
        currentStep = 0;

        GraphNode startNode = findNodeByValue(startNodeField.getText());
        if (startNode == null) startNode = nodes.get(0);

        String selectedAlgo = algoComboBox.getValue();
        if (selectedAlgo != null) {
            if (selectedAlgo.startsWith("BFS")) {
                recordBFS(startNode);
            }
//            else if (selectedAlgo.startsWith("DFS")) {
//                recordDFS(startNode);
//            }
        }
    }

    private void setupTimeline() {
        timeline = new Timeline(new KeyFrame(Duration.seconds(1.0), event -> {
            if (currentStep < algorithmSteps.size()) {
                algorithmSteps.get(currentStep).run();
                currentStep++;
            } else {
                // Algorithm finished
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

        // If it's currently running, PAUSE it
        if (timeline != null && timeline.getStatus() == javafx.animation.Animation.Status.RUNNING) {
            timeline.pause();
            playPauseButton.setText("▶ Play");
            return;
        }

        // If it was finished or hasn't started, initialize it
        if (algorithmSteps.isEmpty() || currentStep >= algorithmSteps.size()) {
            initializeAlgorithm();
        }

        if (timeline == null) {
            setupTimeline();
        }

        // PLAY it
        timeline.play();
        playPauseButton.setText("⏸ Pause");
    }

    @FXML
    private void stepForward() {
        if (nodes.isEmpty() || algoComboBox.getValue() == null) return;

        // Pause automatic playback if user decides to manually step
        if (timeline != null) {
            timeline.pause();
            playPauseButton.setText("▶ Play");
        }

        if (algorithmSteps.isEmpty() || currentStep >= algorithmSteps.size()) {
            initializeAlgorithm();
        }

        if (currentStep < algorithmSteps.size()) {
            algorithmSteps.get(currentStep).run();
            currentStep++;
        }
    }

    @FXML
    private void stepBackward() {
        if (algorithmSteps.isEmpty() || currentStep <= 0) return;

        // Pause automatic playback
        if (timeline != null) {
            timeline.pause();
            playPauseButton.setText("▶ Play");
        }

        // Move the step counter back by one
        currentStep--;

        // The "Scrubbing" Trick:
        // Reset the graph instantly, then fast-forward a loop to the exact previous step
        resetGraphColors();
        resultLabel.setText("Traversal Order: ");
        for (int i = 0; i < currentStep; i++) {
            algorithmSteps.get(i).run();
        }
    }

    @FXML
    private void resetAlgorithmState() {
        if (timeline != null) timeline.stop();
        if (playPauseButton != null) playPauseButton.setText("▶ Play");
        resetGraphColors();
        resultLabel.setText("Traversal Order: ");
        algorithmSteps.clear();
        currentStep = 0;
    }

    // --- The BFS Logic ---
    // --- The Updated BFS Logic ---
    private void recordBFS(GraphNode startNode) {
        Set<GraphNode> visited = new HashSet<>();
        Queue<GraphNode> queue = new LinkedList<>();
        List<String> visitedOrder = new ArrayList<>();

        queue.add(startNode);
        visited.add(startNode);

        // Step 1: Mark Start Node as Discovered/Waiting (Yellow)
        algorithmSteps.add(() -> startNode.circle.setFill(Color.YELLOW));

        while (!queue.isEmpty()) {
            GraphNode current = queue.poll();

            // Step 2: Mark current node as Actively Exploring (Magenta) and update text
            visitedOrder.add(current.label.getText());
            final String currentPath = "Traversal Order: " + String.join(" ➔ ", visitedOrder);
            final GraphNode exploringNode = current;

            algorithmSteps.add(() -> {
                exploringNode.circle.setFill(Color.MAGENTA);
                resultLabel.setText(currentPath);
            });

            for (GraphEdge edge : current.connectedEdges) {
                // Determine the neighbor based on directed/undirected rules
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

                    // Step 3: Animate Traversing the Edge (Orange) and Discovered Node (Yellow)
                    algorithmSteps.add(() -> {
                        traversedEdge.line.setStroke(Color.ORANGE);
                        nextNode.circle.setFill(Color.YELLOW);
                    });
                }
            }

            // Step 4: Mark the current node as Done (Green) AFTER all neighbors are checked
            algorithmSteps.add(() -> exploringNode.circle.setFill(Color.GREEN));
        }
    }

}