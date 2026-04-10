package com.merstats.vex.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merstats.vex.model.SeasonRanking;
import com.merstats.vex.model.SeasonRankingResponse;
import com.merstats.vex.model.SkillsRanking;
import com.merstats.vex.model.SkillsResponse;
import com.merstats.vex.model.TeamResponse;
import com.merstats.vex.model.VexTeam;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RobotEventsService {

    private static final String API_KEY = "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIzIiwianRpIjoiNTE0MjRkZDAyMDQ1MzA1MGVlZGYzZjM2OGY4ODQ3NDFhZGMwOTZjMjExMTA1OWRiYWE0MDFiMDJjY2VhZGE1ZGE0OTc4ZmY1ZDhlYjNkNzciLCJpYXQiOjE3NzUxNDUzMzAuNTI1OTI5LCJuYmYiOjE3NzUxNDUzMzAuNTI1OTMwOSwiZXhwIjoyNzIxOTE2NTMwLjUyMDU3NzksInN1YiI6IjEyNjM3MyIsInNjb3BlcyI6W119.hZuGrp7w4nO6m8NOgm3iKsFZ5l85PV8W5yhP9mgnpgXuJCL71Tg9IWR4xTIdD1uVVEKAZGPhuwRybTGHO2jp171zVZFU-U4K7w0m_wj1xzqu8-yIHW6uzhxypMAF6Nixc__vT0gKcEuLZE_WECxRjd3PQTtZ7uOT8bu81bXpCWSSd-GtItSaGpZ10CeQ42VV0aFzNm2uMePINW2N-gMJxjbrKEIqcloNw8R8K_xdrpL8O8VwQJoQFPMmq24fLSmcsWX8l4aoDqwHWsKx19JNj80h6lwge5WdGcWxmYU1b1crESb8Od69GV78QwnM0QqgIu7KRbSqiBeOVE4GyxxIVflnQPoiRSRKz1k5I1dLEzoBwavG-LX2QZjtZZTL5gFLOc_rqJLb6Y4rANZvJBpRfFUJ0MNRn0Ert7Up5ahDZqkU3_67_CQAomZITP8MVK7O__btFScefR4m9mffete2ad2MSbY3PiN9sFFp3dFhYGnC9MJKCvYn-6jzll-4oJpzj41QvKLe8Dwpkqz3DxRV8v9dgQcgifcEUi8yix1V8_YtX-VlPiH3TKdDm6gMMQewxK-25KiajV7wSm2ZLfj_KW2CLc5qyr09egDWdhAJhn_hrkKqlaCjP6CFM998HIIkwPzW81bh3SmqRgzvobg7fgpRrA2CuvU96ZIWDplS8Mg"; // Ensure your live key is here!
    private static final String BASE_URL = "https://www.robotevents.com/api/v2";

    private final HttpClient client;
    private final ObjectMapper mapper;

    public RobotEventsService() {
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    public VexTeam getTeamByNumber(String teamNumber) throws Exception {
        String url = BASE_URL + "/teams?number%5B%5D=" + teamNumber;
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("Authorization", API_KEY).header("Accept", "application/json").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            TeamResponse teamResponse = mapper.readValue(response.body(), TeamResponse.class);
            List<VexTeam> teams = teamResponse.getData();
            if (teams != null && !teams.isEmpty()) return teams.get(0);
        }
        return null;
    }

    public List<SkillsRanking> getSkillsByTeamId(int teamId) throws Exception {
        String url = BASE_URL + "/teams/" + teamId + "/skills?per_page=250";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("Authorization", API_KEY).header("Accept", "application/json").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            SkillsResponse skillsResponse = mapper.readValue(response.body(), SkillsResponse.class);
            return skillsResponse.getData();
        }
        return null;
    }

    public List<SeasonRanking> getEventRankingsBySku(String sku) throws Exception {

        String eventUrl = BASE_URL + "/events?sku%5B%5D=" + sku;
        HttpRequest eventRequest = HttpRequest.newBuilder().uri(URI.create(eventUrl)).header("Authorization", API_KEY).header("Accept", "application/json").GET().build();
        HttpResponse<String> eventResponse = client.send(eventRequest, HttpResponse.BodyHandlers.ofString());

        if (eventResponse.statusCode() != 200) {
            System.err.println("Event Search API Error: Received HTTP " + eventResponse.statusCode());
            return null;
        }

        JsonNode rootNode = mapper.readTree(eventResponse.body());
        JsonNode dataArray = rootNode.path("data");

        if (!dataArray.isArray() || dataArray.isEmpty()) {
            System.err.println("Error: Could not find any event matching SKU: " + sku);
            return null;
        }

        JsonNode eventNode = dataArray.get(0);
        int hiddenEventId = eventNode.path("id").asInt();
        JsonNode divisionsArray = eventNode.path("divisions");

        // 1. Extract all division IDs into a list
        List<Integer> divisionIds = new ArrayList<>();
        if (divisionsArray.isArray()) {
            for (JsonNode divisionNode : divisionsArray) {
                divisionIds.add(divisionNode.path("id").asInt());
            }
        }

        // 2. Create a Thread-Safe list to hold the incoming data from multiple threads
        List<SeasonRanking> masterRankingsList = Collections.synchronizedList(new ArrayList<>());

        // 3. The Multi-Threading Engine: Fetch all divisions simultaneously!
        divisionIds.parallelStream().forEach(divId -> {
            try {
                String rankUrl = BASE_URL + "/events/" + hiddenEventId + "/divisions/" + divId + "/rankings?per_page=250";
                HttpRequest rankRequest = HttpRequest.newBuilder()
                        .uri(URI.create(rankUrl))
                        .header("Authorization", API_KEY)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> rankResponse = client.send(rankRequest, HttpResponse.BodyHandlers.ofString());

                if (rankResponse.statusCode() == 200) {
                    SeasonRankingResponse rankingResponse = mapper.readValue(rankResponse.body(), SeasonRankingResponse.class);
                    if (rankingResponse.getData() != null) {
                        masterRankingsList.addAll(rankingResponse.getData());
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to fetch division " + divId);
                e.printStackTrace();
            }
        });

        return masterRankingsList;
    }
}