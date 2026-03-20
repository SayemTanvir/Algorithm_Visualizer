package org.example.VisuAlgorithm;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
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
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.*;

public class BSTController {

    // ── FXML Bindings ──────────────────────────────────────────────────────────
    @FXML private Pane       canvasPane;
    @FXML private ToolBar    buildToolbar;
    @FXML private ToolBar    algoToolbar;
    @FXML private ToolBar    playbackToolbar;
    @FXML private TextField  insertField;
    @FXML private ComboBox<String> algoComboBox;
    @FXML private TextField  startNodeField;
    @FXML private Slider     speedSlider;
    @FXML private Button     playPauseButton;
    @FXML private Label      resultLabel;
    @FXML private Button     backButton;

    // ── Tree state ─────────────────────────────────────────────────────────────
    private BSTNode root         = null;
    private BSTNode selectedNode = null;
    private final Stack<UndoCommand> undoStack = new Stack<>();

    // ── State snapshot variables to allow backward stepping over structural changes ──
    private class NodeState {
        int value;
        BSTNode left, right, parent;
        NodeState(BSTNode n) {
            this.value = n.value;
            this.left = n.left;
            this.right = n.right;
            this.parent = n.parent;
        }
    }

    private final Map<BSTNode, NodeState> treeSnapshot = new HashMap<>();
    private BSTNode snapshotRoot;

    // ── Layout constants ───────────────────────────────────────────────────────
    private static final double NODE_RADIUS = 22.0;
    private static final double H_SPACING   = 52.0;
    private static final double V_SPACING   = 72.0;
    private static final double MARGIN_LEFT = 44.0;
    private static final double MARGIN_TOP  = 48.0;

    // ── Algorithm playback state ───────────────────────────────────────────────
    private boolean isAlgorithmMode = false;
    private Timeline timeline;
    private final List<Runnable>  algorithmSteps    = new ArrayList<>();
    private final Set<Integer>    nonReplayableSteps = new HashSet<>();
    private int currentStep = 0;

    // ==========================================================================
    // INNER CLASS: BSTNode
    // ==========================================================================
    class BSTNode {
        int     value;
        BSTNode left, right, parent;

        Circle circle;
        Text   label;
        Line   edgeToParent;

        ParallelTransition exitAnimation; // Tracks active deletion animation

        BSTNode(int value) {
            this.value = value;

            circle = new Circle(NODE_RADIUS, Color.LIGHTBLUE);
            circle.setStroke(Color.BLACK);
            circle.setStrokeWidth(2);

            label = new Text(String.valueOf(value));
            label.setFont(Font.font("System", FontWeight.BOLD, 13));
            label.setMouseTransparent(true);

            edgeToParent = new Line();
            edgeToParent.setStroke(Color.DIMGRAY);
            edgeToParent.setStrokeWidth(2);
            edgeToParent.setMouseTransparent(true);

            circle.setOnMouseClicked(e -> {
                handleNodeClick(this);
                e.consume();
            });
        }
    }

    // ==========================================================================
    // UNDO SYSTEM
    // ==========================================================================
    private interface UndoCommand { void undo(); }

    private class InsertCommand implements UndoCommand {
        final int value;
        InsertCommand(int v) { this.value = v; }
        public void undo() { deleteValue(value); }
    }

    private class DeleteCommand implements UndoCommand {
        final int value;
        DeleteCommand(int v) { this.value = v; }
        public void undo() { insertValue(value); }
    }

    // ==========================================================================
    // INITIALISATION
    // ==========================================================================
    @FXML
    public void initialize() {
        algoComboBox.getItems().addAll(
                "Inorder Traversal  (L → N → R)",
                "Preorder Traversal (N → L → R)",
                "Postorder Traversal (L → R → N)",
                "Level Order / BFS",
                "Search Value",
                "Find Predecessor",
                "Find Successor",
                "Delete (Visualized)",
                "Insert (Visualized)"
        );

        resultLabel.setText("");

        if (algoToolbar    != null) { algoToolbar.setVisible(false);    algoToolbar.setManaged(false);    }
        if (playbackToolbar != null) { playbackToolbar.setVisible(false); playbackToolbar.setManaged(false); }

        // Ctrl/Cmd + Z for undo
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
    }

    // ==========================================================================
    // BST CORE LOGIC
    // ==========================================================================

    private BSTNode insertBST(BSTNode node, BSTNode parent, int value) {
        if (node == null) {
            BSTNode n = new BSTNode(value);
            n.parent  = parent;
            return n;
        }
        if (value < node.value) {
            node.left = insertBST(node.left, node, value);
        } else if (value > node.value) {
            node.right = insertBST(node.right, node, value);
        }
        return node;
    }

    private BSTNode deleteBST(BSTNode node, int value) {
        if (node == null) return null;

        if (value < node.value) {
            node.left = deleteBST(node.left, value);
            if (node.left != null) node.left.parent = node;
        } else if (value > node.value) {
            node.right = deleteBST(node.right, value);
            if (node.right != null) node.right.parent = node;
        } else {
            if (node.left == null && node.right == null) {
                removeNodeFromCanvas(node);
                return null;
            } else if (node.left == null) {
                removeNodeFromCanvas(node);
                return node.right;
            } else if (node.right == null) {
                removeNodeFromCanvas(node);
                return node.left;
            } else {
                BSTNode successor = minNode(node.right);
                node.value = successor.value;
                node.label.setText(String.valueOf(node.value));
                node.right = deleteBST(node.right, successor.value);
                if (node.right != null) node.right.parent = node;
                return node;
            }
        }
        return node;
    }

    private BSTNode minNode(BSTNode node) {
        while (node.left != null) node = node.left;
        return node;
    }

    private boolean containsValue(BSTNode node, int value) {
        if (node == null) return false;
        if (value == node.value) return true;
        return value < node.value
                ? containsValue(node.left, value)
                : containsValue(node.right, value);
    }

    // ==========================================================================
    // TREE LAYOUT
    // ==========================================================================

    private void layoutTree() {
        if (root == null) return;
        int[] counter = {0};
        assignInorderX(root, counter);
        assignDepthY(root, 0);
        updateEdgesAndLabels(root);
    }

    private void assignInorderX(BSTNode node, int[] counter) {
        if (node == null) return;
        assignInorderX(node.left, counter);
        node.circle.setCenterX(MARGIN_LEFT + counter[0]++ * H_SPACING);
        assignInorderX(node.right, counter);
    }

    private void assignDepthY(BSTNode node, int depth) {
        if (node == null) return;
        node.circle.setCenterY(MARGIN_TOP + depth * V_SPACING);
        assignDepthY(node.left,  depth + 1);
        assignDepthY(node.right, depth + 1);
    }

    private void updateEdgesAndLabels(BSTNode node) {
        if (node == null) return;

        double cx = node.circle.getCenterX();
        double cy = node.circle.getCenterY();

        if (node.parent != null) {
            double px = node.parent.circle.getCenterX();
            double py = node.parent.circle.getCenterY();
            double dx = cx - px, dy = cy - py;
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len > 0) {
                node.edgeToParent.setStartX(px + (dx / len) * NODE_RADIUS);
                node.edgeToParent.setStartY(py + (dy / len) * NODE_RADIUS);
                node.edgeToParent.setEndX  (cx - (dx / len) * NODE_RADIUS);
                node.edgeToParent.setEndY  (cy - (dy / len) * NODE_RADIUS);
            }
        } else {
            canvasPane.getChildren().remove(node.edgeToParent);
        }

        Platform.runLater(() -> {
            node.label.setX(cx - node.label.getLayoutBounds().getWidth()  / 2.0);
            node.label.setY(cy + node.label.getLayoutBounds().getHeight() / 4.0);
        });

        updateEdgesAndLabels(node.left);
        updateEdgesAndLabels(node.right);
    }

    // ==========================================================================
    // CANVAS MANAGEMENT & ANIMATION
    // ==========================================================================

    private void addSubtreeToCanvas(BSTNode node) {
        if (node == null) return;

        // Cancel any pending exit animation if a node is quickly restored via undo/step backward
        if (node.exitAnimation != null) {
            node.exitAnimation.stop();
            node.exitAnimation = null;
        }

        // Ensure scale and opacity are reset in case it was previously animating out
        node.circle.setScaleX(1.0);
        node.circle.setScaleY(1.0);
        node.label.setScaleX(1.0);
        node.label.setScaleY(1.0);
        node.edgeToParent.setOpacity(1.0);
        node.circle.setMouseTransparent(false);

        if (!canvasPane.getChildren().contains(node.circle)) {
            if (node.parent != null
                    && !canvasPane.getChildren().contains(node.edgeToParent)) {
                canvasPane.getChildren().add(0, node.edgeToParent);
            }
            canvasPane.getChildren().addAll(node.circle, node.label);
        }
        addSubtreeToCanvas(node.left);
        addSubtreeToCanvas(node.right);
    }

    private void removeNodeFromCanvas(BSTNode node) {
        // Prevent interactions while dying
        node.circle.setMouseTransparent(true);

        // Shrink the circle
        ScaleTransition stCircle = new ScaleTransition(Duration.seconds(0.4), node.circle);
        stCircle.setToX(0);
        stCircle.setToY(0);

        // Shrink the text label
        ScaleTransition stLabel = new ScaleTransition(Duration.seconds(0.4), node.label);
        stLabel.setToX(0);
        stLabel.setToY(0);

        // Fade out the connecting edge
        FadeTransition ftEdge = new FadeTransition(Duration.seconds(0.4), node.edgeToParent);
        ftEdge.setToValue(0);

        // Play them all at once, then physically remove them from the canvas
        node.exitAnimation = new ParallelTransition(stCircle, stLabel, ftEdge);
        node.exitAnimation.setOnFinished(e -> {
            canvasPane.getChildren().removeAll(node.circle, node.label, node.edgeToParent);
            node.exitAnimation = null;
        });
        node.exitAnimation.play();
    }

    // ==========================================================================
    // SNAPSHOT SYSTEM FOR BACKWARD ANIMATION
    // ==========================================================================

    private void takeSnapshot(BSTNode node) {
        if (node == null) return;
        treeSnapshot.put(node, new NodeState(node));
        takeSnapshot(node.left);
        takeSnapshot(node.right);
    }

    private void saveTreeState() {
        treeSnapshot.clear();
        snapshotRoot = root;
        takeSnapshot(root);
    }

    private void restoreTreeState() {
        root = snapshotRoot;
        for (Map.Entry<BSTNode, NodeState> entry : treeSnapshot.entrySet()) {
            BSTNode n = entry.getKey();
            NodeState s = entry.getValue();
            n.value = s.value;
            n.label.setText(String.valueOf(n.value));
            n.left = s.left;
            n.right = s.right;
            n.parent = s.parent;
        }

        canvasPane.getChildren().clear();
        if (root != null) {
            addSubtreeToCanvas(root);
            layoutTree();
        }
    }

    // ==========================================================================
    // INSERT / DELETE
    // ==========================================================================

    private void insertValue(int value) {
        root = insertBST(root, null, value);
        addSubtreeToCanvas(root);
        Platform.runLater(this::layoutTree);
    }

    private void deleteValue(int value) {
        if (root == null) return;
        clearSelection();
        root = deleteBST(root, value);
        if (root != null) root.parent = null;
        Platform.runLater(this::layoutTree);
    }

    // ==========================================================================
    // NODE SELECTION
    // ==========================================================================

    private void handleNodeClick(BSTNode node) {
        if (isAlgorithmMode) return;
        clearSelection();
        selectedNode = node;
        node.circle.setStroke(Color.RED);
        node.circle.setStrokeWidth(3.5);
        resultLabel.setText("Selected node: " + node.value);
    }

    private void clearSelection() {
        if (selectedNode != null) {
            selectedNode.circle.setStroke(Color.BLACK);
            selectedNode.circle.setStrokeWidth(2);
            selectedNode = null;
        }
    }

    // ==========================================================================
    // FXML HANDLERS — BUILD MODE
    // ==========================================================================

    @FXML
    private void handleCanvasClick(MouseEvent event) {
        if (isAlgorithmMode) return;
        if (event.getTarget() == canvasPane) clearSelection();
    }

    @FXML
    private void handleInsert() {
        if (isAlgorithmMode) return;
        String text = insertField.getText().trim();
        if (text.isEmpty()) return;
        try {
            int value = Integer.parseInt(text);
            if (containsValue(root, value)) {
                resultLabel.setText("Value " + value + " already exists in the tree.");
                return;
            }
            insertValue(value);
            undoStack.push(new InsertCommand(value));
            resultLabel.setText("Inserted: " + value);
            insertField.clear();
        } catch (NumberFormatException e) {
            resultLabel.setText("Invalid input — please enter an integer.");
        }
    }

    @FXML
    private void deleteSelected() {
        if (isAlgorithmMode || selectedNode == null) return;
        int value = selectedNode.value;
        undoStack.push(new DeleteCommand(value));
        deleteValue(value);
        resultLabel.setText("Deleted: " + value);
    }

    private void handleUndo() {
        if (isAlgorithmMode) return;
        if (!undoStack.isEmpty()) {
            undoStack.pop().undo();
            clearSelection();
            resultLabel.setText("Undo.");
        }
    }

    @FXML
    public void generateRandomTree() {
        clearTree();
        Random rng   = new Random();
        int    count = rng.nextInt(5) + 7;
        Set<Integer> used = new LinkedHashSet<>();
        while (used.size() < count) used.add(rng.nextInt(90) + 10);
        for (int v : used) insertValue(v);
        resultLabel.setText("Random tree generated (" + count + " nodes).");
    }

    @FXML
    public void clearTree() {
        root         = null;
        selectedNode = null;
        canvasPane.getChildren().clear();
        undoStack.clear();
        algorithmSteps.clear();
        nonReplayableSteps.clear();
        currentStep = 0;
        treeSnapshot.clear();
        snapshotRoot = null;

        if (timeline != null) { timeline.stop(); timeline = null; }
        if (playPauseButton != null) playPauseButton.setText("▶ Play");
        resultLabel.setText("");
    }

    @FXML
    private void handleBackButton(ActionEvent event) throws IOException {
        stopAll();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("hello-view.fxml"));
        Parent root = loader.load();
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.getScene().setRoot(root);
    }

    private void stopAll() {
        resetAlgorithmState();
        clearSelection();
    }

    // ==========================================================================
    // MODE SWITCHING
    // ==========================================================================

    @FXML
    private void switchToAlgoMode() {
        if (root == null) {
            resultLabel.setText("Tree is empty — build a tree first.");
            return;
        }
        isAlgorithmMode = true;
        clearSelection();
        resultLabel.setText("Select an algorithm and press Play!");
        buildToolbar.setVisible(false);
        buildToolbar.setManaged(false);
        algoToolbar.setVisible(true);
        algoToolbar.setManaged(true);
        playbackToolbar.setVisible(true);
        playbackToolbar.setManaged(true);
    }

    @FXML
    private void switchToBuildMode() {
        isAlgorithmMode = false;
        resetAlgorithmState();
        algoToolbar.setVisible(false);
        algoToolbar.setManaged(false);
        playbackToolbar.setVisible(false);
        playbackToolbar.setManaged(false);
        buildToolbar.setVisible(true);
        buildToolbar.setManaged(true);
    }

    // ==========================================================================
    // COLOR RESET HELPERS
    // ==========================================================================

    private void resetAllColors() {
        resetNodeColors(root);
        resetEdgeColors(root);
    }

    private void resetNodeColors(BSTNode node) {
        if (node == null) return;
        node.circle.setFill(Color.LIGHTBLUE);
        node.circle.setStroke(Color.BLACK);
        node.circle.setStrokeWidth(2);
        resetNodeColors(node.left);
        resetNodeColors(node.right);
    }

    private void resetEdgeColors(BSTNode node) {
        if (node == null) return;
        if (node.parent != null) {
            node.edgeToParent.setStroke(Color.DIMGRAY);
            node.edgeToParent.setStrokeWidth(2);
        }
        resetEdgeColors(node.left);
        resetEdgeColors(node.right);
    }

    // ==========================================================================
    // ALGORITHM PLAYBACK ENGINE
    // ==========================================================================

    private void initializeAlgorithm() {
        if (timeline != null) { timeline.stop(); timeline = null; }
        resetAllColors();
        resultLabel.setText("Starting…");
        algorithmSteps.clear();
        nonReplayableSteps.clear();
        currentStep = 0;

        saveTreeState();

        if (root == null || algoComboBox.getValue() == null) return;

        String algo = algoComboBox.getValue();
        if (algo.startsWith("Inorder")) {
            List<String> visited = new ArrayList<>();
            recordInorder(root, visited);
            String finalOrder = String.join(" → ", visited);
            algorithmSteps.add(() -> resultLabel.setText("Inorder Complete: " + finalOrder));

        } else if (algo.startsWith("Preorder")) {
            List<String> visited = new ArrayList<>();
            recordPreorder(root, visited);
            String finalOrder = String.join(" → ", visited);
            algorithmSteps.add(() -> resultLabel.setText("Preorder Complete: " + finalOrder));

        } else if (algo.startsWith("Postorder")) {
            List<String> visited = new ArrayList<>();
            recordPostorder(root, visited);
            String finalOrder = String.join(" → ", visited);
            algorithmSteps.add(() -> resultLabel.setText("Postorder Complete: " + finalOrder));

        } else if (algo.startsWith("Level Order")) {
            recordLevelOrder();

        } else if (algo.startsWith("Search")) {
            String text = startNodeField.getText().trim();
            try {
                recordSearch(root, Integer.parseInt(text), new ArrayList<>());
            } catch (NumberFormatException e) {
                resultLabel.setText("Search: please enter a valid integer in the Value field.");
            }
        } else if (algo.startsWith("Find Predecessor")) {
            recordFindPredecessor();

        } else if (algo.startsWith("Find Successor")) {
            recordFindSuccessor();

        } else if (algo.startsWith("Delete (Visualized)")) {
            recordDeletion();

        } else if (algo.startsWith("Insert (Visualized)")) {
            recordInsertion();
        }
    }

    private void setupTimeline() {
        timeline = new Timeline(new KeyFrame(Duration.seconds(1.0), e -> {
            if (currentStep < algorithmSteps.size()) {
                algorithmSteps.get(currentStep++).run();
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
        if (root == null || algoComboBox.getValue() == null) return;

        if (timeline != null
                && timeline.getStatus() == javafx.animation.Animation.Status.RUNNING) {
            timeline.pause();
            playPauseButton.setText("▶ Play");
            return;
        }

        if (algorithmSteps.isEmpty() || currentStep >= algorithmSteps.size()) {
            initializeAlgorithm();
        }
        if (timeline == null) setupTimeline();

        timeline.play();
        playPauseButton.setText("⏸ Pause");
    }

    @FXML
    private void stepForward() {
        if (root == null || algoComboBox.getValue() == null) return;
        if (timeline != null) { timeline.pause(); playPauseButton.setText("▶ Play"); }

        if (algorithmSteps.isEmpty() || currentStep >= algorithmSteps.size()) {
            initializeAlgorithm();
            if (timeline == null) setupTimeline();
        }
        if (currentStep < algorithmSteps.size()) {
            algorithmSteps.get(currentStep++).run();
        }
    }

    @FXML
    private void stepBackward() {
        if (algorithmSteps.isEmpty() || currentStep <= 0) return;
        if (timeline != null) { timeline.pause(); playPauseButton.setText("▶ Play"); }

        currentStep--;

        if (nonReplayableSteps.contains(currentStep)) {
            if (!undoStack.isEmpty()) {
                undoStack.pop();
            }
        }

        restoreTreeState();

        resetAllColors();
        resultLabel.setText("");

        for (int i = 0; i < currentStep; i++) {
            if (!nonReplayableSteps.contains(i)) {
                algorithmSteps.get(i).run();
            }
        }
    }

    @FXML
    private void resetAlgorithmState() {
        if (timeline != null) { timeline.stop(); timeline = null; }
        if (playPauseButton != null) playPauseButton.setText("▶ Play");
        resetAllColors();
        resultLabel.setText(isAlgorithmMode ? "Select an algorithm and press Play!" : "");
        algorithmSteps.clear();
        nonReplayableSteps.clear();
        currentStep = 0;
    }

    // ==========================================================================
    // ALGORITHM RECORDING
    // ==========================================================================

    private void recordInorder(BSTNode node, List<String> visited) {
        if (node == null) return;
        final BSTNode cur = node;

        // 1. Arrive and Traverse Left
        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.GOLD);
            if (cur.parent != null) cur.edgeToParent.setStroke(Color.GOLD);
            resultLabel.setText("Inorder: At " + cur.value + " — descending LEFT");
        });

        recordInorder(node.left, visited);

        // 2. VISIT the node (N)
        visited.add(String.valueOf(node.value));
        final String order = "Inorder: " + String.join(" → ", visited);
        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.MAGENTA);
            cur.circle.setStroke(Color.WHITE);
            cur.circle.setStrokeWidth(3);
            resultLabel.setText(order + "  (Visited " + cur.value + ")");
        });

        // 3. Traverse Right
        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.ORANGE);
            cur.circle.setStroke(Color.BLACK);
            cur.circle.setStrokeWidth(2);
            resultLabel.setText("Inorder: " + cur.value + " visited — descending RIGHT");
        });

        recordInorder(node.right, visited);

        // 4. Completed
        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.LIGHTGRAY);
            if (cur.parent != null) cur.edgeToParent.setStroke(Color.LIGHTGRAY);
        });
    }

    private void recordPreorder(BSTNode node, List<String> visited) {
        if (node == null) return;
        final BSTNode cur = node;

        // 1. VISIT the node immediately (N)
        visited.add(String.valueOf(node.value));
        final String order = "Preorder: " + String.join(" → ", visited);
        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.MAGENTA);
            cur.circle.setStroke(Color.WHITE);
            cur.circle.setStrokeWidth(3);
            if (cur.parent != null) cur.edgeToParent.setStroke(Color.MAGENTA);
            resultLabel.setText(order + "  (Visited " + cur.value + ")");
        });

        // 2. Traverse Left
        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.GOLD);
            cur.circle.setStroke(Color.BLACK);
            cur.circle.setStrokeWidth(2);
            resultLabel.setText("Preorder: Descending LEFT from " + cur.value);
        });

        recordPreorder(node.left, visited);

        // 3. Traverse Right
        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.ORANGE);
            resultLabel.setText("Preorder: Descending RIGHT from " + cur.value);
        });

        recordPreorder(node.right, visited);

        // 4. Completed
        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.LIGHTGRAY);
            if (cur.parent != null) cur.edgeToParent.setStroke(Color.LIGHTGRAY);
        });
    }

    private void recordPostorder(BSTNode node, List<String> visited) {
        if (node == null) return;
        final BSTNode cur = node;

        // 1. Arrive and Traverse Left
        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.GOLD);
            if (cur.parent != null) cur.edgeToParent.setStroke(Color.GOLD);
            resultLabel.setText("Postorder: At " + cur.value + " — descending LEFT");
        });

        recordPostorder(node.left, visited);

        // 2. Traverse Right
        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.ORANGE);
            resultLabel.setText("Postorder: At " + cur.value + " — descending RIGHT");
        });

        recordPostorder(node.right, visited);

        // 3. VISIT the node last (N)
        visited.add(String.valueOf(node.value));
        final String order = "Postorder: " + String.join(" → ", visited);
        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.MAGENTA);
            cur.circle.setStroke(Color.WHITE);
            cur.circle.setStrokeWidth(3);
            resultLabel.setText(order + "  (Visited " + cur.value + ")");
        });

        // 4. Completed
        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.LIGHTGRAY);
            cur.circle.setStroke(Color.BLACK);
            cur.circle.setStrokeWidth(2);
            if (cur.parent != null) cur.edgeToParent.setStroke(Color.LIGHTGRAY);
        });
    }

    private void recordLevelOrder() {
        Queue<BSTNode>  queue   = new LinkedList<>();
        List<String>    visited = new ArrayList<>();
        queue.add(root);

        algorithmSteps.add(() -> {
            root.circle.setFill(Color.YELLOW);
            resultLabel.setText("Level Order: enqueue root (" + root.value + ")");
        });

        while (!queue.isEmpty()) {
            BSTNode cur = queue.poll();
            visited.add(String.valueOf(cur.value));
            final String  order      = "Level Order: " + String.join(" → ", visited);
            final BSTNode processing = cur;

            algorithmSteps.add(() -> {
                processing.circle.setFill(Color.MAGENTA);
                if (processing.parent != null)
                    processing.edgeToParent.setStroke(Color.ORANGE);
                resultLabel.setText(order);
            });

            if (cur.left != null) {
                queue.add(cur.left);
                final BSTNode lc = cur.left;
                algorithmSteps.add(() -> {
                    lc.circle.setFill(Color.YELLOW);
                    resultLabel.setText("Level Order: enqueue left child " + lc.value);
                });
            }
            if (cur.right != null) {
                queue.add(cur.right);
                final BSTNode rc = cur.right;
                algorithmSteps.add(() -> {
                    rc.circle.setFill(Color.YELLOW);
                    resultLabel.setText("Level Order: enqueue right child " + rc.value);
                });
            }

            algorithmSteps.add(() -> processing.circle.setFill(Color.GREEN));
        }

        final String finalOrder = String.join(" → ", visited);
        algorithmSteps.add(() ->
                resultLabel.setText("Level Order complete: " + finalOrder));
    }

    private void recordSearch(BSTNode node, int target, List<String> path) {
        if (node == null) {
            final String pathStr = String.join(" → ", path);
            algorithmSteps.add(() ->
                    resultLabel.setText("✘ " + target + " NOT FOUND. Path taken: " + pathStr));
            return;
        }

        final BSTNode cur = node;
        path.add(String.valueOf(node.value));
        final String pathSoFar = String.join(" → ", path);

        if (target == node.value) {
            algorithmSteps.add(() -> {
                cur.circle.setFill(Color.LIMEGREEN);
                cur.circle.setStrokeWidth(4);
                if (cur.parent != null) cur.edgeToParent.setStroke(Color.LIMEGREEN);
                resultLabel.setText("✔ Found " + target + "!  Path: " + pathSoFar);
            });

        } else if (target < node.value) {
            algorithmSteps.add(() -> {
                cur.circle.setFill(Color.YELLOW);
                if (cur.parent != null) cur.edgeToParent.setStroke(Color.ORANGE);
                resultLabel.setText("Search: " + target + " < " + cur.value
                        + " → go LEFT   (path: " + pathSoFar + ")");
            });
            algorithmSteps.add(() -> cur.circle.setFill(Color.LIGHTGRAY));
            recordSearch(node.left, target, path);

        } else {
            algorithmSteps.add(() -> {
                cur.circle.setFill(Color.YELLOW);
                if (cur.parent != null) cur.edgeToParent.setStroke(Color.ORANGE);
                resultLabel.setText("Search: " + target + " > " + cur.value
                        + " → go RIGHT  (path: " + pathSoFar + ")");
            });
            algorithmSteps.add(() -> cur.circle.setFill(Color.LIGHTGRAY));
            recordSearch(node.right, target, path);
        }
    }

    private List<BSTNode> getPathTo(int target) {
        List<BSTNode> path = new ArrayList<>();
        BSTNode cur = root;
        while (cur != null) {
            path.add(cur);
            if (cur.value == target) break;
            cur = target < cur.value ? cur.left : cur.right;
        }
        return path;
    }

    private void recordNavigateTo(List<BSTNode> path, int target, String action) {
        for (int i = 0; i < path.size(); i++) {
            final BSTNode node     = path.get(i);
            final boolean isTarget = (node.value == target);
            algorithmSteps.add(() -> {
                node.circle.setFill(isTarget ? Color.CYAN : Color.YELLOW);
                if (node.parent != null) node.edgeToParent.setStroke(Color.ORANGE);
                resultLabel.setText(isTarget
                        ? action + " " + target + ": node found!"
                        : action + " " + target + ": at " + node.value
                        + (target < node.value ? "  →  go LEFT" : "  →  go RIGHT"));
            });
            if (!isTarget) {
                final BSTNode n = node;
                algorithmSteps.add(() -> n.circle.setFill(Color.LIGHTGRAY));
            }
        }
    }

    private void recordNavigateAndNotFound(List<BSTNode> path, int target, String algo) {
        for (BSTNode node : path) {
            final BSTNode n = node;
            algorithmSteps.add(() -> {
                n.circle.setFill(Color.YELLOW);
                if (n.parent != null) n.edgeToParent.setStroke(Color.ORANGE);
                resultLabel.setText(algo + ": searching for " + target + " — at " + n.value);
            });
            final BSTNode dim = node;
            algorithmSteps.add(() -> dim.circle.setFill(Color.LIGHTGRAY));
        }
        algorithmSteps.add(() ->
                resultLabel.setText("✘ " + target + " not found — cannot compute " + algo + "."));
    }

    private void recordFindPredecessor() {
        String text = startNodeField.getText().trim();
        int target;
        try { target = Integer.parseInt(text); }
        catch (NumberFormatException e) {
            resultLabel.setText("Predecessor: enter an integer in the Value field.");
            return;
        }

        List<BSTNode> path = getPathTo(target);
        if (path.isEmpty() || path.get(path.size() - 1).value != target) {
            recordNavigateAndNotFound(path, target, "Predecessor");
            return;
        }

        recordNavigateTo(path, target, "Predecessor of");
        BSTNode targetNode = path.get(path.size() - 1);

        if (targetNode.left != null) {
            final BSTNode lc = targetNode.left;
            algorithmSteps.add(() -> {
                lc.circle.setFill(Color.YELLOW);
                lc.edgeToParent.setStroke(Color.ORANGE);
                resultLabel.setText("Left subtree exists → predecessor = rightmost node in left subtree");
            });
            BSTNode cur = targetNode.left;
            while (cur.right != null) {
                final BSTNode next = cur.right;
                algorithmSteps.add(() -> {
                    next.circle.setFill(Color.YELLOW);
                    next.edgeToParent.setStroke(Color.ORANGE);
                    resultLabel.setText("Keep going right → " + next.value);
                });
                cur = cur.right;
            }
            final BSTNode pred = cur;
            algorithmSteps.add(() -> {
                pred.circle.setFill(Color.LIMEGREEN);
                pred.circle.setStrokeWidth(4);
                resultLabel.setText("✔  Predecessor of " + target + "  is  " + pred.value);
            });

        } else {
            algorithmSteps.add(() ->
                    resultLabel.setText("No left subtree → walk up ancestors until we arrive from the RIGHT"));

            BSTNode cur = targetNode;
            BSTNode anc = targetNode.parent;
            while (anc != null && cur == anc.left) {
                final BSTNode c = cur, a = anc;
                algorithmSteps.add(() -> {
                    c.circle.setFill(Color.LIGHTGRAY);
                    a.circle.setFill(Color.YELLOW);
                    if (a.parent != null) a.edgeToParent.setStroke(Color.ORANGE);
                    resultLabel.setText("At " + a.value + " — arrived from LEFT child, continue up…");
                });
                cur = anc;
                anc = anc.parent;
            }

            if (anc == null) {
                algorithmSteps.add(() ->
                        resultLabel.setText("✘  No predecessor — " + target + " is the minimum value in the tree."));
            } else {
                final BSTNode pred = anc;
                algorithmSteps.add(() -> {
                    pred.circle.setFill(Color.LIMEGREEN);
                    pred.circle.setStrokeWidth(4);
                    resultLabel.setText("✔  Predecessor of " + target + "  is  " + pred.value
                            + "  (first ancestor where we arrived from the RIGHT)");
                });
            }
        }
    }

    private void recordFindSuccessor() {
        String text = startNodeField.getText().trim();
        int target;
        try { target = Integer.parseInt(text); }
        catch (NumberFormatException e) {
            resultLabel.setText("Successor: enter an integer in the Value field.");
            return;
        }

        List<BSTNode> path = getPathTo(target);
        if (path.isEmpty() || path.get(path.size() - 1).value != target) {
            recordNavigateAndNotFound(path, target, "Successor");
            return;
        }

        recordNavigateTo(path, target, "Successor of");
        BSTNode targetNode = path.get(path.size() - 1);

        if (targetNode.right != null) {
            final BSTNode rc = targetNode.right;
            algorithmSteps.add(() -> {
                rc.circle.setFill(Color.YELLOW);
                rc.edgeToParent.setStroke(Color.ORANGE);
                resultLabel.setText("Right subtree exists → successor = leftmost node in right subtree");
            });
            BSTNode cur = targetNode.right;
            while (cur.left != null) {
                final BSTNode next = cur.left;
                algorithmSteps.add(() -> {
                    next.circle.setFill(Color.YELLOW);
                    next.edgeToParent.setStroke(Color.ORANGE);
                    resultLabel.setText("Keep going left → " + next.value);
                });
                cur = cur.left;
            }
            final BSTNode succ = cur;
            algorithmSteps.add(() -> {
                succ.circle.setFill(Color.LIMEGREEN);
                succ.circle.setStrokeWidth(4);
                resultLabel.setText("✔  Successor of " + target + "  is  " + succ.value);
            });

        } else {
            algorithmSteps.add(() ->
                    resultLabel.setText("No right subtree → walk up ancestors until we arrive from the LEFT"));

            BSTNode cur = targetNode;
            BSTNode anc = targetNode.parent;
            while (anc != null && cur == anc.right) {
                final BSTNode c = cur, a = anc;
                algorithmSteps.add(() -> {
                    c.circle.setFill(Color.LIGHTGRAY);
                    a.circle.setFill(Color.YELLOW);
                    if (a.parent != null) a.edgeToParent.setStroke(Color.ORANGE);
                    resultLabel.setText("At " + a.value + " — arrived from RIGHT child, continue up…");
                });
                cur = anc;
                anc = anc.parent;
            }

            if (anc == null) {
                algorithmSteps.add(() ->
                        resultLabel.setText("✘  No successor — " + target + " is the maximum value in the tree."));
            } else {
                final BSTNode succ = anc;
                algorithmSteps.add(() -> {
                    succ.circle.setFill(Color.LIMEGREEN);
                    succ.circle.setStrokeWidth(4);
                    resultLabel.setText("✔  Successor of " + target + "  is  " + succ.value
                            + "  (first ancestor where we arrived from the LEFT)");
                });
            }
        }
    }

    private void recordDeletion() {
        String text = startNodeField.getText().trim();
        int target;
        try { target = Integer.parseInt(text); }
        catch (NumberFormatException e) {
            resultLabel.setText("Delete: enter an integer in the Value field.");
            return;
        }

        List<BSTNode> path = getPathTo(target);
        if (path.isEmpty() || path.get(path.size() - 1).value != target) {
            recordNavigateAndNotFound(path, target, "Delete");
            return;
        }

        recordNavigateTo(path, target, "Deleting");
        BSTNode targetNode = path.get(path.size() - 1);

        boolean hasLeft  = targetNode.left  != null;
        boolean hasRight = targetNode.right != null;

        if (!hasLeft && !hasRight) {
            final BSTNode t = targetNode;
            algorithmSteps.add(() -> {
                t.circle.setFill(Color.ORANGERED);
                resultLabel.setText("Case 1 — Leaf node: no children → simply remove " + t.value);
            });
            final int capturedVal = target;
            nonReplayableSteps.add(algorithmSteps.size());
            algorithmSteps.add(() -> {
                if (containsValue(root, capturedVal)) {
                    deleteValue(capturedVal);
                    undoStack.push(new DeleteCommand(capturedVal));
                }
                resultLabel.setText("✔  Deleted " + capturedVal + "  (leaf removed)");
            });

        } else if (!hasLeft || !hasRight) {
            final BSTNode t     = targetNode;
            final BSTNode child = hasLeft ? targetNode.left : targetNode.right;
            final String  side  = hasLeft ? "left" : "right";
            algorithmSteps.add(() -> {
                t.circle.setFill(Color.ORANGERED);
                child.circle.setFill(Color.LIMEGREEN);
                child.edgeToParent.setStroke(Color.LIMEGREEN);
                resultLabel.setText("Case 2 — One child: bypass " + t.value
                        + " and promote its " + side + " child (" + child.value + ")");
            });
            final int capturedVal = target;
            nonReplayableSteps.add(algorithmSteps.size());
            algorithmSteps.add(() -> {
                if (containsValue(root, capturedVal)) {
                    deleteValue(capturedVal);
                    undoStack.push(new DeleteCommand(capturedVal));
                }
                resultLabel.setText("✔  Deleted " + capturedVal + " — child " + child.value + " promoted");
            });

        } else {
            final BSTNode t = targetNode;
            algorithmSteps.add(() -> {
                t.circle.setFill(Color.ORANGERED);
                resultLabel.setText("Case 3 — Two children: find inorder successor (min of right subtree)");
            });

            final BSTNode rc = targetNode.right;
            algorithmSteps.add(() -> {
                rc.circle.setFill(Color.YELLOW);
                rc.edgeToParent.setStroke(Color.ORANGE);
                resultLabel.setText("Step into right subtree → " + rc.value);
            });

            BSTNode cur = targetNode.right;
            while (cur.left != null) {
                final BSTNode next = cur.left;
                algorithmSteps.add(() -> {
                    next.circle.setFill(Color.YELLOW);
                    next.edgeToParent.setStroke(Color.ORANGE);
                    resultLabel.setText("Go left to find minimum → " + next.value);
                });
                cur = cur.left;
            }

            final BSTNode succ        = cur;
            final int     succValue   = succ.value;
            final int     capturedVal = target;

            algorithmSteps.add(() -> {
                succ.circle.setFill(Color.LIMEGREEN);
                succ.circle.setStrokeWidth(4);
                resultLabel.setText("Inorder successor found: " + succValue
                        + "  →  copy it into " + t.value + "'s slot");
            });
            algorithmSteps.add(() -> {
                t.circle.setFill(Color.LIMEGREEN);
                t.circle.setStrokeWidth(4);
                succ.circle.setFill(Color.ORANGERED);
                resultLabel.setText("Copied " + succValue + " → now delete the successor node "
                        + succValue + " (it has at most one child)");
            });
            nonReplayableSteps.add(algorithmSteps.size());
            algorithmSteps.add(() -> {
                if (containsValue(root, capturedVal)) {
                    deleteValue(capturedVal);
                    undoStack.push(new DeleteCommand(capturedVal));
                }
                resultLabel.setText("✔  Deleted " + capturedVal + " via successor swap with " + succValue);
            });
        }
    }

    private void recordInsertion() {
        String text = startNodeField.getText().trim();
        int target;
        try { target = Integer.parseInt(text); }
        catch (NumberFormatException e) {
            resultLabel.setText("Insert: enter an integer in the Value field.");
            return;
        }

        if (containsValue(root, target)) {
            algorithmSteps.add(() ->
                    resultLabel.setText("✘  " + target + " already exists — duplicates not allowed."));
            return;
        }

        if (root == null) {
            algorithmSteps.add(() ->
                    resultLabel.setText("Tree is empty → " + target + " becomes the root."));
            final int val = target;
            nonReplayableSteps.add(algorithmSteps.size());
            algorithmSteps.add(() -> {
                if (!containsValue(root, val)) {
                    insertValue(val);
                    undoStack.push(new InsertCommand(val));
                }
                BSTNode inserted = findNode(root, val);
                if (inserted != null) {
                    inserted.circle.setFill(Color.LIMEGREEN);
                    inserted.circle.setStrokeWidth(4);
                }
                resultLabel.setText("✔  Inserted " + val + " as root.");
            });
            return;
        }

        List<BSTNode> path      = new ArrayList<>();
        BSTNode       cur       = root;
        BSTNode       insParent = null;
        String        insDir    = null;

        while (cur != null) {
            path.add(cur);
            if (target < cur.value) {
                if (cur.left == null)  { insParent = cur; insDir = "left";  break; }
                cur = cur.left;
            } else {
                if (cur.right == null) { insParent = cur; insDir = "right"; break; }
                cur = cur.right;
            }
        }

        for (int i = 0; i < path.size(); i++) {
            final BSTNode node   = path.get(i);
            final boolean isLast = (i == path.size() - 1);
            final String  goLabel = (target < node.value)
                    ? target + " < " + node.value + "  →  go LEFT"
                    : target + " > " + node.value + "  →  go RIGHT";

            algorithmSteps.add(() -> {
                node.circle.setFill(Color.YELLOW);
                if (node.parent != null) node.edgeToParent.setStroke(Color.ORANGE);
                resultLabel.setText("Insert " + target + ": at " + node.value + "  —  " + goLabel);
            });

            if (!isLast) {
                final BSTNode n = node;
                algorithmSteps.add(() -> n.circle.setFill(Color.LIGHTGRAY));
            }
        }

        final BSTNode parent   = insParent;
        final String  side     = insDir;
        final int     finalVal = target;

        algorithmSteps.add(() -> {
            if (parent != null) {
                parent.circle.setFill(Color.CYAN);
                resultLabel.setText("Found empty " + side + " slot under " + parent.value
                        + "  →  insert " + finalVal + " here.");
            }
        });

        nonReplayableSteps.add(algorithmSteps.size());
        algorithmSteps.add(() -> {
            if (!containsValue(root, finalVal)) {
                insertValue(finalVal);
                undoStack.push(new InsertCommand(finalVal));
            }
            Platform.runLater(() -> {
                BSTNode inserted = findNode(root, finalVal);
                if (inserted != null) {
                    inserted.circle.setFill(Color.LIMEGREEN);
                    inserted.circle.setStrokeWidth(4);
                    if (inserted.edgeToParent != null)
                        inserted.edgeToParent.setStroke(Color.LIMEGREEN);
                }
            });
            resultLabel.setText("✔  Inserted " + finalVal
                    + (parent != null ? " as " + side + " child of " + parent.value : " as root") + ".");
        });
    }

    private BSTNode findNode(BSTNode node, int value) {
        if (node == null) return null;
        if (node.value == value) return node;
        return value < node.value
                ? findNode(node.left,  value)
                : findNode(node.right, value);
    }
}