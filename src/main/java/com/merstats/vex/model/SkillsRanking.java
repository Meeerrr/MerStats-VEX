package com.merstats.vex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SkillsRanking {
    private String type;
    private int rank;
    private int score;
    private VexEventInfo event;
    private VexSeasonInfo season;

    //empty constructor for JSON converting
    public SkillsRanking() {

    }

    public String getType() {
        return type;
    }

    public int getRank() {
        return rank;
    }

    public int getScore() {
        return score;
    }

    public VexEventInfo getEvent() {
        return event;
    }

    public VexSeasonInfo getSeason() {
        return season;
    }
}