package com.example.lab3;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Finder finder = new Finder();
        Scanner in = new Scanner(System.in);
        System.out.println("Input place:");
        finder.findLocations(in.nextLine()).thenAccept(locations -> {
            System.out.println("Select and enter location:");
            locations.forEach(s -> System.out.println(s + "\n"));
            finder.findLocationInfo(in.nextLine()).thenAccept(info -> {
                System.out.println("Weather: " + info.getKey());
                System.out.println("Descriptions:\n" + info.getValue());
            }).join();
        }).join();
    }
}
