package com.merstats.vex.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merstats.vex.model.SeasonRanking;
import com.merstats.vex.model.SkillsRanking;
import com.merstats.vex.model.SkillsResponse;
import com.merstats.vex.model.TeamResponse;
import com.merstats.vex.model.VexTeam;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class RobotEventsService {

    // --- ROBOT EVENTS CONFIGURATION ---
    private static final String RE_API_KEY = "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIzIiwianRpIjoiNTE0MjRkZDAyMDQ1MzA1MGVlZGYzZjM2OGY4ODQ3NDFhZGMwOTZjMjExMTA1OWRiYWE0MDFiMDJjY2VhZGE1ZGE0OTc4ZmY1ZDhlYjNkNzciLCJpYXQiOjE3NzUxNDUzMzAuNTI1OTI5LCJuYmYiOjE3NzUxNDUzMzAuNTI1OTMwOSwiZXhwIjoyNzIxOTE2NTMwLjUyMDU3NzksInN1YiI6IjEyNjM3MyIsInNjb3BlcyI6W119.hZuGrp7w4nO6m8NOgm3iKsFZ5l85PV8W5yhP9mgnpgXuJCL71Tg9IWR4xTIdD1uVVEKAZGPhuwRybTGHO2jp171zVZFU-U4K7w0m_wj1xzqu8-yIHW6uzhxypMAF6Nixc__vT0gKcEuLZE_WECxRjd3PQTtZ7uOT8bu81bXpCWSSd-GtItSaGpZ10CeQ42VV0aFzNm2uMePINW2N-gMJxjbrKEIqcloNw8R8K_xdrpL8O8VwQJoQFPMmq24fLSmcsWX8l4aoDqwHWsKx19JNj80h6lwge5WdGcWxmYU1b1crESb8Od69GV78QwnM0QqgIu7KRbSqiBeOVE4GyxxIVflnQPoiRSRKz1k5I1dLEzoBwavG-LX2QZjtZZTL5gFLOc_rqJLb6Y4rANZvJBpRfFUJ0MNRn0Ert7Up5ahDZqkU3_67_CQAomZITP8MVK7O__btFScefR4m9mffete2ad2MSbY3PiN9sFFp3dFhYGnC9MJKCvYn-6jzll-4oJpzj41QvKLe8Dwpkqz3DxRV8v9dgQcgifcEUi8yix1V8_YtX-VlPiH3TKdDm6gMMQewxK-25KiajV7wSm2ZLfj_KW2CLc5qyr09egDWdhAJhn_hrkKqlaCjP6CFM998HIIkwPzW81bh3SmqRgzvobg7fgpRrA2CuvU96ZIWDplS8Mg";
    private static final String BASE_URL = "https://www.robotevents.com/api/v2";

    // --- SUPABASE CONFIGURATION ---
    private static final String SUPABASE_URL = "https://hzgvkmlonbffeuelojxv.supabase.co";
    private static final String SUPABASE_ANON_KEY = "sb_publishable_aEbWOGCjVYkkiG2LS_Zczg_mYlnRsz1";

    private final HttpClient client;
    private final ObjectMapper mapper;

    public RobotEventsService() {
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    // -------------------------------------------------------------
    // PART 1: LIVE ROBOT EVENTS DATA (For Individual Team Searches)
    // -------------------------------------------------------------
    public VexTeam getTeamByNumber(String teamNumber) throws Exception {
        String url = BASE_URL + "/teams?number%5B%5D=" + teamNumber;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", RE_API_KEY)
                .header("Accept", "application/json")
                .GET()
                .build();

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
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", RE_API_KEY)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            SkillsResponse skillsResponse = mapper.readValue(response.body(), SkillsResponse.class);
            return skillsResponse.getData();
        }
        return null;
    }

    // NEW: Fetches a single team's Global Elo score directly from Supabase for the Dashboard
    public Double getTeamGlobalElo(String teamNumber) throws Exception {
        String endpoint = SUPABASE_URL + "/rest/v1/global_truerank?select=elo_score&team_id=eq." + teamNumber + "&season_id=eq.190";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + SUPABASE_ANON_KEY)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonNode array = mapper.readTree(response.body());
            if (array.isArray() && array.size() > 0) {
                return array.get(0).path("elo_score").asDouble();
            }
        }
        return null;
    }

    // -------------------------------------------------------------
    // PART 2: CLOUD DATABASE FETCH (The Global Macro View)
    // -------------------------------------------------------------
    public List<SeasonRanking> getGlobalLeaderboard() throws Exception {
        String endpoint = SUPABASE_URL + "/rest/v1/global_truerank?select=team_id,elo_score,wins,losses,ties,teams(team_name)&season_id=eq.190&order=elo_score.desc";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + SUPABASE_ANON_KEY)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.out.println("Supabase Error: " + response.body());
            return null;
        }

        JsonNode jsonArray = mapper.readTree(response.body());
        List<SeasonRanking> globalLeaderboard = new ArrayList<>();

        if (jsonArray.isArray()) {
            for (JsonNode row : jsonArray) {
                SeasonRanking team = new SeasonRanking();

                team.setTeamNumber(row.path("team_id").asText());
                team.setEloScore(row.path("elo_score").asDouble());

                int wins = row.path("wins").asInt(0);
                int losses = row.path("losses").asInt(0);
                int ties = row.path("ties").asInt(0);
                team.setRecord(wins + "-" + losses + "-" + ties);

                JsonNode teamsNode = row.path("teams");
                if (!teamsNode.isMissingNode() && teamsNode.has("team_name")) {
                    team.setTeamName(teamsNode.get("team_name").asText());
                } else {
                    team.setTeamName("Unknown");
                }

                globalLeaderboard.add(team);
            }
        }

        return globalLeaderboard;
    }

    // -------------------------------------------------------------
    // PART 3: LOCAL EVENT ENGINE (The Event Micro View)
    // -------------------------------------------------------------
    public List<SeasonRanking> getEventTrueRank(String sku) throws Exception {
        String eventUrl = BASE_URL + "/events?sku[]=" + sku;
        HttpRequest evReq = HttpRequest.newBuilder().uri(URI.create(eventUrl)).header("Authorization", RE_API_KEY).GET().build();
        JsonNode evData = mapper.readTree(client.send(evReq, HttpResponse.BodyHandlers.ofString()).body()).path("data");

        if (evData.isEmpty()) return null;

        int eventId = evData.get(0).path("id").asInt();
        JsonNode divisions = evData.get(0).path("divisions");

        List<SeasonRanking> eventRankings = new ArrayList<>();

        for (JsonNode div : divisions) {
            int divId = div.path("id").asInt();

            String rankUrl = BASE_URL + "/events/" + eventId + "/divisions/" + divId + "/rankings?per_page=250";
            HttpRequest rankReq = HttpRequest.newBuilder().uri(URI.create(rankUrl)).header("Authorization", RE_API_KEY).GET().build();
            JsonNode rankData = mapper.readTree(client.send(rankReq, HttpResponse.BodyHandlers.ofString()).body()).path("data");

            java.util.Map<String, SeasonRanking> teamMap = new java.util.HashMap<>();
            for (JsonNode r : rankData) {
                SeasonRanking team = new SeasonRanking();
                JsonNode tNode = r.path("team");
                team.setTeamNumber(tNode.path("name").asText());
                team.setTeamName(tNode.path("team_name").asText());
                team.setWins(r.path("wins").asInt(0));
                team.setLosses(r.path("losses").asInt(0));
                team.setTies(r.path("ties").asInt(0));
                team.setRecord(team.getWins() + "-" + team.getLosses() + "-" + team.getTies());
                team.setEloScore(1500.0);
                teamMap.put(team.getTeamNumber(), team);
            }

            String matchUrl = BASE_URL + "/events/" + eventId + "/divisions/" + divId + "/matches?per_page=250";
            HttpRequest matchReq = HttpRequest.newBuilder().uri(URI.create(matchUrl)).header("Authorization", RE_API_KEY).GET().build();
            JsonNode matchData = mapper.readTree(client.send(matchReq, HttpResponse.BodyHandlers.ofString()).body()).path("data");

            for (JsonNode match : matchData) {
                JsonNode alliances = match.path("alliances");
                if (alliances.size() < 2) continue;

                JsonNode redAll = alliances.get(0).path("color").asText().equals("red") ? alliances.get(0) : alliances.get(1);
                JsonNode blueAll = alliances.get(0).path("color").asText().equals("blue") ? alliances.get(0) : alliances.get(1);

                int redScore = redAll.path("score").asInt(0);
                int blueScore = blueAll.path("score").asInt(0);
                if (redScore == 0 && blueScore == 0) continue;

                double actualRed = redScore > blueScore ? 1.0 : (redScore == blueScore ? 0.5 : 0.0);
                double actualBlue = 1.0 - actualRed;

                double mov = 1.0 + (double) Math.abs(redScore - blueScore) / (redScore + blueScore);

                processAllianceElo(redAll, blueAll, teamMap, actualRed, actualBlue, mov);
            }
            eventRankings.addAll(teamMap.values());
        }

        eventRankings.sort((t1, t2) -> Double.compare(t2.getEloScore(), t1.getEloScore()));
        return eventRankings;
    }

    private void processAllianceElo(JsonNode redAll, JsonNode blueAll, java.util.Map<String, SeasonRanking> teamMap, double actualRed, double actualBlue, double mov) {
        List<SeasonRanking> redTeams = new ArrayList<>();
        List<SeasonRanking> blueTeams = new ArrayList<>();

        for (JsonNode t : redAll.path("teams")) {
            SeasonRanking team = teamMap.get(t.path("team").path("name").asText());
            if (team != null) redTeams.add(team);
        }
        for (JsonNode t : blueAll.path("teams")) {
            SeasonRanking team = teamMap.get(t.path("team").path("name").asText());
            if (team != null) blueTeams.add(team);
        }
        if (redTeams.isEmpty() || blueTeams.isEmpty()) return;

        double redAvg = redTeams.stream().mapToDouble(SeasonRanking::getEloScore).average().orElse(1500.0);
        double blueAvg = blueTeams.stream().mapToDouble(SeasonRanking::getEloScore).average().orElse(1500.0);

        double expectedRed = 1.0 / (1.0 + Math.pow(10.0, (blueAvg - redAvg) / 400.0));
        double expectedBlue = 1.0 - expectedRed;

        double kFactor = 32.0;
        double redShift = kFactor * (actualRed - expectedRed) * mov;
        double blueShift = kFactor * (actualBlue - expectedBlue) * mov;

        for (SeasonRanking rTeam : redTeams) rTeam.setEloScore(rTeam.getEloScore() + redShift);
        for (SeasonRanking bTeam : blueTeams) bTeam.setEloScore(bTeam.getEloScore() + blueShift);
    }
}