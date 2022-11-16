package com.example.lab3;

import javafx.util.Pair;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class Finder {
    private static final String KEY_LOCATIONS = "&locale=en&limit=10&debug=true&key=e7624265-f3d5-4fba-b8ab-b6b8d13c3a22";
    private static final String LOCATIONS = "https://graphhopper.com/api/1/geocode?q=";
    private static final String KEY_WEATHER = "&appid=603aa911651ab3991e931cb1f29d891c";
    private static final String WEATHER = "http://api.openweathermap.org/data/2.5/weather?lat=";
    private static final String KEY_PLACES = "&format=json&limit=100&apikey=5ae2e3f221c38a28845f05b63d8554c9df4a24699e050b60c80591ee";
    private static final String PLACES = "https://api.opentripmap.com/0.1/ru/places/radius?radius=5000&lon=";
    private static final String DESCRIPTION = "https://api.opentripmap.com/0.1/ru/places/xid/";
    private static final String KEY_DESCRIPTION = "?apikey=5ae2e3f221c38a28845f05b63d8554c9df4a24699e050b60c80591ee";
    private static final HttpClient client = HttpClient.newHttpClient();

    private Finder() {}

    public static CompletableFuture<Map<String, Pair<Double, Double>>> findLocations(String key) {
        return client.sendAsync(HttpRequest.newBuilder(URI.create(LOCATIONS + key + KEY_LOCATIONS)).build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(JSONObject::new)
                .thenApply(v -> v.getJSONArray("hits"))
                .thenApply(Finder::getPositions);
    }

    public static CompletableFuture<Info> findInfo(String key, Map<String, Pair<Double, Double>> positions) {
        CompletableFuture<String> weather = client.sendAsync(HttpRequest
                                .newBuilder(URI.create(WEATHER + positions.get(key).getValue().toString() +
                                        "&lon=" + positions.get(key).getKey().toString() + KEY_WEATHER)).build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(JSONObject::new)
                .thenApply(Finder::getWeather);

        CompletableFuture<Map<String, String>> places = client.sendAsync(HttpRequest
                                .newBuilder(URI.create(PLACES + positions.get(key).getKey().toString() +
                                        "&lat=" + positions.get(key).getValue().toString() + KEY_PLACES)).build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(JSONArray::new)
                .thenApply(Finder::getInterestingPlaces);

        CompletableFuture<List<String>> descriptions = places.thenCompose(Finder::findDescriptions);

        return CompletableFuture.allOf(weather, places, descriptions)
                .thenApply(v -> new Info(
                        weather.join(),
                        places.join(),
                        descriptions.join()
                                .stream()
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.joining("\n\n"))));
    }

    private static CompletableFuture<List<String>> findDescriptions(Map<String, String> places) {
        List<CompletableFuture<String>> listDescriptions = new ArrayList<>();
        HttpClient client = HttpClient.newHttpClient();
        places.forEach((key, value) -> listDescriptions.add(client
                .sendAsync(HttpRequest
                        .newBuilder(URI.create(DESCRIPTION + value + KEY_DESCRIPTION))
                        .build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(JSONObject::new)
                .thenApply(Finder::getDescription)));

        return CompletableFuture.allOf(listDescriptions.toArray(new CompletableFuture[0]))
                .thenApply(v -> listDescriptions.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }

    private static String getDescription(JSONObject body) {
        JSONObject info = body.optJSONObject("info");
        return info == null ? "" : body.getString("name") + "\n" + info.getString("descr");
    }

    private static Map<String, String> getInterestingPlaces(JSONArray body) {
        Map<String, String> places = new HashMap<>();
        body.forEach(el -> {
            JSONObject curObj = new JSONObject(el.toString());
            places.put(curObj.getString("name"), curObj.getString("xid"));
        });
        return places;
    }

    private static String getWeather(JSONObject body) {
        return Math.round((body.getJSONObject("main").getDouble("temp") - 273.15) * 100) / 100.0 + "`C";
    }

    private static Map<String, Pair<Double, Double>> getPositions(JSONArray body) {
        Map<String, Pair<Double, Double>> result = new HashMap<>();
        body.forEach(el -> {
            JSONObject curObj = new JSONObject(el.toString());
            result.put(curObj.getString("name") + ", " + curObj.getString("country"),
                    new Pair<>(curObj.getJSONObject("point").getDouble("lng"),
                            curObj.getJSONObject("point").getDouble("lat")));
        });
        return result;
    }
}
