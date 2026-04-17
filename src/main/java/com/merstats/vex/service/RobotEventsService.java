package com.merstats.vex.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merstats.vex.model.*;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SingularValueDecomposition;

import java.net.URI;
import java.net.http.*;
import java.util.*;

public class RobotEventsService {
    // ==========================================
    // ⚠️ INSERT YOUR API KEYS HERE
    // ==========================================
    private static final String RE_API_KEY = "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIzIiwianRpIjoiNTE0MjRkZDAyMDQ1MzA1MGVlZGYzZjM2OGY4ODQ3NDFhZGMwOTZjMjExMTA1OWRiYWE0MDFiMDJjY2VhZGE1ZGE0OTc4ZmY1ZDhlYjNkNzciLCJpYXQiOjE3NzUxNDUzMzAuNTI1OTI5LCJuYmYiOjE3NzUxNDUzMzAuNTI1OTMwOSwiZXhwIjoyNzIxOTE2NTMwLjUyMDU3NzksInN1YiI6IjEyNjM3MyIsInNjb3BlcyI6W119.hZuGrp7w4nO6m8NOgm3iKsFZ5l85PV8W5yhP9mgnpgXuJCL71Tg9IWR4xTIdD1uVVEKAZGPhuwRybTGHO2jp171zVZFU-U4K7w0m_wj1xzqu8-yIHW6uzhxypMAF6Nixc__vT0gKcEuLZE_WECxRjd3PQTtZ7uOT8bu81bXpCWSSd-GtItSaGpZ10CeQ42VV0aFzNm2uMePINW2N-gMJxjbrKEIqcloNw8R8K_xdrpL8O8VwQJoQFPMmq24fLSmcsWX8l4aoDqwHWsKx19JNj80h6lwge5WdGcWxmYU1b1crESb8Od69GV78QwnM0QqgIu7KRbSqiBeOVE4GyxxIVflnQPoiRSRKz1k5I1dLEzoBwavG-LX2QZjtZZTL5gFLOc_rqJLb6Y4rANZvJBpRfFUJ0MNRn0Ert7Up5ahDZqkU3_67_CQAomZITP8MVK7O__btFScefR4m9mffete2ad2MSbY3PiN9sFFp3dFhYGnC9MJKCvYn-6jzll-4oJpzj41QvKLe8Dwpkqz3DxRV8v9dgQcgifcEUi8yix1V8_YtX-VlPiH3TKdDm6gMMQewxK-25KiajV7wSm2ZLfj_KW2CLc5qyr09egDWdhAJhn_hrkKqlaCjP6CFM998HIIkwPzW81bh3SmqRgzvobg7fgpRrA2CuvU96ZIWDplS8Mg";
    private static final String BASE_URL = "https://www.robotevents.com/api/v2";

    private static final String SUPABASE_URL = "https://hzgvkmlonbffeuelojxv.supabase.co";
    private static final String SUPABASE_ANON_KEY = "sb_publishable_aEbWOGCjVYkkiG2LS_Zczg_mYlnRsz1";

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    // ==========================================
    // DASHBOARD & SKILLS FETCHING
    // ==========================================

    public VexTeam getTeamByNumber(String teamNumber) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/teams?number%5B%5D=" + teamNumber))
                .header("Authorization", RE_API_KEY)
                .header("Accept", "application/json")
                .GET().build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
            List<VexTeam> teams = mapper.readValue(res.body(), TeamResponse.class).getData();
            if (teams != null && !teams.isEmpty()) return teams.get(0);
        }
        return null;
    }

    public List<SkillsRanking> getSkillsByTeamId(int teamId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/teams/" + teamId + "/skills?per_page=250"))
                .header("Authorization", RE_API_KEY)
                .GET().build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
            return mapper.readValue(res.body(), SkillsResponse.class).getData();
        }
        return null;
    }

    public Double getTeamGlobalElo(String teamNumber, int seasonId) throws Exception {
        String url = SUPABASE_URL + "/rest/v1/global_truerank?select=elo_score&team_id=eq." + teamNumber + "&season_id=eq." + seasonId;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + SUPABASE_ANON_KEY)
                .GET().build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
            JsonNode arr = mapper.readTree(res.body());
            if (arr.isArray() && arr.size() > 0) return arr.get(0).path("elo_score").asDouble();
        }
        return null;
    }

    // ==========================================
    // GLOBAL LEADERBOARD
    // ==========================================

    public List<SeasonRanking> getGlobalLeaderboard(int seasonId) throws Exception {
        String url = SUPABASE_URL + "/rest/v1/global_truerank?select=team_id,elo_score,opr,wins,losses,ties,teams(team_name)&season_id=eq." + seasonId + "&order=elo_score.desc";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + SUPABASE_ANON_KEY)
                .GET().build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) return null;
        List<SeasonRanking> lb = new ArrayList<>();
        JsonNode arr = mapper.readTree(res.body());

        if (arr.isArray()) {
            for (JsonNode row : arr) {
                SeasonRanking team = new SeasonRanking();
                team.setTeamNumber(row.path("team_id").asText());
                team.setEloScore(row.path("elo_score").asDouble());
                team.setOpr(row.path("opr").asDouble(0.0));
                team.setRecord(row.path("wins").asInt(0) + "-" + row.path("losses").asInt(0) + "-" + row.path("ties").asInt(0));

                JsonNode tNode = row.path("teams");
                team.setTeamName((!tNode.isMissingNode() && tNode.has("team_name")) ? tNode.get("team_name").asText() : "Unknown");
                lb.add(team);
            }
        }
        return lb;
    }

    // ==========================================
    // LOCAL EVENT TRUERANK & OPR ENGINE
    // ==========================================

    public List<SeasonRanking> getEventTrueRank(String sku) throws Exception {
        String eventUrl = BASE_URL + "/events?sku[]=" + sku;
        HttpRequest evReq = HttpRequest.newBuilder().uri(URI.create(eventUrl)).header("Authorization", RE_API_KEY).GET().build();
        JsonNode evData = mapper.readTree(client.send(evReq, HttpResponse.BodyHandlers.ofString()).body()).path("data");
        if (evData.isEmpty()) return null;

        int eventId = evData.get(0).path("id").asInt();
        List<SeasonRanking> eventRankings = new ArrayList<>();

        for (JsonNode div : evData.get(0).path("divisions")) {
            int divId = div.path("id").asInt();

            HttpRequest rankReq = HttpRequest.newBuilder().uri(URI.create(BASE_URL + "/events/" + eventId + "/divisions/" + divId + "/rankings?per_page=250")).header("Authorization", RE_API_KEY).GET().build();
            JsonNode rankData = mapper.readTree(client.send(rankReq, HttpResponse.BodyHandlers.ofString()).body()).path("data");

            Map<String, SeasonRanking> teamMap = new HashMap<>();
            for (JsonNode r : rankData) {
                SeasonRanking team = new SeasonRanking();
                team.setTeamNumber(r.path("team").path("name").asText());
                team.setTeamName(r.path("team").path("team_name").asText());
                team.setWins(r.path("wins").asInt(0));
                team.setLosses(r.path("losses").asInt(0));
                team.setTies(r.path("ties").asInt(0));
                team.setRecord(team.getWins() + "-" + team.getLosses() + "-" + team.getTies());
                team.setEloScore(1500.0);
                team.setOpr(0.0);
                teamMap.put(team.getTeamNumber(), team);
            }

            HttpRequest matchReq = HttpRequest.newBuilder().uri(URI.create(BASE_URL + "/events/" + eventId + "/divisions/" + divId + "/matches?per_page=250")).header("Authorization", RE_API_KEY).GET().build();
            JsonNode matchData = mapper.readTree(client.send(matchReq, HttpResponse.BodyHandlers.ofString()).body()).path("data");

            // 🔥 UPGRADE 1: The 3-Pass Simulation Loop to stabilize chronological bias
            for (int pass = 0; pass < 3; pass++) {
                // Reset Elo to 1500 before starting passes 2 and 3
                if (pass > 0) {
                    for (SeasonRanking t : teamMap.values()) t.setEloScore(1500.0);
                }

                for (JsonNode match : matchData) {
                    if (match.path("alliances").size() < 2) continue;

                    JsonNode redAll = match.path("alliances").get(0).path("color").asText().equals("red") ? match.path("alliances").get(0) : match.path("alliances").get(1);
                    JsonNode blueAll = match.path("alliances").get(0).path("color").asText().equals("blue") ? match.path("alliances").get(0) : match.path("alliances").get(1);

                    int rs = redAll.path("score").asInt(0);
                    int bs = blueAll.path("score").asInt(0);
                    if (rs == 0 && bs == 0) continue;

                    double actR = rs > bs ? 1.0 : (rs == bs ? 0.5 : 0.0);

                    // Slightly nerfed MOV so blowouts don't break the engine
                    double mov = Math.log(1.0 + (double) Math.abs(rs - bs)) + 1.0;

                    processAllianceElo(redAll, blueAll, teamMap, actR, 1.0 - actR, mov);
                }
            }

            // Calculate OPR
            calculateEventOPR(matchData, teamMap);

            // 🔥 UPGRADE 2: Z-Score Blending (70% Elo, 30% OPR)
            double sumElo = 0, sumOpr = 0;
            for (SeasonRanking t : teamMap.values()) {
                sumElo += t.getEloScore();
                sumOpr += t.getOpr();
            }
            double meanElo = sumElo / teamMap.size();
            double meanOpr = sumOpr / teamMap.size();

            double varElo = 0, varOpr = 0;
            for (SeasonRanking t : teamMap.values()) {
                varElo += Math.pow(t.getEloScore() - meanElo, 2);
                varOpr += Math.pow(t.getOpr() - meanOpr, 2);
            }
            double stdElo = Math.max(Math.sqrt(varElo / teamMap.size()), 1.0);
            double stdOpr = Math.max(Math.sqrt(varOpr / teamMap.size()), 1.0);

            for (SeasonRanking t : teamMap.values()) {
                double zElo = (t.getEloScore() - meanElo) / stdElo;
                double zOpr = (t.getOpr() - meanOpr) / stdOpr;

                // The Golden Ratio
                double blendedZ = (zElo * 0.70) + (zOpr * 0.30);

                // Re-map to the intuitive 1500 scale
                double finalTrueRank = meanElo + (blendedZ * stdElo);
                t.setEloScore(finalTrueRank);
            }

            eventRankings.addAll(teamMap.values());
        }

        eventRankings.sort((t1, t2) -> Double.compare(t2.getEloScore(), t1.getEloScore()));
        return eventRankings;
    }

    private void processAllianceElo(JsonNode redAll, JsonNode blueAll, Map<String, SeasonRanking> teamMap, double actR, double actB, double mov) {
        List<SeasonRanking> redT = new ArrayList<>();
        List<SeasonRanking> blueT = new ArrayList<>();

        for (JsonNode t : redAll.path("teams")) {
            if (teamMap.containsKey(t.path("team").path("name").asText())) redT.add(teamMap.get(t.path("team").path("name").asText()));
        }
        for (JsonNode t : blueAll.path("teams")) {
            if (teamMap.containsKey(t.path("team").path("name").asText())) blueT.add(teamMap.get(t.path("team").path("name").asText()));
        }

        if (redT.isEmpty() || blueT.isEmpty()) return;

        double redAvg = redT.stream().mapToDouble(SeasonRanking::getEloScore).average().orElse(1500.0);
        double blueAvg = blueT.stream().mapToDouble(SeasonRanking::getEloScore).average().orElse(1500.0);

        // Expected outcome probability formula
        double expR = 1.0 / (1.0 + Math.pow(10.0, (blueAvg - redAvg) / 400.0));

        double rs = 32.0 * (actR - expR) * mov;
        double bs = 32.0 * (actB - (1.0 - expR)) * mov;

        for (SeasonRanking t : redT) t.setEloScore(t.getEloScore() + rs);
        for (SeasonRanking t : blueT) t.setEloScore(t.getEloScore() + bs);
    }

    private void calculateEventOPR(JsonNode matchData, Map<String, SeasonRanking> teamMap) {
        // 1. Create a definitive index for all teams in this division
        List<String> teamIndexList = new ArrayList<>(teamMap.keySet());
        int numTeams = teamIndexList.size();
        if (numTeams == 0) return;

        // 2. Initialize the Linear Algebra Matrices
        RealMatrix ATA = new Array2DRowRealMatrix(numTeams, numTeams);
        RealVector ATB = new ArrayRealVector(numTeams);

        // 3. Populate the Matrices based on Match Results
        for (JsonNode match : matchData) {
            if (match.path("alliances").size() < 2) continue;

            // Ensure matches with 0-0 score (unplayed) are skipped for OPR
            int rsTest = match.path("alliances").get(0).path("score").asInt(0);
            int bsTest = match.path("alliances").get(1).path("score").asInt(0);
            if (rsTest == 0 && bsTest == 0) continue;

            for (int i = 0; i < 2; i++) {
                JsonNode alliance = match.path("alliances").get(i);
                int score = alliance.path("score").asInt(0);
                JsonNode teams = alliance.path("teams");

                // Find the matrix indices for the teams in this alliance
                List<Integer> indices = new ArrayList<>();
                for (JsonNode t : teams) {
                    String teamNum = t.path("team").path("name").asText();
                    int idx = teamIndexList.indexOf(teamNum);
                    if (idx != -1) indices.add(idx);
                }

                // Add to A^T * A (Matches played together) and A^T * B (Total Scores)
                for (int row : indices) {
                    ATB.setEntry(row, ATB.getEntry(row) + score);
                    for (int col : indices) {
                        ATA.setEntry(row, col, ATA.getEntry(row, col) + 1);
                    }
                }
            }
        }

        // solve the matrix
        try {
            DecompositionSolver solver = new SingularValueDecomposition(ATA).getSolver();
            RealVector oprVector = solver.solve(ATB);

            // 5. Save the calculated OPR back to our Java Objects
            for (int i = 0; i < numTeams; i++) {
                String teamNum = teamIndexList.get(i);
                teamMap.get(teamNum).setOpr(oprVector.getEntry(i));
            }
        } catch (Exception e) {
            System.err.println("Matrix Solver Failed for Event OPR: " + e.getMessage());
        }
    }
}