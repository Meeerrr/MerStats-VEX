package com.merstats.vex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VexTeam {

    private int id;

    private String number;
    private String team_name;
    private String code;
    private String name;

    private String grade;

    public VexTeam() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    public String getTeam_name() { return team_name; }
    public void setTeam_name(String team_name) { this.team_name = team_name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    // --- Smart Getters (Bypasses API inconsistencies) ---
    public String getResolvedNumber() {
        if (this.number != null && !this.number.isEmpty()) return this.number;
        if (this.code != null && !this.code.isEmpty()) return this.code;
        if (this.name != null && !this.name.isEmpty()) return this.name;
        return "Unknown";
    }

    public String getResolvedName() {
        if (this.team_name != null && !this.team_name.isEmpty()) return this.team_name;
        return "";
    }
}