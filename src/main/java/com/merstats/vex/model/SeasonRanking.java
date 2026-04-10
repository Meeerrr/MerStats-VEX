package com.merstats.vex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SeasonRanking {

    private VexTeam team;
    private VexEventInfo event;
    private int rank;
    private int wins;
    private int losses;
    private int ties;
    private int wp;
    private int ap;
    private int sp;

    // Empty constructor for Jackson JSON deserialization
    public SeasonRanking() {
    }

    // --- Core Algorithm: TrueRank MMR ---
    public double getTrueRankScore() {
        int totalMatches = wins + losses + ties;

        // Prevent Divide-By-Zero errors for teams with 0 played matches
        if (totalMatches == 0) {
            return 0.0;
        }

        // VEX specific Win Rate formula using Win Points (WP)
        double winRate = (double) wp / (totalMatches * 2);

        // Averages
        double avgAp = (double) ap / totalMatches;
        double avgSp = (double) sp / totalMatches;

        // TrueRank Calculation
        double rawMmr = (winRate * 400.0) + (avgAp * 3.0) + (avgSp * 1.5);

        // Round to exactly one decimal place for clean UI rendering (e.g., 2145.5)
        return Math.round(rawMmr * 10.0) / 10.0;
    }

    // --- Getters ---
    public VexTeam getTeam() { return team; }
    public VexEventInfo getEvent() { return event; }
    public int getRank() { return rank; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public int getTies() { return ties; }
    public int getWp() { return wp; }
    public int getAp() { return ap; }
    public int getSp() { return sp; }

    // --- UI Convenience Methods ---
    public String getTeamNumber() {
        return team != null ? team.getNumber() : "Unknown";
    }

    public String getTeamName() {
        return team != null ? team.getTeam_name() : "Unknown";
    }

    public String getRecord() {
        return wins + "-" + losses + "-" + ties;
    }
}