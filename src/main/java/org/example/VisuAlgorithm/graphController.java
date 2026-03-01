package org.example.VisuAlgorithm;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Text;

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

    private List<GraphNode> nodes = new ArrayList<>();
    private List<GraphEdge> edges = new ArrayList<>();

    // adjacency list
    private Map<GraphNode, List<GraphNode>> adjacencyList = new HashMap<>();

    private int[][] adjacencyMatrix = new int[0][0];

    // ===============================
    // CANVAS CLICK
    // ===============================
    @FXML
    private void handleCanvasClick(MouseEvent event) {

        // If click target is not canvas itself → ignore
        if (event.getTarget() != canvasPane) {
            return;
        }

        if (nodeTool.isSelected()) {
            createNode(event.getX(), event.getY());
        }
    }

    // ===============================
    // NODE CLASS
    // ===============================
    class GraphNode {
        Circle circle;
        Text label;
        double offsetX, offsetY;

        GraphNode(double x, double y, String value) {

            circle = new Circle(x, y, 20, Color.LIGHTBLUE);
            circle.setStroke(Color.BLACK);

            label = new Text(value);
            label.setX(x - 5);
            label.setY(y + 5);

            canvasPane.getChildren().addAll(circle, label);
            enableDrag();


            adjacencyList.put(this, new ArrayList<>());
        }

        void enableDrag() {

            circle.setOnMousePressed(e -> {
                offsetX = circle.getCenterX() - e.getX();
                offsetY = circle.getCenterY() - e.getY();
            });

            circle.setOnMouseDragged(e -> {
                circle.setCenterX(e.getX() + offsetX);
                circle.setCenterY(e.getY() + offsetY);
                label.setX(circle.getCenterX() - 5);
                label.setY(circle.getCenterY() + 5);
                updateConnectedEdges();
            });

            circle.setOnMouseClicked(e -> {
                handleNodeClick(this);
            });
        }

        void updateConnectedEdges() {
            for (GraphEdge edge : edges) {
                if (edge.from == this || edge.to == this) {
                    edge.update();
                }
            }
        }
    }

    // ===============================
    // EDGE CLASS
    // ===============================
    class GraphEdge {
        GraphNode from;
        GraphNode to;

        Line line;
        Polygon arrowHead;
        Text weightText;

        GraphEdge(GraphNode from, GraphNode to, int weight) {
            this.from = from;
            this.to = to;

            line = new Line();
            line.setStrokeWidth(2);

            arrowHead = new Polygon();
            arrowHead.setFill(Color.BLACK);

            weightText = new Text(String.valueOf(weight));

            canvasPane.getChildren().add(line);

            if (directedCheck.isSelected()) {
                canvasPane.getChildren().add(arrowHead);
            }

            if (weightedCheck.isSelected()) {
                canvasPane.getChildren().add(weightText);
            }

            update();

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

            if (distance == 0) return;

            double radius = from.circle.getRadius();

            double startX = sx + (dx / distance) * radius;
            double startY = sy + (dy / distance) * radius;

            double endX = ex - (dx / distance) * radius;
            double endY = ey - (dy / distance) * radius;

            line.setStartX(startX);
            line.setStartY(startY);
            line.setEndX(endX);
            line.setEndY(endY);

            // weight position
            weightText.setX((startX + endX) / 2);
            weightText.setY((startY + endY) / 2);

            if (directedCheck.isSelected()) {
                updateArrow(endX, endY, dx, dy, distance);
            }
        }

        void updateArrow(double endX, double endY, double dx, double dy, double distance) {

            double angle = Math.atan2(dy, dx);

            double arrowLength = 15;
            double arrowWidth = 7;

            arrowHead.getPoints().clear();

            arrowHead.getPoints().addAll(
                    endX, endY,
                    endX - arrowLength * Math.cos(angle - Math.PI / 6),
                    endY - arrowLength * Math.sin(angle - Math.PI / 6),
                    endX - arrowLength * Math.cos(angle + Math.PI / 6),
                    endY - arrowLength * Math.sin(angle + Math.PI / 6)
            );
        }
    }

    // ===============================
    // CREATE NODE
    // ===============================
    private void createNode(double x, double y) {

        String value;

        if (customNodeCheck.isSelected() && !nodeValueField.getText().isEmpty()) {
            value = nodeValueField.getText();
        } else {
            value = String.valueOf(nodeCounter++);
        }

        GraphNode node = new GraphNode(x, y, value);
        nodes.add(node);

        rebuildAdjacencyMatrix();
    }

    // ===============================
    // NODE CLICK
    // ===============================
    private void handleNodeClick(GraphNode node) {

        if (edgeTool.isSelected()) {

            // Always mark as selected
            selectNode(node);

            if (firstEdgeNode == null) {
                firstEdgeNode = node;
            } else {
                if (firstEdgeNode != node) {
                    createEdge(firstEdgeNode, node);
                }
                firstEdgeNode.circle.setStroke(Color.BLACK);
                firstEdgeNode = null;
            }

        } else {
            selectNode(node);
        }
    }

    private void createEdge(GraphNode from, GraphNode to) {

        int weight = 1;
        try {
            weight = Integer.parseInt(weightField.getText());
        } catch (Exception ignored) {}

        GraphEdge edge = new GraphEdge(from, to, weight);
        edges.add(edge);

        adjacencyList.get(from).add(to);
        if (!directedCheck.isSelected()) {
            adjacencyList.get(to).add(from);
        }

        rebuildAdjacencyMatrix();
    }

    // ===============================
    // SELECTION
    // ===============================
    private void selectNode(GraphNode node) {

        // Reset previous node
        if (selectedNode != null) {
            selectedNode.circle.setStroke(Color.BLACK);
        }

        // Reset previous edge
        if (selectedEdge != null) {
            selectedEdge.line.setStroke(Color.BLACK);
        }

        selectedNode = node;
        selectedEdge = null;

        node.circle.setStroke(Color.RED);
    }

    private void selectEdge(GraphEdge edge) {

        if (selectedEdge != null) {
            selectedEdge.line.setStroke(Color.BLACK);
        }

        if (selectedNode != null) {
            selectedNode.circle.setStroke(Color.BLACK);
        }

        selectedEdge = edge;
        selectedNode = null;

        edge.line.setStroke(Color.RED);
    }

    // ===============================
    // DELETE
    // ===============================
    @FXML
    private void deleteSelected() {

        // ============================
        // DELETE NODE
        // ============================
        if (selectedNode != null) {

            // Copy edges to avoid concurrent modification
            List<GraphEdge> edgesCopy = new ArrayList<>(edges);

            for (GraphEdge edge : edgesCopy) {
                if (edge.from == selectedNode || edge.to == selectedNode) {
                    removeEdge(edge);
                }
            }

            // Remove node visuals
            canvasPane.getChildren().removeAll(
                    selectedNode.circle,
                    selectedNode.label
            );

            // Remove from adjacency list
            adjacencyList.remove(selectedNode);

            // Remove node from all other adjacency entries
            for (List<GraphNode> neighbors : adjacencyList.values()) {
                neighbors.remove(selectedNode);
            }

            nodes.remove(selectedNode);
            selectedNode = null;
        }

        // ============================
        // DELETE EDGE
        // ============================
        if (selectedEdge != null) {
            removeEdge(selectedEdge);
            selectedEdge = null;
        }

        rebuildAdjacencyMatrix();
    }

    private void removeEdge(GraphEdge edge) {

        canvasPane.getChildren().removeAll(
                edge.line,
                edge.arrowHead,
                edge.weightText
        );

        edges.remove(edge);

        // remove from adjacency list
        adjacencyList.get(edge.from).remove(edge.to);

        if (!directedCheck.isSelected()) {
            adjacencyList.get(edge.to).remove(edge.from);
        }
    }

    private void rebuildAdjacencyMatrix() {

        int n = nodes.size();
        adjacencyMatrix = new int[n][n];

        for (GraphEdge edge : edges) {

            int i = nodes.indexOf(edge.from);
            int j = nodes.indexOf(edge.to);

            int weight = 1;
            try {
                weight = Integer.parseInt(edge.weightText.getText());
            } catch (Exception ignored) {}

            adjacencyMatrix[i][j] = weight;

            if (!directedCheck.isSelected()) {
                adjacencyMatrix[j][i] = weight;
            }
        }
    }
}