package com.merstats.vex.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VexTeam {
    private int id;

    @JsonAlias({"number", "code"})
    private String number;

    @JsonAlias({"team_name", "name"})
    private String team_name;

    private String grade;

    public VexTeam() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    // ADDED: Foolproof setter for the Rankings API endpoint
    public void setCode(String code) { this.number = code; }

    public String getTeam_name() { return team_name; }
    public void setTeam_name(String team_name) { this.team_name = team_name; }

    // ADDED: Foolproof setter for the Rankings API endpoint
    public void setName(String name) { this.team_name = name; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
}