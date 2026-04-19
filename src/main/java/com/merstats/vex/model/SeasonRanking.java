package com.merstats.vex.model;

public class SeasonRanking {
    private int rank;
    private String teamNumber;
    private String teamName;
    private String record;
    private double eloScore;
    private double opr; // NEW OPR FIELD
    private int wins, losses, ties, wp, ap, sp;

    public SeasonRanking() {}

    public String getTeamDisplay() {
        if (teamName != null && !teamName.isEmpty() && !teamName.equals("Unknown")) return teamNumber + " - " + teamName;
        return teamNumber;
    }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public String getTeamNumber() { return teamNumber; }
    public void setTeamNumber(String teamNumber) { this.teamNumber = teamNumber; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public String getRecord() { return record; }
    public void setRecord(String record) { this.record = record; }
    public double getEloScore() { return eloScore; }
    public void setEloScore(double eloScore) { this.eloScore = eloScore; }
    public double getOpr() { return opr; } // NEW
    public void setOpr(double opr) { this.opr = opr; } // NEW
    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }
    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }
    public int getTies() { return ties; }
    public void setTies(int ties) { this.ties = ties; }
}