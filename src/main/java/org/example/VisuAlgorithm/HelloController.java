package org.example.VisuAlgorithm;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloController {
    @FXML private Button themeToggleButton;
    private boolean isDarkMode = true;

    @FXML
    protected void onThemeToggle() {
        Scene scene = themeToggleButton.getScene();
        if (isDarkMode) {
            scene.getStylesheets().remove(getClass().getResource("/org/example/VisuAlgorithm/styles/main.css").toExternalForm());
            themeToggleButton.setText("🌙 Dark Mode");
        } else {
            scene.getStylesheets().add(getClass().getResource("/org/example/VisuAlgorithm/styles/main.css").toExternalForm());
            themeToggleButton.setText("☀️ Light Mode");
        }
        isDarkMode = !isDarkMode;
    }
    @FXML
    void gotoSorting(MouseEvent click) throws IOException {
        switchScene("/org/example/VisuAlgorithm/sorting-view.fxml", click);
    }
    @FXML
    void gotoArray(MouseEvent click) throws IOException {
        switchScene("/org/example/VisuAlgorithm/array-view.fxml", click);
    }
    @FXML
    void gotoLinkedList(MouseEvent click) throws IOException {
        switchScene("/org/example/VisuAlgorithm/linked-list-view.fxml", click);
    }
    @FXML
    void gotoStack(MouseEvent click) throws IOException {
        switchScene("/org/example/VisuAlgorithm/stack-view.fxml", click);
    }
    @FXML
    void gotoQueue(MouseEvent click) throws IOException {
        switchScene("/org/example/VisuAlgorithm/queue-view.fxml", click);
    }
    @FXML
    void gotoGraph(MouseEvent click) throws IOException {
        switchScene("/org/example/VisuAlgorithm/graph-view.fxml", click);
    }
    @FXML
    void gotoBST(MouseEvent click) throws IOException {
        switchScene("/org/example/VisuAlgorithm/bst-view.fxml", click);
    }

    private void switchScene(String fxml_name, MouseEvent click) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource(fxml_name));
        Parent root = fxmlLoader.load();
        Stage stage = (Stage)((Node)click.getSource()).getScene().getWindow();
        Scene scene = new Scene(root);

        // -- This is the line that matters! --
        scene.getStylesheets().add(getClass().getResource("/org/example/VisuAlgorithm/styles/main.css").toExternalForm());

        stage.setScene(scene);
    }
}