package com.merstats.vex.model;

public class SeasonRanking {

    private int rank;
    private String teamNumber;
    private String teamName;
    private String record;
    private double eloScore;

    private int wins;
    private int losses;
    private int ties;

    private int wp;
    private int ap;
    private int sp;

    public SeasonRanking() {}

    public String getTeamDisplay() {
        if (teamName != null && !teamName.isEmpty() && !teamName.equals("Unknown")) {
            return teamNumber + " - " + teamName;
        }
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
}