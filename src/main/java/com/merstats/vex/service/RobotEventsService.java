package com.merstats.vex.service;

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
import java.util.List;

public class RobotEventsService {

    // API key
    private static final String API_KEY = "Bearer API_KEY";
    private static final String BASE_URL = "https://www.robotevents.com/api/v2";

    // Declare the client and mapper
    private final HttpClient client;
    private final ObjectMapper mapper;

    // Construct
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

        String url = BASE_URL + "/teams?number%5B%5D=" + teamNumber;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", API_KEY)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            TeamResponse teamResponse = mapper.readValue(response.body(), TeamResponse.class);
            List<VexTeam> teams = teamResponse.getData();

            if (teams != null && !teams.isEmpty()) {
                return teams.get(0);
            }
        } else {
            System.err.println("API Error: Received HTTP " + response.statusCode());
        }

        return null;
    }

    /**
     * Searches the API for a team's skills rankings using their internal ID.
     * @param teamId The internal database ID of the team (e.g., 181)
     * @return A List of SkillRanking objects.
     * @throws Exception if the network fails or JSON parsing breaks.
     */
    public List<SkillsRanking> getSkillsByTeamId(int teamId) throws Exception {

        String url = BASE_URL + "/teams/" + teamId + "/skills?per_page=250";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", API_KEY)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            SkillsResponse skillsResponse = mapper.readValue(response.body(), SkillsResponse.class);
            return skillsResponse.getData();
        } else {
            System.err.println("Skills API Error: Received HTTP " + response.statusCode());
            return null;
        }
    }

    /**
     * Fetches the global team rankings for a specific season to calculate TrueRank.
     * @param seasonId The internal database ID of the VEX season.
     * @return A List of SeasonRanking objects containing raw W/L/T, WP, AP, and SP.
     * @throws Exception if the network fails or JSON parsing breaks.
     */
    public List<SeasonRanking> getSeasonRankings(int seasonId) throws Exception {

        String url = BASE_URL + "/seasons/" + seasonId + "/rankings?per_page=250";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", API_KEY)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            SeasonRankingResponse rankingResponse = mapper.readValue(response.body(), SeasonRankingResponse.class);
            return rankingResponse.getData();
        } else {
            System.err.println("Season Rankings API Error: Received HTTP " + response.statusCode());
            return null;
        }
    }

    // Temporary main method
    public static void main(String[] args) {
        RobotEventsService service = new RobotEventsService();
        System.out.println("Starting TrueRank Test...");

        try {
            // Using a standard high school season ID (e.g., Over Under was 181)
            int targetSeasonId = 181;
            System.out.println("Fetching Global Rankings for Season ID " + targetSeasonId + "...");
            List<SeasonRanking> globalRankings = service.getSeasonRankings(targetSeasonId);

            if (globalRankings != null && !globalRankings.isEmpty()) {
                System.out.println("\n--- TRUERANK LEADERBOARD (TOP 5) ---");

                // Sort the array by our custom TrueRank score (Descending)
                globalRankings.sort((t1, t2) -> Double.compare(t2.getTrueRankScore(), t1.getTrueRankScore()));

                // Print the top 5 to test the logic
                for (int i = 0; i < Math.min(5, globalRankings.size()); i++) {
                    SeasonRanking rank = globalRankings.get(i);
                    System.out.println(String.format("#%d | Team: %-6s | Record: %-7s | MMR: %.1f",
                            (i+1), rank.getTeamNumber(), rank.getRecord(), rank.getTrueRankScore()));
                }
            } else {
                System.out.println("No season ranking data found.");
            }

        } catch (Exception e) {
            System.err.println("A network or parsing exception occurred!");
            e.printStackTrace();
        }
    }
}