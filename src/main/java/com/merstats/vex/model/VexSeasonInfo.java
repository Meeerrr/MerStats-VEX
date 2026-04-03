package com.merstats.vex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VexSeasonInfo {
    private int id;
    private String name;
    private String code;

    //empty constructor for JSON converting
    public VexSeasonInfo() {

    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }
}
