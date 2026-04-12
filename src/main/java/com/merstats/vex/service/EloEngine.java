package com.merstats.vex.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.merstats.vex.model.SeasonRanking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EloEngine {

    private static final double K_FACTOR = 32.0;
    private static final double BASE_ELO = 1500.0;

    public static void calculateTrueRank(List<SeasonRanking> teams, JsonNode matchArray) {

        Map<String, SeasonRanking> teamMap = new HashMap<>();
        for (SeasonRanking team : teams) {
            team.setEloScore(BASE_ELO);
            teamMap.put(team.getTeamNumber(), team);
        }

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

                // Ignore matches that haven't been played yet
                if (redScore == 0 && blueScore == 0) continue;

                String red1 = getTeamCode(redAlliance, 0);
                String red2 = getTeamCode(redAlliance, 1);
                String blue1 = getTeamCode(blueAlliance, 0);
                String blue2 = getTeamCode(blueAlliance, 1);

                List<SeasonRanking> redTeams = new ArrayList<>();
                if (teamMap.containsKey(red1)) redTeams.add(teamMap.get(red1));
                if (teamMap.containsKey(red2)) redTeams.add(teamMap.get(red2));

                List<SeasonRanking> blueTeams = new ArrayList<>();
                if (teamMap.containsKey(blue1)) blueTeams.add(teamMap.get(blue1));
                if (teamMap.containsKey(blue2)) blueTeams.add(teamMap.get(blue2));

                if (redTeams.isEmpty() || blueTeams.isEmpty()) continue;

                double redAvgElo = redTeams.stream().mapToDouble(SeasonRanking::getEloScore).average().orElse(BASE_ELO);
                double blueAvgElo = blueTeams.stream().mapToDouble(SeasonRanking::getEloScore).average().orElse(BASE_ELO);

                double expectedRed = 1.0 / (1.0 + Math.pow(10.0, (blueAvgElo - redAvgElo) / 400.0));
                double expectedBlue = 1.0 - expectedRed;

                double actualRed = (redScore > blueScore) ? 1.0 : (blueScore > redScore ? 0.0 : 0.5);
                double actualBlue = 1.0 - actualRed;

                // --- NEW: Normalized Margin of Victory (NMoV) ---
                double totalScore = redScore + blueScore;
                double movMultiplier = 1.0; // Default to 1.0x for ties or low-data matches

                if (totalScore > 0) {
                    // Calculates the percentage of dominance (e.g., 0.33 for a 33% blowout)
                    double marginPercent = (double) Math.abs(redScore - blueScore) / totalScore;

                    // Scales the multiplier from 1.0x (a tie) to a theoretical maximum of 2.0x (a total shutout)
                    movMultiplier = 1.0 + marginPercent;
                }

                // Apply the season-agnostic multiplier to the final shifts
                double redShift = K_FACTOR * (actualRed - expectedRed) * movMultiplier;
                double blueShift = K_FACTOR * (actualBlue - expectedBlue) * movMultiplier;

                for (SeasonRanking t : redTeams) t.setEloScore(t.getEloScore() + redShift);
                for (SeasonRanking t : blueTeams) t.setEloScore(t.getEloScore() + blueShift);
            }
        }
    }

    private static String getTeamCode(JsonNode allianceNode, int index) {
        JsonNode teamsArray = allianceNode.path("teams");
        if (teamsArray.isArray() && teamsArray.size() > index) {
            JsonNode teamNode = teamsArray.get(index).path("team");
            if (teamNode.has("name")) return teamNode.get("name").asText();
            if (teamNode.has("code")) return teamNode.get("code").asText();
            if (teamNode.has("number")) return teamNode.get("number").asText();
        }
        return "";
    }
}