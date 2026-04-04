package org.example.VisuAlgorithm;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.*;

public class BSTController {

    // ── FXML Bindings ──────────────────────────────────────────────────────────
    @FXML private Pane               canvasPane;
    @FXML private ToolBar            buildToolbar;
    @FXML private ToolBar            algoToolbar;
    @FXML private ToolBar            playbackToolbar;
    @FXML private TextField          insertField;
    @FXML private ComboBox<String>   algoComboBox;
    @FXML private TextField          startNodeField;
    @FXML private Slider             speedSlider;
    @FXML private Button             playPauseButton;
    @FXML private Label              resultLabel;
    @FXML private Button             backButton;

    // ── Tree state ─────────────────────────────────────────────────────────────
    private BSTNode                      root         = null;
    private BSTNode                      selectedNode = null;
    private final Stack<UndoCommand>     undoStack    = new Stack<>();

    // ── Snapshot (backward stepping) ───────────────────────────────────────────
    private static class NodeState {
        final int     value;
        final BSTNode left, right, parent;
        NodeState(BSTNode n) { value = n.value; left = n.left; right = n.right; parent = n.parent; }
    }
    private final Map<BSTNode, NodeState> treeSnapshot = new HashMap<>();
    private BSTNode snapshotRoot;

    // ── Layout constants ───────────────────────────────────────────────────────
    private static final double NODE_RADIUS = 22.0;
    private static final double H_SPACING   = 52.0;
    private static final double V_SPACING   = 72.0;
    private static final double MARGIN_LEFT = 44.0;
    private static final double MARGIN_TOP  = 48.0;

    // ── Algorithm playback ─────────────────────────────────────────────────────
    private boolean              isAlgorithmMode    = false;
    private Timeline             timeline;
    private final List<Runnable> algorithmSteps     = new ArrayList<>();
    private final Set<Integer>   nonReplayableSteps = new HashSet<>();
    private int                  currentStep        = 0;

    // ── Animation handles ──────────────────────────────────────────────────────
    private PauseTransition pendingLayoutTimer = null;
    private AnimationTimer  activeEdgeSync     = null;

    // ── Canvas Pan & Zoom Sub-Container & Variables ────────────────────────────
    private final Group treeContentGroup = new Group();
    private final Scale scaleTransform = new Scale(1, 1);
    private final Translate panTransform = new Translate(0, 0);
    private double lastPanX, lastPanY;
    private double dragStartX, dragStartY;
    private boolean canvasDragged = false;

    // ==========================================================================
    // INNER CLASS: BSTNode
    // ==========================================================================
    class BSTNode {
        int     value;
        BSTNode left, right, parent;

        Circle     circle;
        Text       label;
        Line       edgeToParent;
        Transition exitAnimation;

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
                if (e.getButton() == MouseButton.PRIMARY && !canvasDragged) {
                    handleNodeClick(this);
                }
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
        @Override public void undo() { deleteValue(value); }
    }

    private class DeleteCommand implements UndoCommand {
        final int value;
        DeleteCommand(int v) { this.value = v; }
        @Override public void undo() { insertValue(value); }
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

        if (algoToolbar    != null) { algoToolbar.setVisible(false);     algoToolbar.setManaged(false);    }
        if (playbackToolbar != null) { playbackToolbar.setVisible(false); playbackToolbar.setManaged(false); }

        // Ctrl / Cmd Shortcuts for Undo and Zooming
        Platform.runLater(() -> {
            if (canvasPane.getScene() != null) {
                canvasPane.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.isShortcutDown()) {
                        if (event.getCode() == KeyCode.Z) {
                            handleUndo();
                            event.consume();
                        } else if (event.getCode() == KeyCode.EQUALS || event.getCode() == KeyCode.PLUS || event.getCode() == KeyCode.ADD) {
                            applyZoom(1.1, canvasPane.getWidth() / 2, canvasPane.getHeight() / 2);
                            event.consume();
                        } else if (event.getCode() == KeyCode.MINUS || event.getCode() == KeyCode.SUBTRACT) {
                            applyZoom(0.9, canvasPane.getWidth() / 2, canvasPane.getHeight() / 2);
                            event.consume();
                        } else if (event.getCode() == KeyCode.DIGIT0 || event.getCode() == KeyCode.NUMPAD0) {
                            resetPanAndZoom();
                            event.consume();
                        }
                    }
                });
            }
        });

        setupCanvasPanAndZoom();
    }

    // ==========================================================================
    // CANVAS PAN & ZOOM SETUP
    // ==========================================================================
    private void setupCanvasPanAndZoom() {
        if (canvasPane == null) return;

        if (!canvasPane.getChildren().contains(treeContentGroup)) {
            canvasPane.getChildren().add(treeContentGroup);
        }

        treeContentGroup.getTransforms().addAll(panTransform, scaleTransform);

        // 1. Trackpad Two-Finger Swipe to Pan
        canvasPane.setOnScroll(event -> {
            panTransform.setX(panTransform.getX() + event.getDeltaX());
            panTransform.setY(panTransform.getY() + event.getDeltaY());
            event.consume();
        });

        // 2. Trackpad Pinch to Zoom
        canvasPane.setOnZoom(event -> {
            applyZoom(event.getZoomFactor(), event.getX(), event.getY());
            event.consume();
        });

        // 3. Mouse Click & Drag to Pan (Left, Middle, or Right Click)
        canvasPane.setOnMousePressed(event -> {
            lastPanX = event.getSceneX();
            lastPanY = event.getSceneY();
            dragStartX = lastPanX;
            dragStartY = lastPanY;
            canvasDragged = false;

            if (event.getButton() != MouseButton.PRIMARY || isAlgorithmMode) {
                canvasPane.setCursor(javafx.scene.Cursor.CLOSED_HAND);
            }
        });

        canvasPane.setOnMouseDragged(event -> {
            double deltaX = event.getSceneX() - lastPanX;
            double deltaY = event.getSceneY() - lastPanY;

            panTransform.setX(panTransform.getX() + deltaX);
            panTransform.setY(panTransform.getY() + deltaY);

            lastPanX = event.getSceneX();
            lastPanY = event.getSceneY();

            // If moved more than 3 pixels, it's considered a drag (prevents accidental selection clearing)
            if (Math.hypot(event.getSceneX() - dragStartX, event.getSceneY() - dragStartY) > 3) {
                canvasDragged = true;
                canvasPane.setCursor(javafx.scene.Cursor.CLOSED_HAND);
            }

            event.consume();
        });

        canvasPane.setOnMouseReleased(event -> {
            canvasPane.setCursor(javafx.scene.Cursor.DEFAULT);
        });
    }

    private void applyZoom(double zoomFactor, double pivotX, double pivotY) {
        double currentScale = scaleTransform.getX();
        double newScale = currentScale * zoomFactor;

        // Clamp the scale bounds
        if (newScale < 0.2 || newScale > 5.0) return;

        double f = (zoomFactor - 1);
        double dx = pivotX - panTransform.getX();
        double dy = pivotY - panTransform.getY();

        panTransform.setX(panTransform.getX() - dx * f);
        panTransform.setY(panTransform.getY() - dy * f);

        scaleTransform.setX(newScale);
        scaleTransform.setY(newScale);
    }

    private void resetPanAndZoom() {
        panTransform.setX(0);
        panTransform.setY(0);
        scaleTransform.setX(1);
        scaleTransform.setY(1);
    }

    // ==========================================================================
    // BST CORE LOGIC
    // ==========================================================================
    private BSTNode insertBST(BSTNode node, BSTNode parent, int value) {
        if (node == null) { BSTNode n = new BSTNode(value); n.parent = parent; return n; }
        if      (value < node.value) node.left  = insertBST(node.left,  node, value);
        else if (value > node.value) node.right = insertBST(node.right, node, value);
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
            if (node.left == null && node.right == null) { removeNodeFromCanvas(node); return null; }
            else if (node.left  == null) { removeNodeFromCanvas(node); return node.right; }
            else if (node.right == null) { removeNodeFromCanvas(node); return node.left;  }
            else {
                BSTNode successor = minNode(node.right);
                node.value = successor.value;
                node.label.setText(String.valueOf(node.value));
                node.right = deleteBST(node.right, successor.value);
                if (node.right != null) node.right.parent = node;
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
        return value < node.value ? containsValue(node.left, value) : containsValue(node.right, value);
    }

    private BSTNode findNode(BSTNode node, int value) {
        if (node == null) return null;
        if (node.value == value) return node;
        return value < node.value ? findNode(node.left, value) : findNode(node.right, value);
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
            treeContentGroup.getChildren().remove(node.edgeToParent);
        }

        double w = node.label.getLayoutBounds().getWidth();
        double h = node.label.getLayoutBounds().getHeight();
        node.label.setX(cx - w / 2.0);
        node.label.setY(cy + h / 4.0);

        updateEdgesAndLabels(node.left);
        updateEdgesAndLabels(node.right);
    }

    private void layoutTreeAnimated() {
        if (root == null) return;

        if (activeEdgeSync != null) { activeEdgeSync.stop(); activeEdgeSync = null; }

        Map<BSTNode, double[]> targets = new LinkedHashMap<>();
        int[] counter = {0};
        collectInorderX(root, counter, targets);
        collectDepthY(root, 0, targets);

        Timeline moveTl  = new Timeline();
        Duration dur     = Duration.millis(550);

        for (Map.Entry<BSTNode, double[]> entry : targets.entrySet()) {
            BSTNode n  = entry.getKey();
            double  tx = entry.getValue()[0];
            double  ty = entry.getValue()[1];
            moveTl.getKeyFrames().add(new KeyFrame(dur,
                    new KeyValue(n.circle.centerXProperty(), tx, Interpolator.EASE_BOTH),
                    new KeyValue(n.circle.centerYProperty(), ty, Interpolator.EASE_BOTH)
            ));
        }

        AnimationTimer sync = new AnimationTimer() {
            @Override public void handle(long now) { updateEdgesAndLabels(root); }
        };
        activeEdgeSync = sync;
        sync.start();

        moveTl.setOnFinished(e -> {
            sync.stop();
            if (activeEdgeSync == sync) activeEdgeSync = null;
            updateEdgesAndLabels(root);
        });
        moveTl.play();
    }

    private void collectInorderX(BSTNode node, int[] counter, Map<BSTNode, double[]> out) {
        if (node == null) return;
        collectInorderX(node.left, counter, out);
        out.computeIfAbsent(node, k -> new double[2])[0] = MARGIN_LEFT + counter[0]++ * H_SPACING;
        collectInorderX(node.right, counter, out);
    }

    private void collectDepthY(BSTNode node, int depth, Map<BSTNode, double[]> out) {
        if (node == null) return;
        out.computeIfAbsent(node, k -> new double[2])[1] = MARGIN_TOP + depth * V_SPACING;
        collectDepthY(node.left,  depth + 1, out);
        collectDepthY(node.right, depth + 1, out);
    }

    // ==========================================================================
    // CANVAS MANAGEMENT & ANIMATION
    // ==========================================================================

    private void resetNodeVisuals(BSTNode node) {
        node.circle.setTranslateX(0); node.circle.setTranslateY(0);
        node.circle.setScaleX(1.0);   node.circle.setScaleY(1.0);
        node.circle.setOpacity(1.0);
        node.label.setTranslateX(0);  node.label.setTranslateY(0);
        node.label.setScaleX(1.0);    node.label.setScaleY(1.0);
        node.label.setOpacity(1.0);
        node.edgeToParent.setOpacity(1.0);
    }

    private void addSubtreeToCanvas(BSTNode node) {
        if (node == null) return;

        if (node.exitAnimation != null) { node.exitAnimation.stop(); node.exitAnimation = null; }
        node.circle.setMouseTransparent(false);

        if (!treeContentGroup.getChildren().contains(node.circle)) {
            resetNodeVisuals(node);

            if (node.parent != null && !treeContentGroup.getChildren().contains(node.edgeToParent)) {
                treeContentGroup.getChildren().add(0, node.edgeToParent);
                node.edgeToParent.setOpacity(0);
            }
            treeContentGroup.getChildren().addAll(node.circle, node.label);

            node.circle.setScaleX(0); node.circle.setScaleY(0);
            node.circle.setOpacity(0);
            node.label.setOpacity(0);

            ScaleTransition stIn = new ScaleTransition(Duration.millis(420), node.circle);
            stIn.setToX(1); stIn.setToY(1);
            stIn.setInterpolator(Interpolator.EASE_OUT);

            FadeTransition ftCircle = new FadeTransition(Duration.millis(320), node.circle);
            ftCircle.setToValue(1);

            FadeTransition ftLabel = new FadeTransition(Duration.millis(380), node.label);
            ftLabel.setToValue(1);
            ftLabel.setDelay(Duration.millis(120));

            FadeTransition ftEdge = new FadeTransition(Duration.millis(360), node.edgeToParent);
            ftEdge.setToValue(1);
            ftEdge.setDelay(Duration.millis(60));

            new ParallelTransition(stIn, ftCircle, ftLabel, ftEdge).play();
        }

        addSubtreeToCanvas(node.left);
        addSubtreeToCanvas(node.right);
    }

    private void addSubtreeToCanvasInstant(BSTNode node) {
        if (node == null) return;

        if (node.exitAnimation != null) { node.exitAnimation.stop(); node.exitAnimation = null; }
        resetNodeVisuals(node);
        node.circle.setMouseTransparent(false);

        if (!treeContentGroup.getChildren().contains(node.circle)) {
            if (node.parent != null && !treeContentGroup.getChildren().contains(node.edgeToParent)) {
                treeContentGroup.getChildren().add(0, node.edgeToParent);
            }
            treeContentGroup.getChildren().addAll(node.circle, node.label);
        }

        addSubtreeToCanvasInstant(node.left);
        addSubtreeToCanvasInstant(node.right);
    }

    private void removeNodeFromCanvas(BSTNode node) {
        node.circle.setMouseTransparent(true);

        FadeTransition pulse = new FadeTransition(Duration.millis(120), node.circle);
        pulse.setFromValue(1.0);
        pulse.setToValue(0.55);
        pulse.setCycleCount(2);
        pulse.setAutoReverse(true);

        TranslateTransition ttCircle = new TranslateTransition(Duration.millis(860), node.circle);
        ttCircle.setByY(-22);
        ttCircle.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition ttLabel = new TranslateTransition(Duration.millis(860), node.label);
        ttLabel.setByY(-22);
        ttLabel.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition stCircle = new ScaleTransition(Duration.millis(860), node.circle);
        stCircle.setToX(0); stCircle.setToY(0);
        stCircle.setInterpolator(Interpolator.EASE_IN);

        FadeTransition ftCircle = new FadeTransition(Duration.millis(680), node.circle);
        ftCircle.setDelay(Duration.millis(180));
        ftCircle.setToValue(0);
        ftCircle.setInterpolator(Interpolator.EASE_IN);

        FadeTransition ftLabel = new FadeTransition(Duration.millis(520), node.label);
        ftLabel.setDelay(Duration.millis(120));
        ftLabel.setToValue(0);
        ftLabel.setInterpolator(Interpolator.EASE_IN);

        FadeTransition ftEdge = new FadeTransition(Duration.millis(380), node.edgeToParent);
        ftEdge.setToValue(0);
        ftEdge.setInterpolator(Interpolator.EASE_IN);

        node.exitAnimation = new SequentialTransition(
                pulse,
                new ParallelTransition(ttCircle, ttLabel, stCircle, ftCircle, ftLabel, ftEdge)
        );
        node.exitAnimation.setOnFinished(e -> {
            treeContentGroup.getChildren().removeAll(node.circle, node.label, node.edgeToParent);
            resetNodeVisuals(node);
            node.exitAnimation = null;
        });
        node.exitAnimation.play();
    }

    // ==========================================================================
    // SNAPSHOT SYSTEM
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
        cancelPendingLayout();

        for (BSTNode n : treeSnapshot.keySet()) {
            if (n.exitAnimation != null) { n.exitAnimation.stop(); n.exitAnimation = null; }
            resetNodeVisuals(n);
        }

        root = snapshotRoot;
        for (Map.Entry<BSTNode, NodeState> entry : treeSnapshot.entrySet()) {
            BSTNode   n = entry.getKey();
            NodeState s = entry.getValue();
            n.value = s.value;
            n.label.setText(String.valueOf(n.value));
            n.left   = s.left;
            n.right  = s.right;
            n.parent = s.parent;
        }

        treeContentGroup.getChildren().clear();
        if (root != null) {
            addSubtreeToCanvasInstant(root);
            layoutTree();
        }
    }

    // ==========================================================================
    // INSERT / DELETE
    // ==========================================================================

    private void insertValue(int value) {
        root = insertBST(root, null, value);
        addSubtreeToCanvas(root);
        Platform.runLater(this::layoutTreeAnimated);
    }

    private void insertValueSilent(int value) {
        root = insertBST(root, null, value);
        addSubtreeToCanvasInstant(root);
    }

    private void deleteValue(int value) {
        if (root == null) return;
        clearSelection();
        root = deleteBST(root, value);
        if (root != null) root.parent = null;

        cancelPendingLayout();
        pendingLayoutTimer = new PauseTransition(Duration.millis(980));
        pendingLayoutTimer.setOnFinished(e -> { pendingLayoutTimer = null; layoutTreeAnimated(); });
        pendingLayoutTimer.play();
    }

    private void cancelPendingLayout() {
        if (pendingLayoutTimer != null) { pendingLayoutTimer.stop(); pendingLayoutTimer = null; }
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
        if (canvasDragged) return; // Prevent clearing selection if we were just panning
        if (event.getTarget() == canvasPane || event.getTarget() == treeContentGroup) clearSelection();
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
        Random       rng   = new Random();
        int          count = rng.nextInt(5) + 7;
        Set<Integer> used  = new LinkedHashSet<>();
        while (used.size() < count) used.add(rng.nextInt(90) + 10);
        for (int v : used) insertValueSilent(v);
        layoutTree();
        resultLabel.setText("Random tree generated (" + count + " nodes).");
    }

    @FXML
    public void clearTree() {
        cancelPendingLayout();
        if (activeEdgeSync != null) { activeEdgeSync.stop(); activeEdgeSync = null; }

        root         = null;
        selectedNode = null;
        treeContentGroup.getChildren().clear();
        undoStack.clear();
        algorithmSteps.clear();
        nonReplayableSteps.clear();
        currentStep = 0;
        treeSnapshot.clear();
        snapshotRoot = null;

        if (timeline != null) { timeline.stop(); timeline = null; }
        if (playPauseButton != null) playPauseButton.setText("▶ Play");
        resultLabel.setText("");
        resetPanAndZoom();
    }

    @FXML
    private void handleBackButton(ActionEvent event) throws IOException {
        stopAll();
        Launcher.switchScene("hello-view.fxml");
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
        if (root == null) { resultLabel.setText("Tree is empty — build a tree first."); return; }
        isAlgorithmMode = true;
        clearSelection();
        resultLabel.setText("Select an algorithm and press Play!");
        buildToolbar.setVisible(false);   buildToolbar.setManaged(false);
        algoToolbar.setVisible(true);     algoToolbar.setManaged(true);
        playbackToolbar.setVisible(true); playbackToolbar.setManaged(true);
    }

    @FXML
    private void switchToBuildMode() {
        isAlgorithmMode = false;
        resetAlgorithmState();
        algoToolbar.setVisible(false);     algoToolbar.setManaged(false);
        playbackToolbar.setVisible(false); playbackToolbar.setManaged(false);
        buildToolbar.setVisible(true);     buildToolbar.setManaged(true);
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

        if      (algo.startsWith("Inorder"))  {
            List<String> visited = new ArrayList<>();
            recordInorder(root, visited);
            String fin = String.join(" → ", visited);
            algorithmSteps.add(() -> resultLabel.setText("Inorder Complete: " + fin));

        } else if (algo.startsWith("Preorder")) {
            List<String> visited = new ArrayList<>();
            recordPreorder(root, visited);
            String fin = String.join(" → ", visited);
            algorithmSteps.add(() -> resultLabel.setText("Preorder Complete: " + fin));

        } else if (algo.startsWith("Postorder")) {
            List<String> visited = new ArrayList<>();
            recordPostorder(root, visited);
            String fin = String.join(" → ", visited);
            algorithmSteps.add(() -> resultLabel.setText("Postorder Complete: " + fin));

        } else if (algo.startsWith("Level Order")) {
            recordLevelOrder();

        } else if (algo.startsWith("Search")) {
            String text = startNodeField.getText().trim();
            try   { recordSearch(root, Integer.parseInt(text), new ArrayList<>()); }
            catch (NumberFormatException e) {
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

        if (timeline != null && timeline.getStatus() == javafx.animation.Animation.Status.RUNNING) {
            timeline.pause();
            playPauseButton.setText("▶ Play");
            return;
        }

        if (algorithmSteps.isEmpty() || currentStep >= algorithmSteps.size()) initializeAlgorithm();
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
        if (currentStep < algorithmSteps.size()) algorithmSteps.get(currentStep++).run();
    }

    @FXML
    private void stepBackward() {
        if (algorithmSteps.isEmpty() || currentStep <= 0) return;
        if (timeline != null) { timeline.pause(); playPauseButton.setText("▶ Play"); }

        currentStep--;

        if (nonReplayableSteps.contains(currentStep) && !undoStack.isEmpty()) {
            undoStack.pop();
        }

        restoreTreeState();
        resetAllColors();
        resultLabel.setText("");

        for (int i = 0; i < currentStep; i++) {
            if (!nonReplayableSteps.contains(i)) algorithmSteps.get(i).run();
        }
    }

    @FXML
    private void resetAlgorithmState() {
        cancelPendingLayout();
        if (timeline != null) { timeline.stop(); timeline = null; }
        if (playPauseButton != null) playPauseButton.setText("▶ Play");
        resetAllColors();
        resultLabel.setText(isAlgorithmMode ? "Select an algorithm and press Play!" : "");
        algorithmSteps.clear();
        nonReplayableSteps.clear();
        currentStep = 0;
    }

    // ==========================================================================
    // ALGORITHM RECORDING — TRAVERSALS
    // ==========================================================================
    private void recordInorder(BSTNode node, List<String> visited) {
        if (node == null) return;
        final BSTNode cur = node;

        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.GOLD);
            if (cur.parent != null) cur.edgeToParent.setStroke(Color.GOLD);
            resultLabel.setText("Inorder: At " + cur.value + " — descending LEFT");
        });

        recordInorder(node.left, visited);

        visited.add(String.valueOf(node.value));
        final String order = "Inorder: " + String.join(" → ", visited);
        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.MAGENTA);
            cur.circle.setStroke(Color.WHITE);
            cur.circle.setStrokeWidth(3);
            resultLabel.setText(order + "  (Visited " + cur.value + ")");
        });

        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.ORANGE);
            cur.circle.setStroke(Color.BLACK);
            cur.circle.setStrokeWidth(2);
            resultLabel.setText("Inorder: " + cur.value + " visited — descending RIGHT");
        });

        recordInorder(node.right, visited);

        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.LIGHTGRAY);
            if (cur.parent != null) cur.edgeToParent.setStroke(Color.LIGHTGRAY);
        });
    }

    private void recordPreorder(BSTNode node, List<String> visited) {
        if (node == null) return;
        final BSTNode cur = node;

        visited.add(String.valueOf(node.value));
        final String order = "Preorder: " + String.join(" → ", visited);
        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.MAGENTA);
            cur.circle.setStroke(Color.WHITE);
            cur.circle.setStrokeWidth(3);
            if (cur.parent != null) cur.edgeToParent.setStroke(Color.MAGENTA);
            resultLabel.setText(order + "  (Visited " + cur.value + ")");
        });

        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.GOLD);
            cur.circle.setStroke(Color.BLACK);
            cur.circle.setStrokeWidth(2);
            resultLabel.setText("Preorder: Descending LEFT from " + cur.value);
        });

        recordPreorder(node.left, visited);

        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.ORANGE);
            resultLabel.setText("Preorder: Descending RIGHT from " + cur.value);
        });

        recordPreorder(node.right, visited);

        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.LIGHTGRAY);
            if (cur.parent != null) cur.edgeToParent.setStroke(Color.LIGHTGRAY);
        });
    }

    private void recordPostorder(BSTNode node, List<String> visited) {
        if (node == null) return;
        final BSTNode cur = node;

        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.GOLD);
            if (cur.parent != null) cur.edgeToParent.setStroke(Color.GOLD);
            resultLabel.setText("Postorder: At " + cur.value + " — descending LEFT");
        });

        recordPostorder(node.left, visited);

        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.ORANGE);
            resultLabel.setText("Postorder: At " + cur.value + " — descending RIGHT");
        });

        recordPostorder(node.right, visited);

        visited.add(String.valueOf(node.value));
        final String order = "Postorder: " + String.join(" → ", visited);
        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.MAGENTA);
            cur.circle.setStroke(Color.WHITE);
            cur.circle.setStrokeWidth(3);
            resultLabel.setText(order + "  (Visited " + cur.value + ")");
        });

        algorithmSteps.add(() -> {
            cur.circle.setFill(Color.LIGHTGRAY);
            cur.circle.setStroke(Color.BLACK);
            cur.circle.setStrokeWidth(2);
            if (cur.parent != null) cur.edgeToParent.setStroke(Color.LIGHTGRAY);
        });
    }

    private void recordLevelOrder() {
        Queue<BSTNode> queue   = new LinkedList<>();
        List<String>   visited = new ArrayList<>();
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
                if (processing.parent != null) processing.edgeToParent.setStroke(Color.ORANGE);
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

        final String fin = String.join(" → ", visited);
        algorithmSteps.add(() -> resultLabel.setText("Level Order complete: " + fin));
    }

    // ==========================================================================
    // ALGORITHM RECORDING — SEARCH / NAVIGATE HELPERS
    // ==========================================================================
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
        for (BSTNode node : path) {
            final BSTNode n        = node;
            final boolean isTarget = (n.value == target);
            algorithmSteps.add(() -> {
                n.circle.setFill(isTarget ? Color.CYAN : Color.YELLOW);
                if (n.parent != null) n.edgeToParent.setStroke(Color.ORANGE);
                resultLabel.setText(isTarget
                        ? action + " " + target + ": node found!"
                        : action + " " + target + ": at " + n.value
                        + (target < n.value ? "  →  go LEFT" : "  →  go RIGHT"));
            });
            if (!isTarget) algorithmSteps.add(() -> n.circle.setFill(Color.LIGHTGRAY));
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
            algorithmSteps.add(() -> n.circle.setFill(Color.LIGHTGRAY));
        }
        algorithmSteps.add(() ->
                resultLabel.setText("✘ " + target + " not found — cannot compute " + algo + "."));
    }

    // ==========================================================================
    // ALGORITHM RECORDING — PREDECESSOR / SUCCESSOR
    // ==========================================================================
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

    // ==========================================================================
    // ALGORITHM RECORDING — DELETION
    // ==========================================================================
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

        // ── Phase 1: Navigate to target ───────────────────────────────────────
        for (int i = 0; i < path.size() - 1; i++) {
            final BSTNode node = path.get(i);
            final int     next = path.get(i + 1).value;
            final String  cmp  = (next < node.value)
                    ? next + " < " + node.value + "  →  go LEFT"
                    : next + " > " + node.value + "  →  go RIGHT";

            algorithmSteps.add(() -> {
                node.circle.setFill(Color.GOLD);
                node.circle.setStrokeWidth(2);
                if (node.parent != null) node.edgeToParent.setStroke(Color.GOLD);
                resultLabel.setText("Searching: at " + node.value + "   ( " + cmp + " )");
            });
            algorithmSteps.add(() -> {
                node.circle.setFill(Color.LIGHTGRAY);
                if (node.parent != null) node.edgeToParent.setStroke(Color.LIGHTGRAY);
            });
        }

        // ── Phase 2: Spotlight target node ───────────────────────────────────
        final BSTNode t = path.get(path.size() - 1);
        algorithmSteps.add(() -> {
            t.circle.setFill(Color.ORANGERED);
            t.circle.setStroke(Color.DARKRED);
            t.circle.setStrokeWidth(4);
            if (t.parent != null) t.edgeToParent.setStroke(Color.ORANGERED);
            resultLabel.setText("Found node " + t.value + "!  Checking children to determine deletion case…");
        });

        boolean hasLeft  = t.left  != null;
        boolean hasRight = t.right != null;

        // ══════════════════════════════════════════════════════════════════════
        // CASE 1 — Leaf node
        // ══════════════════════════════════════════════════════════════════════
        if (!hasLeft && !hasRight) {
            algorithmSteps.add(() ->
                    resultLabel.setText("CASE 1 — Leaf node: " + t.value + " has no children."));
            algorithmSteps.add(() ->
                    resultLabel.setText("No replacement needed — simply unlink "
                            + t.value + " from its parent."));

            final int val = target;
            nonReplayableSteps.add(algorithmSteps.size());
            algorithmSteps.add(() -> {
                if (containsValue(root, val)) {
                    deleteValue(val);
                    undoStack.push(new DeleteCommand(val));
                }
                resultLabel.setText("Done!  Node " + val + " removed (leaf deleted).");
            });

            // ══════════════════════════════════════════════════════════════════════
            // CASE 2 — One child
            // ══════════════════════════════════════════════════════════════════════
        } else if (!hasLeft || !hasRight) {
            final BSTNode child = hasLeft ? t.left : t.right;
            final String  side  = hasLeft ? "left"  : "right";

            algorithmSteps.add(() ->
                    resultLabel.setText("CASE 2 — One child: " + t.value
                            + " has only a " + side + " subtree."));
            algorithmSteps.add(() -> {
                child.circle.setFill(Color.LIMEGREEN);
                child.circle.setStrokeWidth(3);
                child.edgeToParent.setStroke(Color.LIMEGREEN);
                resultLabel.setText("Child " + child.value
                        + " (green) will be promoted to replace " + t.value + ".");
            });
            algorithmSteps.add(() ->
                    resultLabel.setText("Re-linking parent of " + t.value
                            + " directly to child " + child.value + "."));
            algorithmSteps.add(() -> {
                t.circle.setFill(Color.ORANGERED);
                t.circle.setStrokeWidth(4);
                resultLabel.setText("Ready — removing " + t.value
                        + " and promoting " + child.value + ".");
            });

            final int val = target;
            nonReplayableSteps.add(algorithmSteps.size());
            algorithmSteps.add(() -> {
                if (containsValue(root, val)) {
                    deleteValue(val);
                    undoStack.push(new DeleteCommand(val));
                }
                resultLabel.setText("Done!  Node " + val
                        + " removed — child " + child.value + " promoted.");
            });

            // ══════════════════════════════════════════════════════════════════════
            // CASE 3 — Two children (inorder successor swap)
            // ══════════════════════════════════════════════════════════════════════
        } else {
            algorithmSteps.add(() ->
                    resultLabel.setText("CASE 3 — Two children: " + t.value
                            + " has both left and right subtrees."));
            algorithmSteps.add(() ->
                    resultLabel.setText("Strategy: find the INORDER SUCCESSOR "
                            + "(min of right subtree), copy its value here, "
                            + "then delete the now-duplicate successor."));

            final BSTNode rc = t.right;
            algorithmSteps.add(() -> {
                rc.circle.setFill(Color.ORANGE);
                rc.circle.setStrokeWidth(2);
                rc.edgeToParent.setStroke(Color.ORANGE);
                resultLabel.setText("Step into RIGHT subtree → "
                        + rc.value + "  (searching for leftmost node)");
            });

            BSTNode cur = t.right;
            if (cur.left == null) {
                algorithmSteps.add(() -> {
                    rc.circle.setFill(Color.LIMEGREEN);
                    rc.circle.setStrokeWidth(4);
                    resultLabel.setText("No left child — " + rc.value
                            + " is already the minimum → inorder successor found!");
                });
            } else {
                algorithmSteps.add(() ->
                        resultLabel.setText("Go LEFT repeatedly to find the minimum…"));
                while (cur.left != null) {
                    final BSTNode stepping = cur;
                    final BSTNode next     = cur.left;
                    algorithmSteps.add(() -> {
                        stepping.circle.setFill(Color.LIGHTGRAY);
                        next.circle.setFill(Color.GOLD);
                        next.edgeToParent.setStroke(Color.ORANGE);
                        resultLabel.setText("Go left: " + stepping.value
                                + " → " + next.value + "  (looking for minimum)");
                    });
                    cur = cur.left;
                }
                final BSTNode minFound = cur;
                algorithmSteps.add(() -> {
                    minFound.circle.setFill(Color.LIMEGREEN);
                    minFound.circle.setStrokeWidth(4);
                    resultLabel.setText("No left child — " + minFound.value
                            + " is the minimum → INORDER SUCCESSOR found!");
                });
            }

            final BSTNode succ          = cur;
            final int     succValue     = succ.value;
            final int     capturedTarget = target;

            algorithmSteps.add(() -> {
                t.circle.setFill(Color.CYAN);
                t.circle.setStrokeWidth(4);
                resultLabel.setText("Copy successor value " + succValue
                        + " into node " + capturedTarget + "'s slot — "
                        + capturedTarget + " becomes " + succValue + ".");
            });
            algorithmSteps.add(() -> {
                t.circle.setFill(Color.LIMEGREEN);
                t.circle.setStrokeWidth(4);
                t.label.setText(String.valueOf(succValue));   // visual preview
                succ.circle.setFill(Color.ORANGERED);
                succ.circle.setStrokeWidth(4);
                resultLabel.setText("Value copied!  Successor " + succValue
                        + " (red) is now a duplicate — delete it (≤ one child).");
            });

            nonReplayableSteps.add(algorithmSteps.size());
            algorithmSteps.add(() -> {
                if (containsValue(root, capturedTarget)) {
                    deleteValue(capturedTarget);
                    undoStack.push(new DeleteCommand(capturedTarget));
                }
                resultLabel.setText("Done!  Deleted " + capturedTarget
                        + " via successor swap with " + succValue + ".");
            });
        }
    }

    // ==========================================================================
    // ALGORITHM RECORDING — INSERTION
    // ==========================================================================
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
                if (cur.left  == null) { insParent = cur; insDir = "left";  break; }
                cur = cur.left;
            } else {
                if (cur.right == null) { insParent = cur; insDir = "right"; break; }
                cur = cur.right;
            }
        }

        for (int i = 0; i < path.size(); i++) {
            final BSTNode node    = path.get(i);
            final boolean isLast  = (i == path.size() - 1);
            final String  goLabel = (target < node.value)
                    ? target + " < " + node.value + "  →  go LEFT"
                    : target + " > " + node.value + "  →  go RIGHT";

            algorithmSteps.add(() -> {
                node.circle.setFill(Color.YELLOW);
                if (node.parent != null) node.edgeToParent.setStroke(Color.ORANGE);
                resultLabel.setText("Insert " + target + ": at " + node.value + "  —  " + goLabel);
            });
            if (!isLast) algorithmSteps.add(() -> node.circle.setFill(Color.LIGHTGRAY));
        }

        final BSTNode parent   = insParent;
        final String  side     = insDir;
        final int     finalVal = target;

        algorithmSteps.add(() -> {
            if (parent != null) {
                parent.circle.setFill(Color.CYAN);
                resultLabel.setText("Found empty " + side + " slot under "
                        + parent.value + "  →  insert " + finalVal + " here.");
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
}