package com.example.lab3;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;

import java.util.concurrent.atomic.AtomicBoolean;

public class Controller {
    @FXML
    private ListView<String> listPositions = new ListView<>();
    @FXML
    private TextField userPlace;
    @FXML
    private Button searchButton;
    @FXML
    private TextField weatherField;
    @FXML
    private TextArea descriptionArea;

    private final Finder finder = new Finder();

    private final AtomicBoolean searchCompleted = new AtomicBoolean(true);

    @FXML
    private void initialize() {
        searchButton.setDefaultButton(true);
        searchButton.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseEvent -> {
            if (!searchCompleted.get()) {
                return;
            }
            searchCompleted.set(false);
            finder.findLocations(userPlace.getText().trim()).thenAccept(it -> {
                listPositions.getItems().clear();
                listPositions.getItems().addAll(it);
            }).join();
            searchCompleted.set(true);
        });
    }

    @FXML
    private void display() {
        if (!searchCompleted.get()) {
            return;
        }
        searchCompleted.set(false);
        finder.findLocationInfo(listPositions.getSelectionModel().getSelectedItem()).thenAccept(it -> {
            weatherField.setText(it.getKey());
            descriptionArea.setText(it.getValue());
        }).join();
        searchCompleted.set(true);
    }
}