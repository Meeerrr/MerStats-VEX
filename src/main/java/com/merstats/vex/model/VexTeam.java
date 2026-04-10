package com.merstats.vex.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VexTeam {
    private int id;

    // Accept either "number" or "code" from the API
    @JsonAlias({"number", "code"})
    private String number;

    // Accept either "team_name" or "name" from the API
    @JsonAlias({"team_name", "name"})
    private String team_name;

    private String grade;

    public VexTeam() {
    }

    // --- Getters and Setters ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNumber() {
        return number;
    }
    public void setNumber(String number) {
        this.number = number;
    }

    public String getTeam_name() {
        return team_name;
    }
    public void setTeam_name(String team_name) {
        this.team_name = team_name;
    }

    public String getGrade() {
        return grade;
    }
    public void setGrade(String grade) {
        this.grade = grade;
    }
}