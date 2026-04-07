package org.example.VisuAlgorithm;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import javafx.util.Duration;

// Added imports for recording and capture
import javafx.scene.image.WritableImage;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jcodec.api.awt.AWTSequenceEncoder;
import javafx.geometry.Point2D;
import java.util.*;

public class graphController {

    // ===============================
    // FXML UI COMPONENTS
    // ===============================
    @FXML private Pane canvasPane;
    @FXML private ToggleButton nodeTool;
    @FXML private ToggleButton edgeTool;
    @FXML private CheckBox directedCheck;
    @FXML private CheckBox weightedCheck;
    @FXML private TextField weightField;
    @FXML private CheckBox customNodeCheck;
    @FXML private TextField nodeValueField;
    @FXML private Button backButton;
    @FXML private Label resultLabel;

    // --- Capture buttons ---
    @FXML private Button screenshotBtn;
    @FXML private Button recordBtn;

    // Recording state
    private boolean isRecording = false;
    private ScheduledExecutorService recordingExecutor;
    private List<BufferedImage> recordedFrames = new ArrayList<>();
    private static final int RECORD_FPS = 30; // frames per second

    // UI Mode Panels
    @FXML private ToolBar buildToolbar;
    @FXML private ToolBar algoToolbar;
    @FXML private ToolBar playbackToolbar;

    // Algorithm Controls
    @FXML private ComboBox<String> algoComboBox;
    @FXML private TextField startNodeField;
    @FXML private TextField endNodeField;
    @FXML private Slider speedSlider;
    @FXML private Button playPauseButton;

    // Data Representation Panels
    @FXML private VBox dataPane;
    @FXML private ToggleButton dataToggleBuild;
    @FXML private ToggleButton dataToggleAlgo;
    @FXML private TextArea adjListArea;
    @FXML private TextArea adjMatrixArea;

    // ===============================
    // LEFT SIDE: REAL-TIME STATE PANEL
    // ===============================
    @FXML private VBox      algoStatePane;
    @FXML private Label     algoNameLabel;
    @FXML private Label     currentStepLabel;
    @FXML private Label     dsTitleLabel;
    @FXML private TextArea  dsArea;
    @FXML private TextArea  visitedArea;
    @FXML private Separator extraSeparator;
    @FXML private Label     extraTitleLabel;
    @FXML private TextArea  extraArea;

    // ===============================
    // STATE VARIABLES
    // ===============================
    private int nodeCounter = 1;
    private GraphNode firstEdgeNode = null;
    private GraphNode selectedNode  = null;
    private GraphEdge selectedEdge  = null;
    private boolean isAlgorithmMode = false;

    private final List<GraphNode>      nodes      = new ArrayList<>();
    private final List<GraphEdge>      edges      = new ArrayList<>();
    private final Stack<UndoCommand>   undoStack  = new Stack<>();

    private Timeline         timeline       = null;
    private final List<Runnable> algorithmSteps = new ArrayList<>();
    private int currentStep = 0;

    // Canvas Pan & Zoom Sub-Container & Variables
    private final Group graphContentGroup = new Group();
    private final Scale scaleTransform = new Scale(1, 1);
    private final Translate panTransform = new Translate(0, 0);
    private double lastPanX, lastPanY;
    private double dragStartX, dragStartY;
    private boolean canvasDragged = false;

    // ===============================
    // INITIALIZATION
    // ===============================
    @FXML
    public void initialize() {
        nodeTool.setSelected(true);
        nodeTool.setOnAction(e -> clearSelection());
        edgeTool.setOnAction(e -> clearSelection());

        screenshotBtn.setText("📷 Snapshot");
        recordBtn.setText("🎥 Record");

        screenshotBtn.setPrefWidth(130);
        screenshotBtn.setMinWidth(130);

        recordBtn.setPrefWidth(130);
        recordBtn.setMinWidth(130);

        Platform.runLater(() -> {
            if (canvasPane.getScene() != null) {
                // Global Keyboard Shortcuts (Undo + Zooming)
                canvasPane.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.isShortcutDown()) { // isShortcutDown maps to Ctrl on Windows, Cmd on Mac
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
                        setText(null); setDisable(false); setStyle("");
                    } else {
                        setText(item);
                        boolean reqWeight  = item.contains("Dijkstra") || item.contains("Prim") || item.contains("Kruskal");
                        boolean hasUnweighted = edges.stream().anyMatch(e -> !e.isWeighted);
                        boolean reqDAG     = item.contains("Topological");
                        boolean notDAG     = !isDAG();
                        if ((reqWeight && hasUnweighted) || (reqDAG && notDAG)) {
                            setDisable(true);
                            setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
                        } else {
                            setDisable(false);
                            setStyle("-fx-text-fill: black;");
                        }
                    }
                }
            });

            algoComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.equals(oldVal)) {
                    resetAlgorithmState();
                    if (isAlgorithmMode) {
                        resultLabel.setText("Algorithm changed. Press ▶ Play to start!");
                    }
                }
            });
        }

        setupCanvasPanAndZoom();

        if (resultLabel != null) resultLabel.setText("");
        updateGraphRepresentations();
    }

    // ===============================
    // CANVAS PAN & ZOOM SETUP
    // ===============================
    private void setupCanvasPanAndZoom() {
        if (canvasPane == null) return;

        if (!canvasPane.getChildren().contains(graphContentGroup)) {
            canvasPane.getChildren().add(graphContentGroup);
        }

        graphContentGroup.getTransforms().addAll(panTransform, scaleTransform);

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

            // If moved more than 3 pixels, it's a drag (prevents accidental node creation when clicking)
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

        // Clamp the scale bounds so the user doesn't zoom into infinity/microscopic
        if (newScale < 0.2 || newScale > 5.0) return;

        double f = (zoomFactor - 1);

        // Calculate translation math so we zoom directly towards the pivot point
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

    // ===============================
    // REAL-TIME STATE PANEL HELPERS
    // ===============================

    private void setAlgoState(String action,
                              String dsTitle, String ds,
                              String visited,
                              String extraTitle, String extra) {
        if (currentStepLabel != null)
            currentStepLabel.setText(action != null ? action : "—");
        if (dsTitleLabel != null && dsTitle != null && !dsTitle.isEmpty())
            dsTitleLabel.setText(dsTitle);
        if (dsArea != null)
            dsArea.setText(ds != null ? ds : "");
        if (visitedArea != null)
            visitedArea.setText(visited != null ? visited : "");

        boolean showExtra = extra != null && !extra.isEmpty();
        if (extraSeparator  != null) { extraSeparator.setVisible(showExtra);  extraSeparator.setManaged(showExtra);  }
        if (extraTitleLabel != null) { extraTitleLabel.setText(extraTitle != null ? extraTitle : "");
            extraTitleLabel.setVisible(showExtra); extraTitleLabel.setManaged(showExtra); }
        if (extraArea       != null) { extraArea.setText(showExtra ? extra : "");
            extraArea.setVisible(showExtra);       extraArea.setManaged(showExtra);       }
    }

    private void clearStatePanel() {
        if (algoNameLabel    != null) algoNameLabel.setText("");
        if (currentStepLabel != null) currentStepLabel.setText("Press ▶ Play to start");
        if (dsTitleLabel     != null) dsTitleLabel.setText("Data Structure:");
        if (dsArea           != null) dsArea.setText("");
        if (visitedArea      != null) visitedArea.setText("");
        if (extraSeparator   != null) { extraSeparator.setVisible(false);  extraSeparator.setManaged(false);  }
        if (extraTitleLabel  != null) { extraTitleLabel.setText(""); extraTitleLabel.setVisible(false); extraTitleLabel.setManaged(false); }
        if (extraArea        != null) { extraArea.setText(""); extraArea.setVisible(false); extraArea.setManaged(false); }
    }

    // --- Intuitive Format Helpers ---

    private String formatQueue(LinkedList<GraphNode> q) {
        if (q.isEmpty()) return "(Queue is empty)";
        List<String> labels = q.stream().map(n -> n.label.getText()).toList();
        return "Front ➔ [ " + String.join(" | ", labels) + " ] ➔ Back";
    }

    private String formatStack(List<String> stack) {
        if (stack.isEmpty()) return "(Stack is empty)";
        StringBuilder sb = new StringBuilder();
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (i == stack.size() - 1) sb.append("  Top  ➔ [ ").append(stack.get(i)).append(" ]\n");
            else if (i == 0)           sb.append("  Base ➔ [ ").append(stack.get(i)).append(" ]\n");
            else                       sb.append("         [ ").append(stack.get(i)).append(" ]\n");
        }
        return sb.toString().trim();
    }

    private String formatDistances(Map<GraphNode, Integer> snap) {
        if (snap == null || snap.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("Node | Distance\n");
        sb.append("─────┼──────────\n");
        for (GraphNode node : nodes) {
            Integer d = snap.get(node);
            String dStr = (d == null || d == Integer.MAX_VALUE) ? "∞" : String.valueOf(d);
            sb.append(String.format("  %-2s | %s%n", node.label.getText(), dStr));
        }
        return sb.toString().trim();
    }

    private String formatMSTEdges(List<String> mstEdges, int total) {
        if (mstEdges.isEmpty()) return "(None yet)";
        return String.join("\n", mstEdges) + "\n─────────────\nTotal weight = " + total;
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
        GraphNode node; List<GraphEdge> associatedEdges; GraphEdge singleEdge;
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
    // INTERNAL GRAPH CLASSES
    // ===============================
    class GraphNode {
        Circle circle; Text label; double offsetX, offsetY;
        List<GraphEdge> connectedEdges = new ArrayList<>();
        Text distLabel;

        GraphNode(double x, double y, String value) {
            circle = new Circle(x, y, 20, Color.LIGHTBLUE);
            circle.setStroke(Color.BLACK); circle.setStrokeWidth(2);
            label = new Text(value); label.setMouseTransparent(true);

            distLabel = new Text("∞");
            distLabel.setFill(Color.DARKRED);
            distLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
            distLabel.setMouseTransparent(true); distLabel.setVisible(false);

            Platform.runLater(() -> {
                label.xProperty().bind(circle.centerXProperty().subtract(label.getLayoutBounds().getWidth() / 2));
                label.yProperty().bind(circle.centerYProperty().add(label.getLayoutBounds().getHeight() / 4));
                distLabel.textProperty().addListener((obs, oldVal, newVal) -> {
                    distLabel.xProperty().unbind();
                    distLabel.xProperty().bind(circle.centerXProperty().subtract(distLabel.getLayoutBounds().getWidth() / 2));
                });
                distLabel.xProperty().bind(circle.centerXProperty().subtract(distLabel.getLayoutBounds().getWidth() / 2));
                distLabel.yProperty().bind(circle.centerYProperty().subtract(circle.getRadius() + 5));
            });
            enableDrag();
        }

        void enableDrag() {
            circle.setOnMousePressed(e -> {
                if (isAlgorithmMode) return;

                Point2D localPoint = graphContentGroup.sceneToLocal(e.getSceneX(), e.getSceneY());
                offsetX = circle.getCenterX() - localPoint.getX();
                offsetY = circle.getCenterY() - localPoint.getY();

                if (!edgeTool.isSelected()) selectNode(this);
                e.consume(); // Prevents canvas pan drag from triggering
            });
            circle.setOnMouseDragged(e -> {
                if (isAlgorithmMode) return;

                Point2D localPoint = graphContentGroup.sceneToLocal(e.getSceneX(), e.getSceneY());
                circle.setCenterX(localPoint.getX() + offsetX);
                circle.setCenterY(localPoint.getY() + offsetY);
                updateConnectedEdges();
                e.consume(); // Prevents canvas pan drag from triggering
            });
            circle.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    handleNodeClick(this);
                }
                e.consume();
            });
        }

        void updateConnectedEdges() { for (GraphEdge edge : connectedEdges) edge.update(); }
    }

    class GraphEdge {
        GraphNode from, to; Line line; Polygon arrowHead; Text weightText;
        boolean isDirected, isWeighted;

        GraphEdge(GraphNode from, GraphNode to, int weight, boolean directed, boolean weighted) {
            this.from = from; this.to = to; this.isDirected = directed; this.isWeighted = weighted;
            line = new Line(); line.setStrokeWidth(3); line.setStroke(Color.BLACK);
            weightText = new Text(String.valueOf(weight)); weightText.setMouseTransparent(true);
            arrowHead  = new Polygon(); arrowHead.setFill(Color.BLACK);

            line.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) selectEdge(this);
                e.consume();
            });
        }

        void update() {
            double sx = from.circle.getCenterX(), sy = from.circle.getCenterY();
            double ex = to.circle.getCenterX(),   ey = to.circle.getCenterY();
            double dx = ex - sx, dy = ey - sy;
            double distance = Math.sqrt(dx * dx + dy * dy);
            if (distance < 1) return;
            double r = from.circle.getRadius();
            double startX = sx + (dx / distance) * r, startY = sy + (dy / distance) * r;
            double endX   = ex - (dx / distance) * r, endY   = ey - (dy / distance) * r;
            line.setStartX(startX); line.setStartY(startY);
            line.setEndX(endX);     line.setEndY(endY);
            if (isWeighted) { weightText.setX((startX + endX) / 2 + 5); weightText.setY((startY + endY) / 2 - 5); }
            if (isDirected) {
                double angle = Math.atan2(dy, dx), al = 12;
                arrowHead.getPoints().setAll(
                        endX, endY,
                        endX - al * Math.cos(angle - Math.PI / 8), endY - al * Math.sin(angle - Math.PI / 8),
                        endX - al * Math.cos(angle + Math.PI / 8), endY - al * Math.sin(angle + Math.PI / 8)
                );
            }
        }
    }

    // ===============================
    // GRAPH BUILDING ACTIONS
    // ===============================
    @FXML
    private void handleCanvasClick(MouseEvent event) {
        if (isAlgorithmMode) return;

        // Ensure only primary clicks create nodes, and ignore if it was a drag gesture
        if (event.getButton() != MouseButton.PRIMARY) return;
        if (canvasDragged) return;
        if (event.getTarget() != canvasPane) return;

        if (nodeTool.isSelected()) {
            // Apply coordinates correctly mapped into the Pan/Zoom space
            Point2D localPoint = graphContentGroup.sceneToLocal(event.getSceneX(), event.getSceneY());
            createNode(localPoint.getX(), localPoint.getY());
        } else {
            clearSelection();
        }
    }

    private void clearSelection() {
        if (selectedNode  != null) selectedNode.circle.setStroke(Color.BLACK);
        if (selectedEdge  != null) selectedEdge.line.setStroke(Color.BLACK);
        selectedNode = null; selectedEdge = null;
        if (firstEdgeNode != null) { firstEdgeNode.circle.setStroke(Color.BLACK); firstEdgeNode = null; }
    }

    private void createNode(double x, double y) {
        String value = (customNodeCheck.isSelected() && !nodeValueField.getText().isEmpty())
                ? nodeValueField.getText() : String.valueOf(nodeCounter++);
        GraphNode node = new GraphNode(x, y, value);
        restoreNodeInternal(node);
        undoStack.push(new AddNodeCommand(node));
        updateGraphRepresentations();
    }

    private void handleNodeClick(GraphNode node) {
        if (isAlgorithmMode) return;
        if (edgeTool.isSelected()) {
            if (firstEdgeNode == null) { firstEdgeNode = node; node.circle.setStroke(Color.ORANGE); }
            else {
                if (firstEdgeNode != node) createEdge(firstEdgeNode, node);
                firstEdgeNode.circle.setStroke(Color.BLACK); firstEdgeNode = null;
            }
        } else { selectNode(node); }
    }

    private void createEdge(GraphNode from, GraphNode to) {
        if (edges.stream().anyMatch(e -> e.from == from && e.to == to)) return;
        int weight = 1;
        try { weight = Integer.parseInt(weightField.getText()); } catch (NumberFormatException ignored) {}
        GraphEdge edge = new GraphEdge(from, to, weight, directedCheck.isSelected(), weightedCheck.isSelected());
        restoreEdgeInternal(edge); undoStack.push(new AddEdgeCommand(edge));
        updateGraphRepresentations();
    }

    private void selectNode(GraphNode node) { clearSelection(); selectedNode = node; node.circle.setStroke(Color.RED); }
    private void selectEdge(GraphEdge edge) { clearSelection(); selectedEdge = edge; edge.line.setStroke(Color.RED); }

    @FXML
    private void deleteSelected() {
        GraphNode nodeToDelete = selectedNode != null ? selectedNode : firstEdgeNode;
        if (nodeToDelete != null) {
            List<GraphEdge> toRemove = new ArrayList<>(nodeToDelete.connectedEdges);
            undoStack.push(new DeleteCommand(nodeToDelete, new ArrayList<>(toRemove)));
            toRemove.forEach(this::removeEdgeInternal);
            removeNodeInternal(nodeToDelete);
            selectedNode = null; firstEdgeNode = null;
        } else if (selectedEdge != null) {
            undoStack.push(new DeleteCommand(selectedEdge));
            removeEdgeInternal(selectedEdge); selectedEdge = null;
        }
        updateGraphRepresentations();
    }

    private void handleUndo() {
        if (!undoStack.isEmpty()) { undoStack.pop().undo(); clearSelection(); updateGraphRepresentations(); }
    }

    private void removeNodeInternal(GraphNode node) {
        nodes.remove(node);
        graphContentGroup.getChildren().removeAll(node.circle, node.label, node.distLabel);
    }

    private void restoreNodeInternal(GraphNode node) {
        if (!nodes.contains(node)) nodes.add(node);
        if (!graphContentGroup.getChildren().contains(node.circle))
            graphContentGroup.getChildren().addAll(node.circle, node.label, node.distLabel);
    }

    private void removeEdgeInternal(GraphEdge edge) {
        edges.remove(edge);
        edge.from.connectedEdges.remove(edge); edge.to.connectedEdges.remove(edge);
        graphContentGroup.getChildren().removeAll(edge.line, edge.arrowHead, edge.weightText);
    }

    private void restoreEdgeInternal(GraphEdge edge) {
        if (!edges.contains(edge)) edges.add(edge);
        if (!edge.from.connectedEdges.contains(edge)) edge.from.connectedEdges.add(edge);
        if (!edge.to.connectedEdges.contains(edge))   edge.to.connectedEdges.add(edge);
        if (!graphContentGroup.getChildren().contains(edge.line)) {
            int idx = 0;
            graphContentGroup.getChildren().add(idx++, edge.line);
            if (edge.isDirected) graphContentGroup.getChildren().add(idx++, edge.arrowHead);
            if (edge.isWeighted) graphContentGroup.getChildren().add(idx,   edge.weightText);
        }
        edge.update();
    }

    @FXML
    public void generateRandomGraph() {
        clearGraph();
        Random random = new Random();
        double width  = canvasPane.getWidth()  > 0 ? canvasPane.getWidth()  : 600;
        double height = canvasPane.getHeight() > 0 ? canvasPane.getHeight() : 400;
        int numNodes  = random.nextInt(4) + 5;
        double cx = width / 2, cy = height / 2, r = Math.min(cx, cy) - 50;
        double step = 2 * Math.PI / numNodes;

        for (int i = 0; i < numNodes; i++) {
            GraphNode node = new GraphNode(cx + r * Math.cos(i * step), cy + r * Math.sin(i * step), String.valueOf(nodeCounter++));
            restoreNodeInternal(node);
        }
        boolean directed = directedCheck.isSelected(), weighted = weightedCheck.isSelected();
        List<GraphNode> connected = new ArrayList<>(), unconnected = new ArrayList<>(nodes);
        connected.add(unconnected.remove(random.nextInt(unconnected.size())));
        while (!unconnected.isEmpty()) {
            GraphNode from = connected.get(random.nextInt(connected.size()));
            GraphNode to   = unconnected.remove(random.nextInt(unconnected.size()));
            restoreEdgeInternal(new GraphEdge(from, to, weighted ? random.nextInt(20) + 1 : 1, directed, weighted));
            connected.add(to);
        }
        for (int i = 0; i < random.nextInt(3); i++) {
            GraphNode from = nodes.get(random.nextInt(nodes.size()));
            GraphNode to   = nodes.get(random.nextInt(nodes.size()));
            if (from != to) {
                boolean exists = edges.stream().anyMatch(e ->
                        (e.from == from && e.to == to) || (!directed && e.from == to && e.to == from));
                if (!exists)
                    restoreEdgeInternal(new GraphEdge(from, to, weighted ? random.nextInt(20) + 1 : 1, directed, weighted));
            }
        }
        updateGraphRepresentations();
    }

    @FXML
    public void clearGraph() {
        nodes.clear();
        edges.clear();
        graphContentGroup.getChildren().clear();
        undoStack.clear();
        nodeCounter = 1;
        clearSelection();
        updateGraphRepresentations();
        resetPanAndZoom();
    }

    // ===============================
    // CAPTURE & RECORDING LOGIC
    // ===============================
    @FXML
    void takeScreenshot() {
        WritableImage snapshot = canvasPane.snapshot(null, null);
        BufferedImage buffered = SwingFXUtils.fromFXImage(snapshot, null);

        String downloadsDir = getDownloadsPath();
        String timestamp    = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File   outputFile   = new File(downloadsDir, "graph_" + timestamp + ".png");

        try {
            ImageIO.write(buffered, "png", outputFile);
            System.out.println("Screenshot saved: " + outputFile.getAbsolutePath());
        } catch (IOException ex) {
            System.err.println("Screenshot failed: " + ex.getMessage());
        }
    }

    @FXML
    void toggleRecording() {
        if (!isRecording) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void startRecording() {
        isRecording = true;
        recordedFrames.clear();
        recordBtn.setText("⏹");
        recordBtn.setStyle(
                "-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-size: 14px;" +
                        "-fx-background-radius: 6; -fx-cursor: hand; -fx-border-color: #991b1b; -fx-border-radius: 6;"
        );

        recordingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "screen-recorder");
            t.setDaemon(true);
            return t;
        });

        recordingExecutor.scheduleAtFixedRate(() -> {
            Platform.runLater(() -> {
                if (!isRecording) return;
                WritableImage frame = canvasPane.snapshot(null, null);
                BufferedImage buffered = SwingFXUtils.fromFXImage(frame, null);
                synchronized (recordedFrames) {
                    recordedFrames.add(buffered);
                }
            });
        }, 0, 1000 / RECORD_FPS, TimeUnit.MILLISECONDS);

        System.out.println("Recording started...");
    }

    private void stopRecording() {
        isRecording = false;

        if (recordingExecutor != null) {
            recordingExecutor.shutdownNow();
            recordingExecutor = null;
        }

        // Instantly revert to default record state
        recordBtn.setText("🎥 Record");
        recordBtn.setStyle("-fx-background-color: rgba(255,255,255,0.15); -fx-text-fill: white; -fx-font-size: 14px; -fx-background-radius: 6; -fx-cursor: hand; -fx-border-color: rgba(255,255,255,0.25); -fx-border-radius: 6;");

        List<BufferedImage> frames;
        synchronized (recordedFrames) {
            frames = new ArrayList<>(recordedFrames);
        }

        if (frames.isEmpty()) {
            return;
        }

        // Save frames as mp4 video on a background thread
        List<BufferedImage> finalFrames = frames;
        Thread saveThread = new Thread(() -> saveMp4Video(finalFrames), "mp4-saver");
        saveThread.setDaemon(true);
        saveThread.start();
    }

    private void saveMp4Video(List<BufferedImage> frames) {
        String downloadsDir = getDownloadsPath();
        String timestamp    = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File   outputFile   = new File(downloadsDir, "graph_rec_" + timestamp + ".mp4");

        try {
            int originalWidth = frames.get(0).getWidth();
            int originalHeight = frames.get(0).getHeight();

            int evenWidth = (originalWidth % 2 == 0) ? originalWidth : originalWidth + 1;
            int evenHeight = (originalHeight % 2 == 0) ? originalHeight : originalHeight + 1;

            AWTSequenceEncoder encoder = AWTSequenceEncoder.createSequenceEncoder(outputFile, RECORD_FPS);

            for (BufferedImage frame : frames) {
                BufferedImage bgrFrame = new BufferedImage(evenWidth, evenHeight, BufferedImage.TYPE_3BYTE_BGR);
                java.awt.Graphics2D g = bgrFrame.createGraphics();
                g.drawImage(frame, 0, 0, evenWidth, evenHeight, null);
                g.dispose();

                encoder.encodeImage(bgrFrame);
            }

            encoder.finish();
            System.out.println("Video saved: " + outputFile.getAbsolutePath());
        } catch (Exception ex) {
            System.err.println("Video save failed: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private String getDownloadsPath() {
        String home = System.getProperty("user.home");
        Path   dl   = Paths.get(home, "Downloads");
        if (!dl.toFile().exists()) dl.toFile().mkdirs();
        return dl.toString();
    }

    // ===============================
    // NAVIGATION & VIEW TOGGLES
    // ===============================
    @FXML
    private void handleBackButton(ActionEvent event) throws IOException {
        stopAll();
        Launcher.switchScene("hello-view.fxml");
    }

    private void stopAll() {
        if (isRecording) stopRecording();
        resetAlgorithmState();
        clearSelection();
    }

    @FXML
    private void toggleDataPane(ActionEvent event) {
        ToggleButton clickedBtn = (ToggleButton) event.getSource();
        boolean showPane = clickedBtn.isSelected();
        if (dataToggleBuild != null) dataToggleBuild.setSelected(showPane);
        if (dataToggleAlgo  != null) dataToggleAlgo.setSelected(showPane);

        if (dataPane != null) {
            dataPane.setVisible(showPane);
            dataPane.setManaged(showPane);
        }
        if (showPane) updateGraphRepresentations();
    }

    @FXML
    private void switchToAlgoMode() {
        if (nodes.isEmpty()) { System.out.println("Graph is empty! Build a graph first."); return; }
        isAlgorithmMode = true; clearSelection();
        resultLabel.setText("Select an algorithm and press Play!");

        boolean hasUnweightedEdges = edges.stream().anyMatch(e -> !e.isWeighted);
        String savedSel = algoComboBox.getValue();
        List<String> items = new ArrayList<>(algoComboBox.getItems());
        algoComboBox.getItems().clear(); algoComboBox.getItems().addAll(items);
        if (savedSel != null && (
                (hasUnweightedEdges && (savedSel.contains("Dijkstra") || savedSel.contains("Prim") || savedSel.contains("Kruskal")))
                        || (savedSel.contains("Topological") && !isDAG()))) {
            algoComboBox.setValue(null);
        } else {
            algoComboBox.setValue(savedSel);
        }

        buildToolbar.setVisible(false);
        algoToolbar.setVisible(true);
        playbackToolbar.setVisible(true);
        playbackToolbar.setManaged(true);

        if (algoStatePane != null) { algoStatePane.setVisible(true); algoStatePane.setManaged(true); }
        clearStatePanel();
    }

    @FXML
    private void switchToBuildMode() {
        isAlgorithmMode = false;
        resetAlgorithmState();
        algoToolbar.setVisible(false);
        playbackToolbar.setVisible(false);
        playbackToolbar.setManaged(false);
        buildToolbar.setVisible(true);

        if (algoStatePane != null) { algoStatePane.setVisible(false); algoStatePane.setManaged(false); }
    }

    // ===============================
    // GRAPH REPRESENTATION
    // ===============================
    private void updateGraphRepresentations() {
        if (adjListArea == null || adjMatrixArea == null) return;
        StringBuilder listBuilder = new StringBuilder();
        for (GraphNode node : nodes) {
            listBuilder.append(node.label.getText()).append(" -> ");
            List<String> neighbors = new ArrayList<>();
            for (GraphEdge edge : edges) {
                if (edge.from == node)
                    neighbors.add(edge.to.label.getText() + (edge.isWeighted ? "(" + edge.weightText.getText() + ")" : ""));
                else if (!edge.isDirected && edge.to == node)
                    neighbors.add(edge.from.label.getText() + (edge.isWeighted ? "(" + edge.weightText.getText() + ")" : ""));
            }
            listBuilder.append(String.join(", ", neighbors)).append("\n");
        }
        adjListArea.setText(listBuilder.toString());

        int n = nodes.size();
        if (n == 0) { adjMatrixArea.setText(""); return; }
        String[][] matrix = new String[n][n];
        for (int i = 0; i < n; i++) Arrays.fill(matrix[i], "0");
        Map<GraphNode, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < n; i++) indexMap.put(nodes.get(i), i);
        for (GraphEdge edge : edges) {
            Integer u = indexMap.get(edge.from), v = indexMap.get(edge.to);
            if (u == null || v == null) continue;
            String w = edge.isWeighted ? edge.weightText.getText() : "1";
            matrix[u][v] = w;
            if (!edge.isDirected) matrix[v][u] = w;
        }
        StringBuilder matrixBuilder = new StringBuilder();
        matrixBuilder.append(String.format("%-6s", ""));
        for (GraphNode node : nodes) matrixBuilder.append(String.format("%-6s", node.label.getText()));
        matrixBuilder.append("\n");
        for (int i = 0; i < n; i++) {
            matrixBuilder.append(String.format("%-6s", nodes.get(i).label.getText()));
            for (int j = 0; j < n; j++) matrixBuilder.append(String.format("%-6s", matrix[i][j]));
            matrixBuilder.append("\n");
        }
        adjMatrixArea.setText(matrixBuilder.toString());
    }

    // ===============================
    // ALGORITHM CORE LOGIC
    // ===============================
    @FXML
    private void resetGraphColors() {
        for (GraphNode n : nodes) {
            n.circle.setFill(Color.LIGHTBLUE); n.circle.setStroke(Color.BLACK);
            n.distLabel.setText("∞"); n.distLabel.setFill(Color.DARKRED); n.distLabel.setVisible(false);
        }
        for (GraphEdge e : edges) { e.line.setStroke(Color.BLACK); e.line.setStrokeWidth(3); }
    }

    private GraphNode findNodeByValue(String value) {
        if (value == null || value.isEmpty()) return null;
        for (GraphNode node : nodes) if (node.label.getText().equals(value)) return node;
        return null;
    }

    private void initializeAlgorithm() {
        if (timeline != null) { timeline.stop(); timeline = null; }
        resetGraphColors();
        resultLabel.setText("Starting Algorithm...");
        algorithmSteps.clear();
        currentStep = 0;

        String selectedAlgo = algoComboBox.getValue();
        if (algoNameLabel != null) algoNameLabel.setText(selectedAlgo != null ? selectedAlgo : "");

        GraphNode startNode = findNodeByValue(startNodeField.getText());
        if (startNode == null && !nodes.isEmpty()) startNode = nodes.get(0);
        GraphNode endNode = findNodeByValue(endNodeField.getText());

        if (selectedAlgo != null) {
            if      (selectedAlgo.startsWith("BFS"))         recordBFS(startNode);
            else if (selectedAlgo.startsWith("DFS"))         recordDFS(startNode);
            else if (selectedAlgo.startsWith("Prim")) {
                if (edges.stream().anyMatch(e -> !e.isWeighted)) { resultLabel.setText("Error: Prim's MST requires a fully weighted graph!"); return; }
                recordPrim(startNode);
            }
            else if (selectedAlgo.startsWith("Kruskal")) {
                if (edges.stream().anyMatch(e -> !e.isWeighted)) { resultLabel.setText("Error: Kruskal's MST requires a fully weighted graph!"); return; }
                recordKruskal();
            }
            else if (selectedAlgo.startsWith("Dijkstra")) {
                if (edges.stream().anyMatch(e -> !e.isWeighted)) { resultLabel.setText("Error: Dijkstra requires a fully weighted graph!"); return; }
                recordDijkstra(startNode, endNode);
            }
            else if (selectedAlgo.startsWith("Topological")) {
                if (!isDAG()) { resultLabel.setText("Error: Graph must be a directed acyclic graph (DAG)!"); return; }
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
            timeline.pause(); playPauseButton.setText("▶ Play"); return;
        }
        if (algorithmSteps.isEmpty() || currentStep >= algorithmSteps.size()) initializeAlgorithm();
        if (timeline == null) setupTimeline();
        timeline.play(); playPauseButton.setText("⏸ Pause");
    }

    @FXML
    private void stepForward() {
        if (nodes.isEmpty() || algoComboBox.getValue() == null) return;
        if (timeline != null) { timeline.pause(); playPauseButton.setText("▶ Play"); }
        if (algorithmSteps.isEmpty() || currentStep >= algorithmSteps.size()) {
            initializeAlgorithm();
            if (timeline == null) setupTimeline();
        }
        if (currentStep < algorithmSteps.size()) { algorithmSteps.get(currentStep).run(); currentStep++; }
    }

    @FXML
    private void stepBackward() {
        if (algorithmSteps.isEmpty() || currentStep <= 0) return;
        if (timeline != null) { timeline.pause(); playPauseButton.setText("▶ Play"); }
        currentStep--;
        resetGraphColors();
        resultLabel.setText("");
        clearStatePanel();
        for (int i = 0; i < currentStep; i++) algorithmSteps.get(i).run();
    }

    @FXML
    private void resetAlgorithmState() {
        if (timeline != null) { timeline.stop(); timeline = null; }
        if (playPauseButton != null) playPauseButton.setText("▶ Play");
        resetGraphColors();
        if (isAlgorithmMode) resultLabel.setText("Select an algorithm and press Play!");
        else                 resultLabel.setText("");
        algorithmSteps.clear(); currentStep = 0;
        clearStatePanel();
    }

    // ===============================
    // ALGORITHM IMPLEMENTATIONS
    // ===============================

    // ─────────────────────────────────────────────
    // BFS
    // ─────────────────────────────────────────────
    private void recordBFS(GraphNode startNode) {
        final String DS_TITLE = "Active Queue:";

        LinkedList<GraphNode> queue = new LinkedList<>();
        Set<GraphNode>        visited     = new LinkedHashSet<>();
        List<String>          visitedOrder = new ArrayList<>();

        queue.add(startNode);
        visited.add(startNode);

        final String initQ = formatQueue(queue);
        algorithmSteps.add(() -> {
            startNode.circle.setFill(Color.YELLOW);
            setAlgoState("Found starting node '" + startNode.label.getText() + "' and pushed to Queue.",
                    DS_TITLE, initQ, "(None)", null, null);
        });

        while (!queue.isEmpty()) {
            GraphNode current = queue.poll();
            visitedOrder.add(current.label.getText());

            final GraphNode exploringNode = current;
            final String visitStr   = String.join(" ➔ ", visitedOrder);
            final String resultPath = "Traversal Order: " + visitStr;
            final String qSnap      = formatQueue(queue);

            algorithmSteps.add(() -> {
                exploringNode.circle.setFill(Color.MAGENTA);
                resultLabel.setText(resultPath);
                setAlgoState("Popped '" + exploringNode.label.getText() + "' from Queue for exploration.",
                        DS_TITLE, qSnap, visitStr, null, null);
            });

            for (GraphEdge edge : current.connectedEdges) {
                GraphNode neighbor = null;
                if      (edge.from == current)                       neighbor = edge.to;
                else if (!edge.isDirected && edge.to == current)     neighbor = edge.from;

                if (neighbor != null && !visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);

                    final GraphEdge te       = edge;
                    final GraphNode nextNode = neighbor;
                    final String updatedQ    = formatQueue(queue);
                    final String vs          = String.join(" ➔ ", visitedOrder);

                    algorithmSteps.add(() -> {
                        te.line.setStroke(Color.ORANGE);
                        nextNode.circle.setFill(Color.YELLOW);
                        setAlgoState("Discovered unvisited neighbor '" + nextNode.label.getText() + "' -> Enqueuing.",
                                DS_TITLE, updatedQ, vs, null, null);
                    });
                }
            }

            final String visitStr2 = String.join(" ➔ ", visitedOrder);
            final String finalQSnap = formatQueue(queue);

            algorithmSteps.add(() -> {
                exploringNode.circle.setFill(Color.GREEN);
                setAlgoState("Finished exploring '" + exploringNode.label.getText() + "'. Marked as complete.",
                        DS_TITLE, finalQSnap, visitStr2, null, null);
            });
        }

        final String finalOrder = String.join(" ➔ ", visitedOrder);
        algorithmSteps.add(() -> {
            resultLabel.setText("BFS Complete! Order: " + finalOrder);
            setAlgoState("✅ BFS Traversal Complete!", DS_TITLE, formatQueue(queue), finalOrder, null, null);
        });
    }

    // ─────────────────────────────────────────────
    // DFS
    // ─────────────────────────────────────────────
    private void recordDFS(GraphNode startNode) {
        final String DS_TITLE = "Recursion Call Stack:";
        Set<GraphNode> visited     = new HashSet<>();
        List<String>   visitedOrder = new ArrayList<>();
        List<String>   callStack   = new ArrayList<>();

        dfsHelper(startNode, null, visited, visitedOrder, callStack, DS_TITLE);

        final String finalOrder = String.join(" ➔ ", visitedOrder);
        algorithmSteps.add(() -> {
            resultLabel.setText("DFS Complete! Order: " + finalOrder);
            setAlgoState("✅ DFS Traversal Complete!", DS_TITLE, formatStack(new ArrayList<>()), finalOrder, null, null);
        });
    }

    private void dfsHelper(GraphNode current, GraphEdge edgeToReach,
                           Set<GraphNode> visited, List<String> visitedOrder,
                           List<String> callStack, String dsTitle) {
        visited.add(current);
        callStack.add(current.label.getText());
        visitedOrder.add(current.label.getText());

        final List<String> stackSnap = new ArrayList<>(callStack);
        final String stackStr  = formatStack(stackSnap);
        final String visitStr  = String.join(" ➔ ", visitedOrder);
        final String resultPath = "Traversal Order: " + visitStr;
        final GraphNode cn = current;

        if (edgeToReach != null) {
            final GraphEdge te = edgeToReach;
            algorithmSteps.add(() -> {
                te.line.setStroke(Color.ORANGE);
                cn.circle.setFill(Color.YELLOW);
                resultLabel.setText(resultPath);
                setAlgoState("Traversing deep into node '" + cn.label.getText() + "' (Push to Stack).",
                        dsTitle, stackStr, visitStr, null, null);
            });
        } else {
            algorithmSteps.add(() -> {
                cn.circle.setFill(Color.YELLOW);
                resultLabel.setText(resultPath);
                setAlgoState("Starting DFS at node '" + cn.label.getText() + "' (Push to Stack).",
                        dsTitle, stackStr, visitStr, null, null);
            });
        }

        final GraphNode exploringNode = current;
        algorithmSteps.add(() -> {
            exploringNode.circle.setFill(Color.MAGENTA);
            setAlgoState("Checking neighbors of '" + exploringNode.label.getText() + "'...",
                    dsTitle, stackStr, visitStr, null, null);
        });

        for (GraphEdge edge : current.connectedEdges) {
            GraphNode neighbor = null;
            if      (edge.from == current)                    neighbor = edge.to;
            else if (!edge.isDirected && edge.to == current) neighbor = edge.from;
            if (neighbor != null && !visited.contains(neighbor))
                dfsHelper(neighbor, edge, visited, visitedOrder, callStack, dsTitle);
        }

        callStack.remove(callStack.size() - 1);
        final List<String> poppedStack   = new ArrayList<>(callStack);
        final String       poppedStr     = formatStack(poppedStack);
        final String       visitStr2     = String.join(" ➔ ", visitedOrder);
        final GraphNode    finishedNode  = current;

        algorithmSteps.add(() -> {
            finishedNode.circle.setFill(Color.GREEN);
            setAlgoState("No unvisited neighbors left for '" + finishedNode.label.getText() + "'. Backtracking (Pop from Stack).",
                    dsTitle, poppedStr, visitStr2, null, null);
        });
    }

    // ─────────────────────────────────────────────
    // Prim's MST
    // ─────────────────────────────────────────────
    private void recordPrim(GraphNode startNode) {
        final String DS_TITLE    = "Candidate Edges (Min-Heap):";
        final String EXTRA_TITLE = "MST Edges Chosen:";

        Set<GraphNode>        visited  = new HashSet<>();
        PriorityQueue<GraphEdge> pq    = new PriorityQueue<>(Comparator.comparingInt(this::parseWeight));
        List<String>          mstEdges = new ArrayList<>();
        int[]                 totalW   = {0};

        visited.add(startNode);
        for (GraphEdge e : startNode.connectedEdges) pq.add(e);

        final String initPQ  = formatEdgePQ(pq);
        final String initMST = "(None)";
        algorithmSteps.add(() -> {
            startNode.circle.setFill(Color.YELLOW);
            resultLabel.setText("Prim's MST: Started at " + startNode.label.getText());
            setAlgoState("Initialized Prim's at '" + startNode.label.getText() + "'. Added adjacent edges to PQ.",
                    DS_TITLE, initPQ, startNode.label.getText(), EXTRA_TITLE, initMST);
        });

        while (!pq.isEmpty() && visited.size() < nodes.size()) {
            GraphEdge minEdge = pq.poll();

            GraphNode unvisited = null;
            if      (visited.contains(minEdge.from) && !visited.contains(minEdge.to))   unvisited = minEdge.to;
            else if (!minEdge.isDirected && visited.contains(minEdge.to)
                    && !visited.contains(minEdge.from))                                  unvisited = minEdge.from;

            if (unvisited != null) {
                visited.add(unvisited);
                int w = parseWeight(minEdge);
                totalW[0] += w;
                mstEdges.add("  " + minEdge.from.label.getText() + " ─ " + minEdge.to.label.getText()
                        + "  (Weight: " + w + ")");

                for (GraphEdge e : unvisited.connectedEdges) {
                    GraphNode nb = (e.from == unvisited) ? e.to
                            : (!e.isDirected && e.to == unvisited) ? e.from : null;
                    if (nb != null && !visited.contains(nb)) pq.add(e);
                }

                final GraphNode   nextNode    = unvisited;
                final GraphEdge   mstEdge     = minEdge;
                final int         currTotal   = totalW[0];
                final String      pqSnap      = formatEdgePQ(pq);
                final String      visitSnap   = String.join(", ", visited.stream().map(n -> n.label.getText()).toList());
                final String      mstSnap     = formatMSTEdges(mstEdges, currTotal);

                algorithmSteps.add(() -> {
                    mstEdge.line.setStroke(Color.ORANGE); mstEdge.line.setStrokeWidth(5);
                    nextNode.circle.setFill(Color.YELLOW);
                    resultLabel.setText("Prim's MST - Total Weight: " + currTotal);
                    setAlgoState("Extracted minimum edge connecting to '" + nextNode.label.getText() + "'.",
                            DS_TITLE, pqSnap, visitSnap, EXTRA_TITLE, mstSnap);
                });
            }
        }

        final String finalVisit = String.join(", ", visited.stream().map(n -> n.label.getText()).toList());
        final String finalMST   = formatMSTEdges(mstEdges, totalW[0]);
        algorithmSteps.add(() -> {
            for (GraphNode node : visited) node.circle.setFill(Color.GREEN);
            resultLabel.setText("Prim's MST Complete! Total Weight: " + totalW[0]);
            setAlgoState("✅ All nodes connected! Prim's MST Complete.", DS_TITLE, "(Empty)", finalVisit, EXTRA_TITLE, finalMST);
        });
    }

    // ─────────────────────────────────────────────
    // Kruskal's MST
    // ─────────────────────────────────────────────
    private void recordKruskal() {
        final String DS_TITLE    = "Sorted Edges Remaining:";
        final String EXTRA_TITLE = "MST Edges Chosen:";

        Map<GraphNode, GraphNode> parent = new HashMap<>();
        for (GraphNode node : nodes) parent.put(node, node);

        java.util.function.Function<GraphNode, GraphNode> find = new java.util.function.Function<>() {
            @Override public GraphNode apply(GraphNode node) {
                if (parent.get(node) == node) return node;
                GraphNode root = apply(parent.get(node)); parent.put(node, root); return root;
            }
        };

        List<GraphEdge> sortedEdges = new ArrayList<>(edges);
        sortedEdges.sort(Comparator.comparingInt(this::parseWeight));

        List<String> mstEdges   = new ArrayList<>();
        Set<GraphNode> mstNodes = new HashSet<>();
        int[] totalW = {0};
        int[] edgesAdded = {0};

        LinkedList<GraphEdge> remaining = new LinkedList<>(sortedEdges);

        final String initRemaining = formatEdgeList(remaining, 6);
        algorithmSteps.add(() -> {
            resultLabel.setText("Kruskal's MST: Sorted all edges globally by weight.");
            setAlgoState("Sorted all edges by weight. Ready to pick the smallest non-cycling edges.",
                    DS_TITLE, initRemaining, "(None)", EXTRA_TITLE, "(None)");
        });

        for (GraphEdge edge : sortedEdges) {
            if (edgesAdded[0] >= nodes.size() - 1) break;
            remaining.remove(edge);

            GraphNode root1 = find.apply(edge.from), root2 = find.apply(edge.to);
            boolean   cycle = (root1 == root2);

            if (!cycle) {
                parent.put(root1, root2);
                edgesAdded[0]++;
                mstNodes.add(edge.from); mstNodes.add(edge.to);
                int w = parseWeight(edge);
                totalW[0] += w;
                mstEdges.add("  ✓ " + edge.from.label.getText() + " ─ " + edge.to.label.getText() + "  (Weight: " + w + ")");

                final GraphEdge mstEdge    = edge;
                final String    remSnap    = formatEdgeList(remaining, 6);
                final String    visitSnap  = String.join(", ", mstNodes.stream().map(n -> n.label.getText()).toList());
                final String    mstSnap    = formatMSTEdges(mstEdges, totalW[0]);
                final int       currTotal  = totalW[0];

                algorithmSteps.add(() -> {
                    mstEdge.line.setStroke(Color.ORANGE); mstEdge.line.setStrokeWidth(5);
                    mstEdge.from.circle.setFill(Color.YELLOW); mstEdge.to.circle.setFill(Color.YELLOW);
                    resultLabel.setText("Kruskal's MST - Total Weight: " + currTotal);
                    setAlgoState("✓ Edge safely bridges components without forming a cycle.",
                            DS_TITLE, remSnap, visitSnap, EXTRA_TITLE, mstSnap);
                });
            } else {
                final GraphEdge cycleEdge = edge;
                final String    remSnap2  = formatEdgeList(remaining, 6);
                final String    visitSnap2= String.join(", ", mstNodes.stream().map(n -> n.label.getText()).toList());
                final String    mstSnap2  = mstEdges.isEmpty() ? "(None)" : formatMSTEdges(mstEdges, totalW[0]);

                algorithmSteps.add(() -> {
                    cycleEdge.line.setStroke(Color.RED);
                    setAlgoState("✗ Skipped edge: Connecting '" + cycleEdge.from.label.getText() + "' and '" + cycleEdge.to.label.getText() + "' creates a cycle.",
                            DS_TITLE, remSnap2, visitSnap2, EXTRA_TITLE, mstSnap2);
                });
            }
        }

        final String finalVisit = String.join(", ", mstNodes.stream().map(n -> n.label.getText()).toList());
        final String finalMST   = formatMSTEdges(mstEdges, totalW[0]);
        algorithmSteps.add(() -> {
            for (GraphNode node : mstNodes) node.circle.setFill(Color.GREEN);
            resultLabel.setText("Kruskal's MST Complete! Total Weight: " + totalW[0]);
            setAlgoState("✅ Maximum edges reached. Kruskal's MST Complete!", DS_TITLE, "(Empty)", finalVisit, EXTRA_TITLE, finalMST);
        });
    }

    // ─────────────────────────────────────────────
    // Dijkstra's Shortest Path
    // ─────────────────────────────────────────────
    private void recordDijkstra(GraphNode startNode, GraphNode endNode) {
        final String DS_TITLE    = "Priority Queue (Node, Dist):";
        final String EXTRA_TITLE = "Distance Map:";

        Map<GraphNode, Integer> distances = new HashMap<>();
        Map<GraphNode, GraphEdge> edgeTo  = new HashMap<>();
        Set<GraphNode>            settled  = new HashSet<>();
        List<String>              settledOrder = new ArrayList<>();

        class ND implements Comparable<ND> {
            GraphNode node; int dist;
            ND(GraphNode n, int d) { node = n; dist = d; }
            public int compareTo(ND o) { return Integer.compare(dist, o.dist); }
        }
        PriorityQueue<ND> pq = new PriorityQueue<>();
        for (GraphNode n : nodes) distances.put(n, Integer.MAX_VALUE);
        distances.put(startNode, 0);
        pq.add(new ND(startNode, 0));

        java.util.function.Supplier<String> snapPQ = () -> {
            List<ND> list = new ArrayList<>(pq); Collections.sort(list);
            StringBuilder sb = new StringBuilder();
            int c = 0;
            for (ND nd : list) {
                if (c++ >= 7) { sb.append("  …\n"); break; }
                sb.append(String.format("  %-4s → dist = %s%n",
                        nd.node.label.getText(),
                        nd.dist == Integer.MAX_VALUE ? "∞" : nd.dist));
            }
            return sb.length() == 0 ? "(Empty)" : sb.toString().trim();
        };

        final Map<GraphNode, Integer> distSnap0 = new HashMap<>(distances);
        final String pqSnap0 = snapPQ.get();
        algorithmSteps.add(() -> {
            for (GraphNode n : nodes) n.distLabel.setVisible(true);
            startNode.circle.setFill(Color.YELLOW);
            startNode.distLabel.setText("0"); startNode.distLabel.setFill(Color.GREEN);
            resultLabel.setText("Dijkstra: Starting at " + startNode.label.getText());
            setAlgoState("Set starting node distance to 0. All other nodes are ∞.",
                    DS_TITLE, pqSnap0, "(None settled)", EXTRA_TITLE, formatDistances(distSnap0));
        });

        while (!pq.isEmpty()) {
            ND current = pq.poll();
            GraphNode u = current.node;
            if (settled.contains(u)) continue;
            settled.add(u);
            settledOrder.add(u.label.getText());

            final GraphNode exploringNode = u;
            final int       currDist      = current.dist;
            final String    settled0      = String.join(" ➔ ", settledOrder);
            final String    pqSnap1       = snapPQ.get();
            final Map<GraphNode, Integer> distSnap1 = new HashMap<>(distances);

            algorithmSteps.add(() -> {
                if (exploringNode != startNode) exploringNode.circle.setFill(Color.MAGENTA);
                exploringNode.distLabel.setFill(Color.DARKBLUE);
                resultLabel.setText("Dijkstra: Locking in " + exploringNode.label.getText() + " at optimal distance: " + currDist);
                setAlgoState("Locked in optimal distance for '" + exploringNode.label.getText() + "'. Evaluating neighbors...",
                        DS_TITLE, pqSnap1, settled0, EXTRA_TITLE, formatDistances(distSnap1));
            });

            if (endNode != null && u == endNode) break;

            for (GraphEdge edge : u.connectedEdges) {
                GraphNode v = null;
                if      (edge.from == u)                        v = edge.to;
                else if (!edge.isDirected && edge.to == u)     v = edge.from;

                if (v != null && !settled.contains(v)) {
                    int w      = parseWeight(edge);
                    int newDist = currDist + w;

                    if (newDist < distances.get(v)) {
                        distances.put(v, newDist);
                        edgeTo.put(v, edge);
                        pq.add(new ND(v, newDist));

                        final GraphNode   nb       = v;
                        final GraphEdge   te       = edge;
                        final int         ndist    = newDist;
                        final String      pqSnap2  = snapPQ.get();
                        final String      settled2 = String.join(" ➔ ", settledOrder);
                        final Map<GraphNode, Integer> distSnap2 = new HashMap<>(distances);

                        algorithmSteps.add(() -> {
                            te.line.setStroke(Color.ORANGE);
                            if (nb != startNode) nb.circle.setFill(Color.YELLOW);
                            nb.distLabel.setText(String.valueOf(ndist)); nb.distLabel.setFill(Color.DARKRED);
                            resultLabel.setText("Dijkstra: Relaxed " + nb.label.getText() + " → dist = " + ndist);
                            setAlgoState("Relaxation Step! Found a shorter path to '" + nb.label.getText() + "' (New Dist: " + ndist + ").",
                                    DS_TITLE, pqSnap2, settled2, EXTRA_TITLE, formatDistances(distSnap2));
                        });
                    }
                }
            }

            final String settled3 = String.join(" ➔ ", settledOrder);
            final Map<GraphNode, Integer> distSnap3 = new HashMap<>(distances);
            algorithmSteps.add(() -> {
                if (exploringNode != startNode && exploringNode != endNode) {
                    exploringNode.circle.setFill(Color.LIGHTGREEN);
                    exploringNode.distLabel.setFill(Color.DARKGREEN);
                }
                setAlgoState("✓ Fully evaluated node '" + exploringNode.label.getText() + "'.",
                        DS_TITLE, snapPQ.get(), settled3, EXTRA_TITLE, formatDistances(distSnap3));
            });
        }

        if (endNode != null) {
            if (distances.get(endNode) == Integer.MAX_VALUE) {
                algorithmSteps.add(() -> {
                    resultLabel.setText("Dijkstra: Target " + endNode.label.getText() + " is unreachable!");
                    setAlgoState("Algorithm exhausted. Target node is completely disconnected.", DS_TITLE, "(Empty)",
                            String.join(" ➔ ", settledOrder), null, null);
                });
            } else {
                final int finalDist = distances.get(endNode);
                algorithmSteps.add(() -> {
                    resultLabel.setText("Dijkstra: Shortest path found! Total dist = " + finalDist);
                    setAlgoState("Target Reached! Tracing shortest path backward...",
                            DS_TITLE, "(Empty)", String.join(" ➔ ", settledOrder),
                            EXTRA_TITLE, formatDistances(distances));
                });
                GraphNode curr = endNode;
                List<Runnable> path = new ArrayList<>();
                while (curr != startNode && edgeTo.containsKey(curr)) {
                    GraphEdge e = edgeTo.get(curr);
                    final GraphEdge pe = e; final GraphNode pn = curr;
                    path.add(() -> { pe.line.setStroke(Color.GREEN); pe.line.setStrokeWidth(5);
                        pn.circle.setFill(Color.GREEN); pn.distLabel.setFill(Color.WHITE); });
                    curr = (e.to == curr) ? e.from : e.to;
                }
                Collections.reverse(path);
                algorithmSteps.addAll(path);
                algorithmSteps.add(() -> { startNode.circle.setFill(Color.GREEN); startNode.distLabel.setFill(Color.WHITE); });
            }
        } else {
            algorithmSteps.add(() -> {
                resultLabel.setText("Dijkstra: Shortest path tree computed for all nodes.");
                setAlgoState("All reachable nodes settled. Shortest Path Tree (SPT) generated.", DS_TITLE, "(Empty)",
                        String.join(" ➔ ", settledOrder), EXTRA_TITLE, formatDistances(distances));
            });
        }
    }

    // ─────────────────────────────────────────────
    // Topological Sort
    // ─────────────────────────────────────────────
    private boolean isDAG() {
        if (edges.isEmpty()) return directedCheck.isSelected();
        if (edges.stream().anyMatch(e -> !e.isDirected)) return false;
        Set<GraphNode> visited = new HashSet<>(), recStack = new HashSet<>();
        for (GraphNode node : nodes)
            if (!visited.contains(node) && hasCycleDFS(node, visited, recStack)) return false;
        return true;
    }

    private boolean hasCycleDFS(GraphNode node, Set<GraphNode> visited, Set<GraphNode> recStack) {
        visited.add(node); recStack.add(node);
        for (GraphEdge edge : node.connectedEdges) {
            if (edge.from != node) continue;
            GraphNode nb = edge.to;
            if      (!visited.contains(nb) && hasCycleDFS(nb, visited, recStack)) return true;
            else if (recStack.contains(nb))                                         return true;
        }
        recStack.remove(node); return false;
    }

    private void recordTopologicalSort() {
        final String DS_TITLE = "Result Stack:";

        Set<GraphNode> visited    = new HashSet<>();
        List<GraphNode> finished  = new ArrayList<>();
        List<String>    dfsVisit  = new ArrayList<>();
        List<String>    finishStack = new ArrayList<>();

        algorithmSteps.add(() -> {
            resultLabel.setText("Topological Sort: Searching for dependencies...");
            setAlgoState("Running specialized DFS. Nodes will be pushed to the Result Stack upon backtracking.", DS_TITLE, "(Empty)", "(None)", null, null);
        });

        for (GraphNode node : nodes)
            if (!visited.contains(node))
                topoSortHelper(node, visited, finished, dfsVisit, finishStack, DS_TITLE);

        Collections.reverse(finished);
        List<String> labels = new ArrayList<>();
        for (GraphNode n : finished) labels.add(n.label.getText());
        final String finalOrder = String.join(" ➔ ", labels);

        for (int i = 0; i < finished.size(); i++) {
            final GraphNode n         = finished.get(i);
            final String    orderSoFar = String.join(" ➔ ", labels.subList(0, i + 1));
            final String    fsSnap    = formatStack(finishStack);
            algorithmSteps.add(() -> {
                n.circle.setFill(Color.ORANGE);
                resultLabel.setText("Topological Order: " + orderSoFar);
                setAlgoState("Popping dependencies to reveal linear Topological flow: '" + n.label.getText() + "'", DS_TITLE, fsSnap, orderSoFar, null, null);
            });
        }

        algorithmSteps.add(() -> {
            for (GraphNode n : finished)  n.circle.setFill(Color.GREEN);
            for (GraphEdge e : edges)     e.line.setStroke(Color.BLACK);
            resultLabel.setText("Topological Order: " + finalOrder);
            setAlgoState("✅ Directed Acyclic Graph sorted linearly!", DS_TITLE, "(Done)", finalOrder, null, null);
        });
    }

    private void topoSortHelper(GraphNode node, Set<GraphNode> visited, List<GraphNode> finished,
                                List<String> dfsVisit, List<String> finishStack, String dsTitle) {
        visited.add(node);
        dfsVisit.add(node.label.getText());

        final GraphNode visiting  = node;
        final String    vsSnap    = String.join(" ➔ ", dfsVisit);
        final String    fsSnap1   = formatStack(finishStack);

        algorithmSteps.add(() -> {
            visiting.circle.setFill(Color.YELLOW);
            resultLabel.setText("Topological Sort: Visiting " + visiting.label.getText());
            setAlgoState("Checking prerequisites for '" + visiting.label.getText() + "'...", dsTitle, fsSnap1, vsSnap, null, null);
        });

        for (GraphEdge edge : node.connectedEdges) {
            if (edge.from != node) continue;
            GraphNode nb = edge.to;
            if (!visited.contains(nb)) {
                final GraphEdge te = edge;
                algorithmSteps.add(() -> te.line.setStroke(Color.ORANGE));
                topoSortHelper(nb, visited, finished, dfsVisit, finishStack, dsTitle);
            }
        }

        finished.add(node);
        finishStack.add(node.label.getText());

        final List<String> fsSnap2 = new ArrayList<>(finishStack);
        final String       fsStr   = formatStack(fsSnap2);
        final String       vsSnap2 = String.join(" ➔ ", dfsVisit);

        algorithmSteps.add(() -> {
            visiting.circle.setFill(Color.MAGENTA);
            resultLabel.setText("Topological Sort: " + visiting.label.getText() + " dependencies resolved.");
            setAlgoState("All dependencies for '" + visiting.label.getText() + "' resolved. Pushing to Result Stack.", dsTitle, fsStr, vsSnap2, null, null);
        });
    }

    // ===============================
    // PRIVATE UTILITY HELPERS
    // ===============================

    private int parseWeight(GraphEdge e) {
        if (!e.isWeighted) return 1;
        try { return Integer.parseInt(e.weightText.getText()); } catch (NumberFormatException ex) { return 1; }
    }

    private String formatEdgePQ(PriorityQueue<GraphEdge> pq) {
        if (pq.isEmpty()) return "(Empty)";
        List<GraphEdge> list = new ArrayList<>(pq);
        list.sort(Comparator.comparingInt(this::parseWeight));
        StringBuilder sb = new StringBuilder();
        int c = 0;
        for (GraphEdge e : list) {
            if (c++ >= 7) { sb.append("  …\n"); break; }
            sb.append(String.format("  %-4s ─ %-4s | Weight = %-3d%n",
                    e.from.label.getText(), e.to.label.getText(), parseWeight(e)));
        }
        return sb.toString().trim();
    }

    private String formatEdgeList(LinkedList<GraphEdge> list, int max) {
        if (list.isEmpty()) return "(Empty)";
        StringBuilder sb = new StringBuilder();
        int c = 0;
        for (GraphEdge e : list) {
            if (c++ >= max) { sb.append("  …\n"); break; }
            sb.append(String.format("  %-4s ─ %-4s | Weight = %-3d%n",
                    e.from.label.getText(), e.to.label.getText(), parseWeight(e)));
        }
        return sb.toString().trim();
    }
}