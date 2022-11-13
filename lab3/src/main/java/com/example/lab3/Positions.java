package com.example.lab3;

import javafx.util.Pair;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class Positions {
    private static final String KEY_LOCATIONS = "&locale=en&limit=10&debug=true&key=e7624265-f3d5-4fba-b8ab-b6b8d13c3a22";
    private static final String LOCATIONS = "https://graphhopper.com/api/1/geocode?q=";
    private static final String KEY_WEATHER = "&appid=603aa911651ab3991e931cb1f29d891c";
    private static final String WEATHER = "http://api.openweathermap.org/data/2.5/weather?lat=";
    private static final String KEY_PLACES = "&format=json&limit=100&apikey=5ae2e3f221c38a28845f05b63d8554c9df4a24699e050b60c80591ee";
    private static final String PLACES = "https://api.opentripmap.com/0.1/ru/places/radius?radius=5000&lon=";
    private static final String DESCRIPTION = "https://api.opentripmap.com/0.1/ru/places/xid/";
    private static final String KEY_DESCRIPTION = "?apikey=5ae2e3f221c38a28845f05b63d8554c9df4a24699e050b60c80591ee";
    private final Map<String, Pair<Double, Double>> positions = new HashMap<>();
    private final HttpClient client = HttpClient.newHttpClient();

    public CompletableFuture<Map<String, Pair<Double, Double>>> findLocations(String key) {
        return client.sendAsync(HttpRequest.newBuilder(URI.create(LOCATIONS + key + KEY_LOCATIONS)).build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(JSONObject::new)
                .thenApply(v -> v.getJSONArray("hits")).thenApply(Positions::getPositions);
    }

    public CompletableFuture<Info> findInfo(String key) {
        CompletableFuture<String> weather = client.sendAsync(HttpRequest
                                .newBuilder(URI.create(WEATHER + positions.get(key).getValue().toString() +
                                        "&lon=" + positions.get(key).getKey().toString() + KEY_WEATHER)).build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(Positions::getWeather);

        CompletableFuture<Map<String, String>> places = client.sendAsync(HttpRequest
                                .newBuilder(URI.create(PLACES + positions.get(key).getKey().toString() +
                                "&lat=" + positions.get(key).getValue().toString() + KEY_PLACES)).build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(Positions::getInterestingPlaces);

        return CompletableFuture.allOf(weather, places)
                .thenApply(v -> new Info(weather.join(),
                        places.join(),
                        findDescriptions(places.join()).join()
                                .stream()
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.joining("\n\n"))));
    }

    public void put(Map<String, Pair<Double, Double>> pos) {
        positions.clear();
        positions.putAll(pos);
    }

    public Map<String, Pair<Double, Double>> get() {
        return positions;
    }

    private CompletableFuture<List<String>> findDescriptions(Map<String, String> places) {
        List<CompletableFuture<String>> listDescriptions = new ArrayList<>();
        HttpClient client = HttpClient.newHttpClient();
        places.forEach((key, value) -> listDescriptions.add(client
                .sendAsync(HttpRequest
                        .newBuilder(URI.create(DESCRIPTION + places.get(key) + KEY_DESCRIPTION))
                        .build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(Positions::getDescription)));

        return CompletableFuture.allOf(listDescriptions.toArray(new CompletableFuture[0]))
                .thenApply(v -> listDescriptions.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }

    private static String getDescription(String body) {
        JSONObject obj = new JSONObject(body);
        if (obj.isNull("info")) return "";
        return obj.getString("name") + "\n" + obj.getJSONObject("info").getString("descr");
    }

    private static Map<String, String> getInterestingPlaces(String body) {
        Map<String, String> places = new HashMap<>();
        new JSONArray(body).forEach(el -> {
            JSONObject curObj = new JSONObject(el.toString());
            places.put(curObj.getString("name"), curObj.getString("xid"));
        });
        return places;
    }

    private static String getWeather(String body) {
        return Math.round((new JSONObject(body).getJSONObject("main").getDouble("temp") - 273.15) * 100) / 100.0 + "`C";
    }

    private static Map<String, Pair<Double, Double>> getPositions(JSONArray array) {
        Map<String, Pair<Double, Double>> result = new HashMap<>();
        array.forEach(el -> {
            JSONObject curObj = new JSONObject(el.toString());
            result.put(curObj.getString("name") + ", " + curObj.getString("country"),
                    new Pair<>(curObj.getJSONObject("point").getDouble("lng"),
                    curObj.getJSONObject("point").getDouble("lat")));
        });
        return result;
    }
}
