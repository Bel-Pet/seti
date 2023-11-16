package com.example.lab3;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class Controller {
    private static final String NOT_FOUND = "Not found";
    private final Finder finder = new Finder();
    @FXML
    private TextArea description;
    @FXML
    private ListView<String> listPositions = new ListView<>();
    @FXML
    private TextField userPlace;
    @FXML
    private Button click;
    @FXML
    private TextField weather;

    @FXML
    private void initialize() {
        click.setDefaultButton(true);
        click.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseEvent -> {
            var place = userPlace.getText().trim();
            listPositions.getItems().clear();
            if (place.isEmpty()) {
                listPositions.getItems().add(NOT_FOUND);
                return;
            }
            try {
                listPositions.getItems().addAll(finder.getLocations(place));
            } catch (InterruptedException | IOException e) {
                listPositions.getItems().add(NOT_FOUND);
            }
        });
    }

    @FXML
    private void display() {
        var place = listPositions.getSelectionModel().getSelectedItem();
        if (place == null || place.isEmpty()) {
            weather.setText(NOT_FOUND);
            description.setText(NOT_FOUND);
            return;
        }
        try {
            var info = finder.findInfo(place).get();
            weather.setText(info.weather());
            description.setText(info.descriptions());
        } catch (ExecutionException | InterruptedException e) {
            weather.setText(NOT_FOUND);
            description.setText(NOT_FOUND);
        }
    }
}