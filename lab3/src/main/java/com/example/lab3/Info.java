package com.example.lab3;


import java.util.Map;

public record Info(String weather, Map<String, String> places, String descriptions) {
}
