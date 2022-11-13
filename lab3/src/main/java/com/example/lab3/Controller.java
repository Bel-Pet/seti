package com.example.lab3;

import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.concurrent.ExecutionException;

public class Controller {
    private final Positions positions = new Positions();
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
        click.setOnAction(event -> {
            String cur = userPlace.getText().trim();
            listPositions.getItems().clear();
            if (cur.isEmpty()) {
                listPositions.getItems().add("Not found");
                return;
            }
            try {
                positions.put(positions.findLocations(cur).get());
                listPositions.getItems().addAll(positions.get().keySet());
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
                listPositions.getItems().add("Not found");
            }
        });
    }

    @FXML
    private void display() {
        String cur = listPositions.getSelectionModel().getSelectedItem();
        listPlaces.getItems().clear();
        if (cur == null || cur.isEmpty()) {
            listPlaces.getItems().add("Not found");
            weather.setText("Not found");
            description.setText("Not found");
            return;
        }
        try {
            Info info = positions.findInfo(cur).get();
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