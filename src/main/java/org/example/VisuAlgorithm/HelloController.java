package org.example.VisuAlgorithm;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;

import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloController {
    @FXML
    private Label welcomeText;

    @FXML
    void gotoSorting(MouseEvent click) throws IOException{
        System.out.println("sort_window");
        switchScene("org.example.VisuAlgorithm/sorting-view.fxml", click);
    }
    @FXML
    void gotoArray(MouseEvent click){
        System.out.println("array_window");
    }
    @FXML
    void gotoLinkedList(MouseEvent click){
        System.out.println("LL_window");
    }
    @FXML
    void gotoStack(MouseEvent click){
        System.out.println("stack_window");
    }
    @FXML
    void gotoQueue(MouseEvent click){
        System.out.println("queue_window");
    }
    @FXML
    void gotoGraph(MouseEvent click){
        System.out.println("graph_window");
    }


    private void switchScene(String fxml_name, MouseEvent click) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource(fxml_name));
        Parent root = fxmlLoader.load();
        Stage stage = (Stage) ((Node)click.getSource()).getScene().getWindow();
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
    }
}