package com.example.lab3;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class InterestingPlacesApp extends Application {
    private static final String FILE_NAME = "interesting-places-view.fxml";
    private static final String TITLE = "Interesting Places";

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(InterestingPlacesApp.class.getResource(FILE_NAME));
        Scene scene = new Scene(fxmlLoader.load(), 800, 800);
        stage.setTitle(TITLE);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}