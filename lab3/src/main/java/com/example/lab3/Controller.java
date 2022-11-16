package com.example.lab3;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.util.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class Controller {
    Map<String, Pair<Double, Double>> positions = new HashMap<>();
    @FXML
    private TextArea description;
    @FXML
    private ListView<String> listPlaces = new ListView<>();
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
            String place = userPlace.getText().trim();
            listPositions.getItems().clear();
            if (place.isEmpty()) {
                listPositions.getItems().add("Not found");
                return;
            }
            try {
                positions.clear();
                positions.putAll(Finder.findLocations(place).get());
                listPositions.getItems().addAll(positions.keySet());
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
                listPositions.getItems().add("Not found");
            }
        });
    }

    @FXML
    private void display() {
        String place = listPositions.getSelectionModel().getSelectedItem();
        listPlaces.getItems().clear();
        if (place == null || place.isEmpty()) {
            listPlaces.getItems().add("Not found");
            weather.setText("Not found");
            description.setText("Not found");
            return;
        }
        try {
            Info info = Finder.findInfo(place, positions).get();
            weather.setText(info.weather());
            listPlaces.getItems().addAll(info.places().keySet());
            description.setText(info.descriptions());

        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            listPlaces.getItems().add("Not found");
            weather.setText("Not found");
            description.setText("Not found");
        }
    }
}