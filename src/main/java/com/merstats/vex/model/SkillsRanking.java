package com.merstats.vex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SkillsRanking {
    private String type;
    private int rank;
    private int score;
    private int attempts;
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

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public VexEventInfo getEvent() {
        return event;
    }

    public VexSeasonInfo getSeason() {
        return season;
    }

    // Convenience methods for the JavaFX TableView
    public String getEventName() {
        if (this.event != null && this.event.getName() != null) {
            return this.event.getName();
        }
        return "Unknown Event";
    }

    public String getSeasonName() {
        if (this.season != null && this.season.getName() != null) {
            return this.season.getName();
        }
        return "Unknown Season";
    }
    public int getSeasonId() {
        if (this.season != null) {
            return this.season.getId();
        }
        return 0;
    }
    // Grabs the raw type (e.g., "driver") and capitalizes the first letter
    public String getFormattedType() {
        if (this.type == null || this.type.isEmpty()) {
            return "Unknown";
        }
        // Take the first letter, uppercase it, and attach the rest of the word
        return this.type.substring(0, 1).toUpperCase() + this.type.substring(1).toLowerCase();
    }
}