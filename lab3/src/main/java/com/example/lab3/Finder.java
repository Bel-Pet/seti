package com.example.lab3;

import javafx.util.Pair;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Finder {
    private static final String LOCATIONS_URL = "https://graphhopper.com/api/1/geocode?q=%s&locale=en&limit=10&debug=true&key=e7624265-f3d5-4fba-b8ab-b6b8d13c3a22";
    private static final String WEATHER_URL = "https://api.openweathermap.org/data/2.5/weather?lat=%s&lon=%s&appid=603aa911651ab3991e931cb1f29d891c";
    private static final String PLACES_URL = "https://api.opentripmap.com/0.1/ru/places/radius?radius=5000&lon=%s&lat=%s&format=json&limit=100&apikey=5ae2e3f221c38a28845f05b63d8554c9df4a24699e050b60c80591ee";
    private static final String DESCRIPTION_URL = "https://api.opentripmap.com/0.1/ru/places/xid/%s?apikey=5ae2e3f221c38a28845f05b63d8554c9df4a24699e050b60c80591ee";
    private final HttpClient client = HttpClient.newHttpClient();
    private final Map<String, Pair<Double, Double>> locationMap = new HashMap<>();

    public Finder() {}

    public CompletableFuture<List<String>> findLocations(String substring) {
        if (substring == null || substring.isEmpty()) {
            return CompletableFuture.completedFuture(List.of(""));
        }
        var request = HttpRequest.newBuilder().uri(URI.create(LOCATIONS_URL.formatted(substring))).build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(JSONObject::new)
                .thenApply(it -> it.getJSONArray("hits"))
                .thenAccept(this::fillLocations)
                .thenApply(__ -> locationMap.keySet())
                .thenApply(Set::stream)
                .thenApply(Stream::toList);
    }

    public CompletableFuture<Pair<String, String>> findLocationInfo(String name) {
        if (name == null || name.isEmpty()) {
            return CompletableFuture.completedFuture(new Pair<>("", ""));
        }
        var weather = findWeather(name);
        var placesWithDescriptions = findPlacesWithDescriptions(name);
        return CompletableFuture.allOf(weather, placesWithDescriptions)
                .thenApply(__ -> new Pair<>(weather.join(), placesWithDescriptions.join()));
    }

    private void fillLocations(JSONArray jsonArray) {
        locationMap.clear();
        jsonArray.forEach(it -> {
            var jsonObject = new JSONObject(it.toString());
            var name = jsonObject.getString("name") + ", " + jsonObject.getString("country");
            var position = new Pair<>(jsonObject.getJSONObject("point").getDouble("lng"),
                    jsonObject.getJSONObject("point").getDouble("lat"));
            locationMap.put(name, position);
        });
    }

    private CompletableFuture<String> findWeather(String key) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(WEATHER_URL.formatted(locationMap.get(key).getValue(), locationMap.get(key).getKey())))
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(JSONObject::new)
                .thenApply(it -> it.getJSONObject("main"))
                .thenApply(it -> it.getDouble("temp"))
                .thenApply(it -> (it - 273.15) * 100)
                .thenApply(Math::round)
                .thenApply(it -> it / 100.0)
                .thenApply(it -> it + "`C");
    }

    private CompletableFuture<String> findPlacesWithDescriptions(String key) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(PLACES_URL.formatted(locationMap.get(key).getKey(), locationMap.get(key).getValue())))
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(JSONArray::new)
                .thenApply(this::mapPlace)
                .thenCompose(this::findDescriptions);
    }

    private Map<String, String> mapPlace(JSONArray jsonArray) {
        var places = new HashMap<String, String>();
        jsonArray.forEach(obj -> {
            JSONObject jsonObject = new JSONObject(obj.toString());
            places.put(jsonObject.getString("name"), jsonObject.getString("xid"));
        });
        return places;
    }

    private CompletableFuture<String> findDescriptions(Map<String, String> places) {
        var descriptions = places.entrySet().stream()
                .map(this::findDescription)
                .toList();
        return CompletableFuture.allOf(descriptions.toArray(new CompletableFuture[0]))
                .thenApply(__ -> descriptions.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.joining("\n\n")));
    }

    private CompletableFuture<String> findDescription(Map.Entry<String, String> place) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(DESCRIPTION_URL.formatted(place.getValue())))
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(JSONObject::new)
                .thenApply(it -> it.optJSONObject("info"))
                .thenApply(it -> it == null ?
                        place.getKey() : it.getString("name") + "\n" + it.getString("descr")
                );
    }
}
