package com.merstats.vex.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merstats.vex.model.*;

import io.github.cdimascio.dotenv.Dotenv; // Added Dotenv import

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
    // 🔒 ENVIRONMENT VARIABLES
    // ==========================================
    // ignoreIfMissing() ensures that if the app is packaged and run in a CI/CD pipeline
    // or production where .env files aren't used, it will fall back to System variables.
    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    // Dynamically pull from .env and format correctly
    private static final String RE_API_KEY = "Bearer " + dotenv.get("ROBOT_EVENTS_KEY");
    private static final String SUPABASE_URL = dotenv.get("SUPABASE_URL");
    private static final String SUPABASE_KEY = dotenv.get("SUPABASE_KEY");

    private static final String BASE_URL = "https://events.vex.com/api/v2";

    static {
        System.out.println("\n==============================================");
        System.out.println(" 🔧 MERSTATS DIAGNOSTIC: ENVIRONMENT VARIABLES");
        System.out.println("==============================================");
        System.out.println(" -> Supabase URL Loaded: " + (SUPABASE_URL != null ? SUPABASE_URL : "❌ NULL (Check .env location)"));
        System.out.println(" -> Supabase Key Loaded: " + (SUPABASE_KEY != null ? "✅ YES (Hidden for security)" : "❌ NULL"));
        System.out.println(" -> RobotEvents Key Loaded: " + (dotenv.get("ROBOT_EVENTS_KEY") != null ? "✅ YES (Hidden for security)" : "❌ NULL"));
        System.out.println(" -> Formatted RE_API_KEY starts with 'Bearer ': " + (RE_API_KEY.startsWith("Bearer null") ? "❌ NO (It says Bearer null)" : "✅ YES"));
        System.out.println("==============================================\n");
    }

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

        // 🚨 INJECT THIS LOGGING BLOCK 🚨
        System.out.println("\n--- API NETWORK REQUEST ---");
        System.out.println("Target URL: " + req.uri());
        System.out.println("Status Code: " + res.statusCode());
        System.out.println("Raw Body: " + res.body());
        System.out.println("---------------------------\n");

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
                .header("Accept", "application/json")
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
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .header("Accept", "application/json")
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
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .header("Accept", "application/json")
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

        HttpRequest evReq = HttpRequest.newBuilder()
                .uri(URI.create(eventUrl))
                .header("Authorization", RE_API_KEY)
                .header("Accept", "application/json")
                .GET().build();

        JsonNode evData = mapper.readTree(client.send(evReq, HttpResponse.BodyHandlers.ofString()).body()).path("data");
        if (evData.isEmpty()) return null;

        int eventId = evData.get(0).path("id").asInt();
        List<SeasonRanking> eventRankings = new ArrayList<>();

        for (JsonNode div : evData.get(0).path("divisions")) {
            int divId = div.path("id").asInt();

            HttpRequest rankReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/events/" + eventId + "/divisions/" + divId + "/rankings?per_page=250"))
                    .header("Authorization", RE_API_KEY)
                    .header("Accept", "application/json")
                    .GET().build();

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

            HttpRequest matchReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/events/" + eventId + "/divisions/" + divId + "/matches?per_page=250"))
                    .header("Authorization", RE_API_KEY)
                    .header("Accept", "application/json")
                    .GET().build();

            JsonNode matchData = mapper.readTree(client.send(matchReq, HttpResponse.BodyHandlers.ofString()).body()).path("data");

            // 2-Pass Local Simulation
            for (int pass = 0; pass < 2; pass++) {
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

                    // SURGICAL NERF 1: Hyper-compressed MOV. Maxes out around 1.3x for insane blowouts.
                    double mov = 1.0 + (Math.log10(1.0 + Math.abs(rs - bs)) * 0.15);

                    processAllianceElo(redAll, blueAll, teamMap, actR, 1.0 - actR, mov);
                }
            }

            calculateEventOPR(matchData, teamMap);

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

            // SURGICAL NERF 2: Dynamic OPR Weighting based on Event Size
            double oprWeight = Math.max(0.30, 0.85 - (teamMap.size() * 0.007));
            double eloWeight = 1.0 - oprWeight;

            for (SeasonRanking t : teamMap.values()) {
                double zElo = (t.getEloScore() - meanElo) / stdElo;
                double zOpr = (t.getOpr() - meanOpr) / stdOpr;

                double blendedZ = (zElo * eloWeight) + (zOpr * oprWeight);

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

        double expR = 1.0 / (1.0 + Math.pow(10.0, (blueAvg - redAvg) / 400.0));

        // TWEAK 4: Halved the K-Factor (16.0 instead of 32.0)
        double rs = 16.0 * (actR - expR) * mov;
        double bs = 16.0 * (actB - (1.0 - expR)) * mov;

        for (SeasonRanking t : redT) t.setEloScore(t.getEloScore() + rs);
        for (SeasonRanking t : blueT) t.setEloScore(t.getEloScore() + bs);
    }

    private void calculateEventOPR(JsonNode matchData, Map<String, SeasonRanking> teamMap) {
        List<String> teamIndexList = new ArrayList<>(teamMap.keySet());
        int numTeams = teamIndexList.size();
        if (numTeams == 0) return;

        RealMatrix ATA = new Array2DRowRealMatrix(numTeams, numTeams);
        RealVector ATB = new ArrayRealVector(numTeams);

        for (JsonNode match : matchData) {
            if (match.path("alliances").size() < 2) continue;

            int rsTest = match.path("alliances").get(0).path("score").asInt(0);
            int bsTest = match.path("alliances").get(1).path("score").asInt(0);
            if (rsTest == 0 && bsTest == 0) continue;

            for (int i = 0; i < 2; i++) {
                JsonNode alliance = match.path("alliances").get(i);
                int score = alliance.path("score").asInt(0);
                JsonNode teams = alliance.path("teams");

                List<Integer> indices = new ArrayList<>();
                for (JsonNode t : teams) {
                    String teamNum = t.path("team").path("name").asText();
                    int idx = teamIndexList.indexOf(teamNum);
                    if (idx != -1) indices.add(idx);
                }

                for (int row : indices) {
                    ATB.setEntry(row, ATB.getEntry(row) + score);
                    for (int col : indices) {
                        ATA.setEntry(row, col, ATA.getEntry(row, col) + 1);
                    }
                }
            }
        }

        try {
            DecompositionSolver solver = new SingularValueDecomposition(ATA).getSolver();
            RealVector oprVector = solver.solve(ATB);

            for (int i = 0; i < numTeams; i++) {
                String teamNum = teamIndexList.get(i);
                teamMap.get(teamNum).setOpr(oprVector.getEntry(i));
            }
        } catch (Exception e) {
            System.err.println("Matrix Solver Failed for Event OPR: " + e.getMessage());
        }
    }
}