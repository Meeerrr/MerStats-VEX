package com.merstats.vex.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.merstats.vex.model.SeasonRanking;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EloEngine {

    // K-Factor dictates how heavily a single match impacts the rating.
    // 32 is standard for dynamic, short-term tournaments (like e-sports or chess qualifiers).
    private static final double K_FACTOR = 32.0;
    private static final double BASE_ELO = 1500.0;

    /**
     * Iterates through every match chronologically, calculates the expected 2v2 outcome,
     * and adjusts the Elo ratings of all four teams based on the actual score.
     */
    public static void calculateTrueRank(List<SeasonRanking> teams, JsonNode matchArray) {

        // 1. Initialize all teams to base ELO and create a lookup map for instant access
        Map<String, SeasonRanking> teamMap = new HashMap<>();
        for (SeasonRanking team : teams) {
            team.setEloScore(BASE_ELO);
            teamMap.put(team.getTeamNumber(), team);
        }

        // 2. Iterate chronologically through the division's match schedule
        if (matchArray != null && matchArray.isArray()) {
            for (JsonNode match : matchArray) {

                JsonNode alliances = match.path("alliances");
                if (!alliances.isArray() || alliances.size() < 2) continue;

                JsonNode redAlliance = null;
                JsonNode blueAlliance = null;

                for (JsonNode alliance : alliances) {
                    if (alliance.path("color").asText().equals("red")) redAlliance = alliance;
                    if (alliance.path("color").asText().equals("blue")) blueAlliance = alliance;
                }

                if (redAlliance == null || blueAlliance == null) continue;

                int redScore = redAlliance.path("score").asInt();
                int blueScore = blueAlliance.path("score").asInt();

                // Skip matches that haven't been played yet (0-0 ties are mathematically ignored by this filter to prevent division errors early in tournaments)
                if (redScore == 0 && blueScore == 0) continue;

                // 3. Extract the team numbers from the JSON payload
                String red1 = getTeamCode(redAlliance, 0);
                String red2 = getTeamCode(redAlliance, 1);
                String blue1 = getTeamCode(blueAlliance, 0);
                String blue2 = getTeamCode(blueAlliance, 1);

                // Ensure all 4 teams exist in our ranking list before doing math
                if (!teamMap.containsKey(red1) || !teamMap.containsKey(red2) ||
                        !teamMap.containsKey(blue1) || !teamMap.containsKey(blue2)) {
                    continue;
                }

                SeasonRanking r1 = teamMap.get(red1);
                SeasonRanking r2 = teamMap.get(red2);
                SeasonRanking b1 = teamMap.get(blue1);
                SeasonRanking b2 = teamMap.get(blue2);

                // 4. Calculate Alliance Averages
                double redAvgElo = (r1.getEloScore() + r2.getEloScore()) / 2.0;
                double blueAvgElo = (b1.getEloScore() + b2.getEloScore()) / 2.0;

                // 5. Calculate Expected Win Probabilities using the ELO formula
                double expectedRed = 1.0 / (1.0 + Math.pow(10.0, (blueAvgElo - redAvgElo) / 400.0));
                double expectedBlue = 1.0 - expectedRed;

                // 6. Determine the Actual Outcome Multipliers (Win = 1.0, Tie = 0.5, Loss = 0.0)
                double actualRed = 0.5;
                double actualBlue = 0.5;

                if (redScore > blueScore) {
                    actualRed = 1.0;
                    actualBlue = 0.0;
                } else if (blueScore > redScore) {
                    actualRed = 0.0;
                    actualBlue = 1.0;
                }

                // 7. Calculate ELO shifts
                double redShift = K_FACTOR * (actualRed - expectedRed);
                double blueShift = K_FACTOR * (actualBlue - expectedBlue);

                // 8. Apply shifts dynamically to the objects
                r1.setEloScore(r1.getEloScore() + redShift);
                r2.setEloScore(r2.getEloScore() + redShift);
                b1.setEloScore(b1.getEloScore() + blueShift);
                b2.setEloScore(b2.getEloScore() + blueShift);
            }
        }
    }

    private static String getTeamCode(JsonNode allianceNode, int index) {
        JsonNode teamsArray = allianceNode.path("teams");
        if (teamsArray.isArray() && teamsArray.size() > index) {
            JsonNode teamNode = teamsArray.get(index).path("team");
            // The API randomly alternates between "code" and "number" based on the endpoint, so we check both
            if (teamNode.has("code")) return teamNode.get("code").asText();
            if (teamNode.has("number")) return teamNode.get("number").asText();
        }
        return "";
    }
}