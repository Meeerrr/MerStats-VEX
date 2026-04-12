package com.merstats.vex.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.merstats.vex.model.SeasonRanking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EloEngine {

    private static final double BASE_ELO = 1500.0;

    public static void calculateTrueRank(List<SeasonRanking> teams, JsonNode matchArray) {

        Map<String, SeasonRanking> teamMap = new HashMap<>();
        Map<String, Integer> matchCounts = new HashMap<>();

        for (SeasonRanking team : teams) {
            team.setEloScore(BASE_ELO);
            teamMap.put(team.getTeamNumber(), team);
            matchCounts.put(team.getTeamNumber(), 0);
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

                // Ignore unplayed matches
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

                // --- 1. ALLIANCE AVERAGING ---
                double redAvgElo = redTeams.stream().mapToDouble(SeasonRanking::getEloScore).average().orElse(BASE_ELO);
                double blueAvgElo = blueTeams.stream().mapToDouble(SeasonRanking::getEloScore).average().orElse(BASE_ELO);

                double expectedRed = 1.0 / (1.0 + Math.pow(10.0, (blueAvgElo - redAvgElo) / 400.0));
                double expectedBlue = 1.0 - expectedRed;

                double actualRed = (redScore > blueScore) ? 1.0 : (blueScore > redScore ? 0.0 : 0.5);
                double actualBlue = 1.0 - actualRed;

                // --- 2. STACKED MULTIPLIERS ---

                // A) Normalized Margin of Victory (NMoV)
                double totalScore = redScore + blueScore;
                double movMultiplier = 1.0;
                if (totalScore > 0) {
                    double marginPercent = (double) Math.abs(redScore - blueScore) / totalScore;
                    movMultiplier = 1.0 + marginPercent;
                }

                // B) Elimination Bracket Stakes (Rounds > 2 are Eliminations)
                int round = match.path("round").asInt(2);
                double elimMultiplier = (round > 2) ? 1.5 : 1.0;

                // C) Autonomous Filter (Safely reads the score breakdown if available)
                double autoRedMultiplier = 1.0;
                double autoBlueMultiplier = 1.0;
                JsonNode scoreBreakdown = match.path("score_breakdown");
                if (!scoreBreakdown.isMissingNode()) {
                    String autoWinner = scoreBreakdown.path("autonomous").asText("");
                    if (autoWinner.equals("red")) autoRedMultiplier = 1.15;
                    if (autoWinner.equals("blue")) autoBlueMultiplier = 1.15;
                }

                // Compile Final Multipliers
                double baseRedShift = (actualRed - expectedRed) * movMultiplier * elimMultiplier * autoRedMultiplier;
                double baseBlueShift = (actualBlue - expectedBlue) * movMultiplier * elimMultiplier * autoBlueMultiplier;

                // --- 3. K-FACTOR DECAY & APPLICATION ---
                for (SeasonRanking t : redTeams) {
                    String id = t.getTeamNumber();
                    int matchesPlayed = matchCounts.get(id);
                    matchCounts.put(id, matchesPlayed + 1);

                    double activeK = getDynamicKFactor(matchesPlayed);
                    t.setEloScore(t.getEloScore() + (activeK * baseRedShift));
                }

                for (SeasonRanking t : blueTeams) {
                    String id = t.getTeamNumber();
                    int matchesPlayed = matchCounts.get(id);
                    matchCounts.put(id, matchesPlayed + 1);

                    double activeK = getDynamicKFactor(matchesPlayed);
                    t.setEloScore(t.getEloScore() + (activeK * baseBlueShift));
                }
            }
        }
    }

    private static double getDynamicKFactor(int matchesPlayed) {
        if (matchesPlayed <= 3) return 64.0;  // Highly volatile provisional rating
        if (matchesPlayed <= 7) return 32.0;  // Standard tuning
        return 16.0;                          // Confirmed stable rating
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