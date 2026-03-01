package org.example.VisuAlgorithm;

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

}