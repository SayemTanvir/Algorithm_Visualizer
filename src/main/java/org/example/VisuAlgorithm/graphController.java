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

        // Setup Algorithm Dropdown with Custom CellFactory
        if (algoComboBox != null) {
            algoComboBox.getItems().addAll(
                    "BFS (Breadth-First Search)",
                    "DFS (Depth-First Search)",
                    "Dijkstra's Shortest Path",
                    "Prim's MST",
                    "Kruskal's MST"
            );

            // This factory controls how each item in the dropdown list looks and behaves
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

                        // Define which algorithms require fully weighted graphs
                        boolean requiresWeight = item.contains("Dijkstra") ||
                                item.contains("Prim") ||
                                item.contains("Kruskal");

                        boolean hasUnweightedEdges = edges.stream().anyMatch(e -> !e.isWeighted);

                        // Disable and grey out if it's a weighted algo and the graph has unweighted edges
                        if (requiresWeight && hasUnweightedEdges) {
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

            // Use JavaFX bindings to keep label centered automatically (eliminates drag lag)
            Platform.runLater(() -> {
                distLabel.xProperty().bind(circle.centerXProperty().subtract(distLabel.getLayoutBounds().getWidth() / 2));
                distLabel.yProperty().bind(circle.centerYProperty().subtract(circle.getRadius() + 5));
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
        canvasPane.getChildren().removeAll(node.circle, node.label, node.distLabel); // + distLabel
    }

    private void restoreNodeInternal(GraphNode node) {
        if (!nodes.contains(node)) nodes.add(node);
        if (!canvasPane.getChildren().contains(node.circle)) {
            canvasPane.getChildren().addAll(node.circle, node.label, node.distLabel); // + distLabel
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

        boolean hasUnweightedEdges = edges.stream().anyMatch(e -> !e.isWeighted);

        // --- NEW: Force the ComboBox to refresh its cells ---
        // This makes JavaFX re-evaluate the graph's edges and update the greyed-out text
        List<String> currentItems = new ArrayList<>(algoComboBox.getItems());
        algoComboBox.getItems().clear();
        algoComboBox.getItems().addAll(currentItems);

        // Safety Check: Clear selection if an invalid algorithm was previously selected
        String currentSelection = algoComboBox.getValue();
        if (currentSelection != null && hasUnweightedEdges &&
                (currentSelection.contains("Dijkstra") || currentSelection.contains("Prim") || currentSelection.contains("Kruskal"))) {
            algoComboBox.setValue(null);
        }

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
            n.distLabel.setText("∞");
            n.distLabel.setFill(Color.DARKRED);
            n.distLabel.setVisible(false);
        }
        for (GraphEdge e : edges) {
            e.line.setStroke(Color.BLACK);
            e.line.setStrokeWidth(3);
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
        GraphNode endNode = findNodeByValue(endNodeField.getText());

        String selectedAlgo = algoComboBox.getValue();
        if (selectedAlgo != null) {
            if (selectedAlgo.startsWith("BFS")) {
                recordBFS(startNode);
            }
            else if (selectedAlgo.startsWith("DFS")) {
                recordDFS(startNode);
            }
            else if (selectedAlgo.startsWith("Prim")) {
                if (edges.stream().anyMatch(e -> !e.isWeighted)) {
                    resultLabel.setText("Error: Prim's MST requires a fully weighted graph!");
                    return;
                }
                recordPrim(startNode);
            }
            else if (selectedAlgo.startsWith("Kruskal")) {
                if (edges.stream().anyMatch(e -> !e.isWeighted)) {
                    resultLabel.setText("Error: Kruskal's MST requires a fully weighted graph!");
                    return;
                }
                recordKruskal();
            }
            else if (selectedAlgo.startsWith("Dijkstra")) { // Hooked up Dijkstra here!
                if (edges.stream().anyMatch(e -> !e.isWeighted)) {
                    resultLabel.setText("Error: Dijkstra requires a fully weighted graph!");
                    return;
                }
                recordDijkstra(startNode, endNode);
            }
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

    // --- The True Recursive DFS Logic ---
    private void recordDFS(GraphNode startNode) {
        Set<GraphNode> visited = new HashSet<>();
        List<String> visitedOrder = new ArrayList<>();

        // Start the recursive recording
        dfsHelper(startNode, null, visited, visitedOrder);
    }

    private void dfsHelper(GraphNode current, GraphEdge edgeToReach, Set<GraphNode> visited, List<String> visitedOrder) {
        // Mark as visited the moment we enter the node
        visited.add(current);

        // Step 1: Animate the arrival
        if (edgeToReach != null) {
            // If we traveled an edge to get here, turn the edge Orange and the node Yellow
            final GraphEdge traversedEdge = edgeToReach;
            final GraphNode nextNode = current;
            algorithmSteps.add(() -> {
                traversedEdge.line.setStroke(Color.ORANGE);
                nextNode.circle.setFill(Color.YELLOW);
            });
        } else {
            // If it's the very first starting node, just turn it Yellow
            algorithmSteps.add(() -> current.circle.setFill(Color.YELLOW));
        }

        // Step 2: Mark current node as Actively Exploring (Magenta) and update the text
        visitedOrder.add(current.label.getText());
        final String currentPath = "Traversal Order: " + String.join(" ➔ ", visitedOrder);
        final GraphNode exploringNode = current;

        algorithmSteps.add(() -> {
            exploringNode.circle.setFill(Color.MAGENTA);
            resultLabel.setText(currentPath);
        });

        // Step 3: Check neighbors and DIVE DEEP immediately
        for (GraphEdge edge : current.connectedEdges) {
            // Determine the neighbor based on directed/undirected rules
            GraphNode neighbor = null;
            if (edge.from == current) {
                neighbor = edge.to;
            } else if (!edge.isDirected && edge.to == current) {
                neighbor = edge.from;
            }

            // If the neighbor is unvisited, immediately pause this node and dive into the neighbor
            if (neighbor != null && !visited.contains(neighbor)) {
                dfsHelper(neighbor, edge, visited, visitedOrder);
            }
        }

        // Step 4: All neighbors checked. Mark this node as completely Done (Green)
        algorithmSteps.add(() -> exploringNode.circle.setFill(Color.GREEN));
    }

    // --- Prim's MST Logic ---
    private void recordPrim(GraphNode startNode) {
        Set<GraphNode> visited = new HashSet<>();

        // Priority Queue comparing edges by their parsed weight
        PriorityQueue<GraphEdge> pq = new PriorityQueue<>(Comparator.comparingInt(e -> {
            if (!e.isWeighted) return 1; // Default weight if unweighted
            try {
                return Integer.parseInt(e.weightText.getText());
            } catch (NumberFormatException ex) {
                return 1;
            }
        }));

        // Step 1: Start at the selected node
        visited.add(startNode);
        algorithmSteps.add(() -> {
            startNode.circle.setFill(Color.YELLOW);
            resultLabel.setText("Prim's MST: Started at " + startNode.label.getText() + " (Weight: 0)");
        });

        // Add all edges from the starting node to the PQ
        for (GraphEdge edge : startNode.connectedEdges) {
            pq.add(edge);
        }

        // Use an array to keep a mutable total weight for the lambda closures
        int[] totalWeight = {0};

        while (!pq.isEmpty() && visited.size() < nodes.size()) {
            GraphEdge minEdge = pq.poll();

            // Find the unvisited end of this edge
            GraphNode unvisitedNode = null;
            if (visited.contains(minEdge.from) && !visited.contains(minEdge.to)) {
                unvisitedNode = minEdge.to;
            } else if (!minEdge.isDirected && visited.contains(minEdge.to) && !visited.contains(minEdge.from)) {
                unvisitedNode = minEdge.from;
            }

            // If we found a valid unvisited node, it becomes part of the MST
            if (unvisitedNode != null) {
                visited.add(unvisitedNode);

                int edgeWeight = minEdge.isWeighted ? Integer.parseInt(minEdge.weightText.getText()) : 1;
                totalWeight[0] += edgeWeight;

                final GraphNode nextNode = unvisitedNode;
                final GraphEdge mstEdge = minEdge;
                final int currentTotal = totalWeight[0];

                // Step 2: Animate adding the edge and node to the MST
                algorithmSteps.add(() -> {
                    mstEdge.line.setStroke(Color.ORANGE);
                    mstEdge.line.setStrokeWidth(5); // Make the MST edge thicker so it stands out
                    nextNode.circle.setFill(Color.YELLOW);
                    resultLabel.setText("Prim's MST Total Weight: " + currentTotal);
                });

                // Add the new node's edges to the PQ
                for (GraphEdge edge : nextNode.connectedEdges) {
                    GraphNode neighbor = (edge.from == nextNode) ? edge.to : (!edge.isDirected && edge.to == nextNode) ? edge.from : null;
                    if (neighbor != null && !visited.contains(neighbor)) {
                        pq.add(edge);
                    }
                }
            }
        }

        // Step 3: Algorithm complete, turn everything Green
        algorithmSteps.add(() -> {
            for (GraphNode node : visited) {
                node.circle.setFill(Color.GREEN);
            }
            resultLabel.setText(resultLabel.getText() + " (Complete)");
        });
    }

    // --- Kruskal's MST Logic ---
    private void recordKruskal() {
        // 1. Setup Disjoint Set (Union-Find) to detect cycles
        Map<GraphNode, GraphNode> parent = new HashMap<>();
        for (GraphNode node : nodes) {
            parent.put(node, node);
        }

        // Helper function for Union-Find: 'Find' with path compression
        java.util.function.Function<GraphNode, GraphNode> find = new java.util.function.Function<>() {
            @Override
            public GraphNode apply(GraphNode node) {
                if (parent.get(node) == node) {
                    return node;
                }
                GraphNode root = apply(parent.get(node));
                parent.put(node, root);
                return root;
            }
        };

        // 2. Get all edges and sort them globally by weight
        List<GraphEdge> sortedEdges = new ArrayList<>(edges);
        sortedEdges.sort(Comparator.comparingInt(e -> {
            if (!e.isWeighted) return 1;
            try {
                return Integer.parseInt(e.weightText.getText());
            } catch (NumberFormatException ex) {
                return 1;
            }
        }));

        algorithmSteps.add(() -> {
            resultLabel.setText("Kruskal's MST: Sorting all edges by weight...");
        });

        int[] totalWeight = {0};
        Set<GraphNode> mstNodes = new HashSet<>();
        int edgesAdded = 0;

        // 3. Iterate through the sorted edges
        for (GraphEdge edge : sortedEdges) {
            if (edgesAdded >= nodes.size() - 1) break; // MST is complete when we have V-1 edges

            GraphNode root1 = find.apply(edge.from);
            GraphNode root2 = find.apply(edge.to);

            // If the roots are different, adding this edge won't form a cycle (Union)
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

                // Animate adding the edge to the MST
                algorithmSteps.add(() -> {
                    mstEdge.line.setStroke(Color.ORANGE);
                    mstEdge.line.setStrokeWidth(5); // Make it thick like Prim's
                    u.circle.setFill(Color.YELLOW);
                    v.circle.setFill(Color.YELLOW);
                    resultLabel.setText("Kruskal's MST Total Weight: " + currentTotal);
                });
            }
        }

        // 4. Algorithm complete, turn all included nodes Green
        algorithmSteps.add(() -> {
            for (GraphNode node : mstNodes) {
                node.circle.setFill(Color.GREEN);
            }
            resultLabel.setText(resultLabel.getText() + " (Complete)");
        });
    }

    // --- Dijkstra's Shortest Path Logic ---
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

        // Initialize distances
        for (GraphNode node : nodes) {
            distances.put(node, Integer.MAX_VALUE);
        }
        distances.put(startNode, 0);
        pq.add(new NodeDist(startNode, 0));

        // Show all dist labels now that Dijkstra is running
        for (GraphNode node : nodes) {
            node.distLabel.setVisible(true);
        }

        // Step 1: Animate start node
        algorithmSteps.add(() -> {
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

            // Step 2: Animate actively exploring this node (Magenta)
            algorithmSteps.add(() -> {
                if (exploringNode != startNode) exploringNode.circle.setFill(Color.MAGENTA);
                exploringNode.distLabel.setFill(Color.DARKBLUE);
                resultLabel.setText("Dijkstra: Exploring " + exploringNode.label.getText()
                        + " (dist: " + currentDist + ")");
            });

            // Stop early if we reached the requested end node
            if (endNode != null && u == endNode) break;

            // Step 3: Check all neighbors
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

                        // Animate discovering a shorter path — update the dist label live
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

            // Mark node as settled (Light Green), lock in dist label color
            algorithmSteps.add(() -> {
                if (exploringNode != startNode && exploringNode != endNode) {
                    exploringNode.circle.setFill(Color.LIGHTGREEN);
                    exploringNode.distLabel.setFill(Color.DARKGREEN);
                }
            });
        }

        // Step 4: Reconstruct and animate the final shortest path
        if (endNode != null) {
            if (distances.get(endNode) == Integer.MAX_VALUE) {
                algorithmSteps.add(() -> resultLabel.setText(
                        "Dijkstra: No reachable path to " + endNode.label.getText() + "!"));
            } else {
                algorithmSteps.add(() -> resultLabel.setText(
                        "Dijkstra: Shortest path found! Total Dist: " + distances.get(endNode)));

                // Trace back the path from end to start
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

                    curr = (e.from == curr) ? e.to : e.from;
                }

                // Reverse so it draws Start → End
                Collections.reverse(pathAnimations);
                algorithmSteps.addAll(pathAnimations);

                // Make sure start node is green too
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
}