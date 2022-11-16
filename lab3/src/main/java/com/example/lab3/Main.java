package com.example.lab3;

import javafx.util.Pair;

import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

public class Main {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        System.out.println("Input place:");
        Scanner in = new Scanner(System.in);
        String userPlace = in.nextLine();

        Map<String, Pair<Double, Double>> positions = Finder.findLocations(userPlace).get();

        System.out.println("Select and enter place:");
        positions.keySet().forEach(s -> {
            System.out.println(s + "\n");
        });

        var userIndex = in.nextLine();

        var placeInfo = Finder.findInfo(userIndex, positions).get();

        System.out.println("Weather: " + placeInfo.weather());
        System.out.println("Descriptions:\n" + placeInfo.descriptions());
    }
}
