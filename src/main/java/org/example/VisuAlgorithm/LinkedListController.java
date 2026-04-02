package org.example.VisuAlgorithm;

import java.util.Random;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

public class LinkedListController {

    @FXML
    private Label statusLabel;

    @FXML
    public void initialize() {
        if (statusLabel != null) {
            statusLabel.setText("Choose a linked list type");
        }
    }

    @FXML
    private void onBack() {
        openView("hello-view.fxml");
    }

    @FXML
    private void gotoSinglyLinkedList(MouseEvent event) {
        openView("singly-linked-list-view.fxml");
    }

    @FXML
    private void gotoDoublyLinkedList(MouseEvent event) {
        openView("doubly-linked-list-view.fxml");
    }

    @FXML
    private void gotoCircularLinkedList(MouseEvent event) {
        openView("circular-linked-list-view.fxml");
    }

    private void openView(String fxmlName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlName));
            Parent root = loader.load();

            Stage stage = getCurrentStage();

            if (stage != null) {
                Scene scene = new Scene(root);
                scene.getStylesheets().add(getClass().getResource("/org/example/VisuAlgorithm/styles/main.css").toExternalForm()); // <--- ADD THIS!
                stage.setScene(scene);
                stage.show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (statusLabel != null) {
                statusLabel.setText("Failed to open: " + fxmlName);
            }
        }
    }

    private Stage getCurrentStage() {
        if (statusLabel == null || statusLabel.getScene() == null) {
            return null;
        }
        return (Stage) statusLabel.getScene().getWindow();
    }
}