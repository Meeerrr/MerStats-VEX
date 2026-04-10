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

    // NEW: The dynamic Elo tracker managed by the EloEngine
    private double eloScore = 1500.0;

    public SeasonRanking() {
    }

    // This method now serves the dynamically processed Elo to the JavaFX TableView
    public double getTrueRankScore() {
        return Math.round(this.eloScore * 10.0) / 10.0;
    }

    public double getEloScore() { return eloScore; }
    public void setEloScore(double eloScore) { this.eloScore = eloScore; }

    public VexTeam getTeam() { return team; }
    public void setTeam(VexTeam team) { this.team = team; }

    public VexEventInfo getEvent() { return event; }
    public void setEvent(VexEventInfo event) { this.event = event; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }

    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }

    public int getTies() { return ties; }
    public void setTies(int ties) { this.ties = ties; }

    public int getWp() { return wp; }
    public void setWp(int wp) { this.wp = wp; }

    public int getAp() { return ap; }
    public void setAp(int ap) { this.ap = ap; }

    public int getSp() { return sp; }
    public void setSp(int sp) { this.sp = sp; }

    public String getTeamNumber() {
        return team != null && team.getNumber() != null ? team.getNumber() : "Unknown";
    }

    public String getTeamName() {
        return team != null && team.getTeam_name() != null ? team.getTeam_name() : "Unknown";
    }

    public String getTeamDisplay() {
        if (team == null) return "Unknown Team";
        String num = team.getNumber() != null ? team.getNumber() : "???";
        String name = team.getTeam_name() != null ? team.getTeam_name() : "Unknown";
        return num + " | " + name;
    }

    public String getRecord() {
        return wins + "-" + losses + "-" + ties;
    }
}