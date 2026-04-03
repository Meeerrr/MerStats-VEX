package com.merstats.vex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merstats.vex.model.TeamResponse;
import com.merstats.vex.model.VexTeam;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class RobotEventsService {

    //API key
    private static final String API_KEY = "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIzIiwianRpIjoiNTE0MjRkZDAyMDQ1MzA1MGVlZGYzZjM2OGY4ODQ3NDFhZGMwOTZjMjExMTA1OWRiYWE0MDFiMDJjY2VhZGE1ZGE0OTc4ZmY1ZDhlYjNkNzciLCJpYXQiOjE3NzUxNDUzMzAuNTI1OTI5LCJuYmYiOjE3NzUxNDUzMzAuNTI1OTMwOSwiZXhwIjoyNzIxOTE2NTMwLjUyMDU3NzksInN1YiI6IjEyNjM3MyIsInNjb3BlcyI6W119.hZuGrp7w4nO6m8NOgm3iKsFZ5l85PV8W5yhP9mgnpgXuJCL71Tg9IWR4xTIdD1uVVEKAZGPhuwRybTGHO2jp171zVZFU-U4K7w0m_wj1xzqu8-yIHW6uzhxypMAF6Nixc__vT0gKcEuLZE_WECxRjd3PQTtZ7uOT8bu81bXpCWSSd-GtItSaGpZ10CeQ42VV0aFzNm2uMePINW2N-gMJxjbrKEIqcloNw8R8K_xdrpL8O8VwQJoQFPMmq24fLSmcsWX8l4aoDqwHWsKx19JNj80h6lwge5WdGcWxmYU1b1crESb8Od69GV78QwnM0QqgIu7KRbSqiBeOVE4GyxxIVflnQPoiRSRKz1k5I1dLEzoBwavG-LX2QZjtZZTL5gFLOc_rqJLb6Y4rANZvJBpRfFUJ0MNRn0Ert7Up5ahDZqkU3_67_CQAomZITP8MVK7O__btFScefR4m9mffete2ad2MSbY3PiN9sFFp3dFhYGnC9MJKCvYn-6jzll-4oJpzj41QvKLe8Dwpkqz3DxRV8v9dgQcgifcEUi8yix1V8_YtX-VlPiH3TKdDm6gMMQewxK-25KiajV7wSm2ZLfj_KW2CLc5qyr09egDWdhAJhn_hrkKqlaCjP6CFM998HIIkwPzW81bh3SmqRgzvobg7fgpRrA2CuvU96ZIWDplS8Mg";
    private static final String BASE_URL = "https://www.robotevents.com/api/v2";

    //Declare the client and mapper
    private final HttpClient client;
    private final ObjectMapper mapper;

    //construct
    public RobotEventsService() {
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    /**
     * Searches the API for a team by their number.
     * @param teamNumber The team number (e.g., "254A")
     * @return A VexTeam object, or null if the team is not found.
     * @throws Exception if the network fails or JSON parsing breaks.
     */
    public VexTeam getTeamByNumber(String teamNumber) throws Exception {

        // Construct the dynamic URL
        String url = BASE_URL + "/teams?number%5B%5D=" + teamNumber;

        // Build the request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", API_KEY)
                .header("Accept", "application/json")
                .GET()
                .build();

        // Send the request
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Check if the API request was successful
        if (response.statusCode() == 200) {

            // Translate the raw JSON string into our TeamResponse wrapper class
            TeamResponse teamResponse = mapper.readValue(response.body(), TeamResponse.class);
            List<VexTeam> teams = teamResponse.getData();

            // The API might return multiple teams with the same number
            // Although we only fetch the first one
            if (teams != null && !teams.isEmpty()) {
                return teams.get(0);
            }
        } else {
            // Print the error code if the server rejects us
            System.err.println("API Error: Received HTTP " + response.statusCode());
        }

        // Return null if the team was not found or if there was an error
        return null;
    }

    // Temporary main method
    public static void main(String[] args) {
        RobotEventsService service = new RobotEventsService();
        System.out.println("Starting dynamic network test...");

        try {
            // We pass the string "254A" into our new method
            VexTeam fetchedTeam = service.getTeamByNumber("254A");

            // Check if the method successfully returned an object
            if (fetchedTeam != null) {
                System.out.println("Success! Translated into Java Object.");
                System.out.println("Team Name: " + fetchedTeam.getTeam_name());
                System.out.println("Internal ID: " + fetchedTeam.getId());
            } else {
                System.out.println("Team not found.");
            }

        } catch (Exception e) {
            System.err.println("A network or parsing exception occurred!");
            e.printStackTrace();
        }
    }
}