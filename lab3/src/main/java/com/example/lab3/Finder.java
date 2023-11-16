package com.example.lab3;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class Finder {
    private static final String LOCATIONS_URL = "https://graphhopper.com/api/1/geocode?q=%s&locale=en&limit=10&debug=true&key=e7624265-f3d5-4fba-b8ab-b6b8d13c3a22";
    private static final String WEATHER_URL = "https://api.openweathermap.org/data/2.5/weather?lat=%s&lon=%s&appid=603aa911651ab3991e931cb1f29d891c";
    private static final String PLACES_URL = "https://api.opentripmap.com/0.1/ru/places/radius?radius=5000&lon=%s&lat=%s&format=json&limit=100&apikey=5ae2e3f221c38a28845f05b63d8554c9df4a24699e050b60c80591ee";
    private static final String DESCRIPTION_URL = "https://api.opentripmap.com/0.1/ru/places/xid/%s?apikey=5ae2e3f221c38a28845f05b63d8554c9df4a24699e050b60c80591ee";
    private final HttpClient client = HttpClient.newHttpClient();
    private final Map<String, Position> locations = new HashMap<>();

    public Finder() {}

    public Set<String> getLocations(String key) throws IOException, InterruptedException {
        locations.clear();
        locations.putAll(client.send(
                HttpRequest.newBuilder(URI.create(String.format(LOCATIONS_URL, key))).build(),
                HttpResponse.BodyHandlers.ofString()
        ).body().transform(this::mapToLocations));
        return locations.keySet();
    }

    public CompletableFuture<Info> findInfo(String key) {
        var weather = findWeather(key);
        var descriptions = findPlaces(key).thenCompose(this::findDescriptions);
        return CompletableFuture.allOf(weather, descriptions)
                .thenApply(__ -> new Info(
                        weather.join(),
                        descriptions.join()
                ));
    }

    private CompletableFuture<String> findWeather(String key) {
        return client.sendAsync(
                HttpRequest.newBuilder(
                        URI.create(String.format(WEATHER_URL, locations.get(key).y(), locations.get(key).x()))
                ).build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(JSONObject::new)
                .thenApply(this::mapToWeather);
    }

    private CompletableFuture<Map<String, String>> findPlaces(String key) {
        return client.sendAsync(
                HttpRequest.newBuilder(
                        URI.create(String.format(PLACES_URL, locations.get(key).x(), locations.get(key).y()))
                ).build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(JSONArray::new)
                .thenApply(this::mapToPlaces);
    }

    private CompletableFuture<String> findDescriptions(Map<String, String> places) {
        var listDescriptions = places.entrySet().stream()
                .map(this::findDescription)
                .toList();
        return CompletableFuture.allOf(listDescriptions.toArray(new CompletableFuture[0]))
                .thenApply(__ -> listDescriptions.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.joining("\n\n")));
    }

    private CompletableFuture<String> findDescription(Map.Entry<String, String> place) {
        return client.sendAsync(
                        HttpRequest.newBuilder(URI.create(String.format(DESCRIPTION_URL, place.getValue()))).build(),
                        HttpResponse.BodyHandlers.ofString()
                )
                .thenApply(HttpResponse::body)
                .thenApply(JSONObject::new)
                .thenApply(this::mapToDescription)
                .thenApply(it -> it.isEmpty() ? place.getKey() : it);
    }

    private String mapToDescription(JSONObject body) {
        var descr = body.optJSONObject("info");
        return descr == null ? "" : String.join("\n", body.getString("name"), descr.getString("descr"));
    }

    private Map<String, String> mapToPlaces(JSONArray body) {
        var places = new HashMap<String, String>();
        body.forEach(it -> {
            JSONObject jsonObject = new JSONObject(it.toString());
            places.put(jsonObject.getString("name"), jsonObject.getString("xid"));
        });
        return places;
    }

    private String mapToWeather(JSONObject body) {
        return Math.round((body.getJSONObject("main").getDouble("temp") - 273.15) * 100) / 100.0 + "`C";
    }

    private Map<String, Position> mapToLocations(String str) {
        var jsonArray = new JSONObject(str).getJSONArray("hits");
        var locations = new HashMap<String, Position>();
        jsonArray.forEach(it -> {
            var jsonObject = new JSONObject(it.toString());
            locations.put(
                    String.join(", ", jsonObject.getString("name"), jsonObject.getString("country")),
                    new Position(
                            jsonObject.getJSONObject("point").getDouble("lng"),
                            jsonObject.getJSONObject("point").getDouble("lat")
                    )
            );
        });
        return locations;
    }

    private record Position(Double x, Double y) {}

}
