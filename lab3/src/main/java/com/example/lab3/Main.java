package com.example.lab3;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

public class Main {
    public static void main(String[] args) throws ExecutionException, InterruptedException, IOException {
        Finder finder = new Finder();
        Scanner in = new Scanner(System.in);

        System.out.println("Input place:");
        var locations = finder.getLocations(in.nextLine());

        System.out.println("Select and enter location:");
        locations.forEach(s -> System.out.println(s + "\n"));

        var info = finder.findInfo(in.nextLine()).get();

        System.out.println("Weather: " + info.weather());
        System.out.println("Descriptions:\n" + info.descriptions());
    }
}
