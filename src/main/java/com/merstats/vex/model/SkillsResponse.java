package com.merstats.vex.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
@JsonIgnoreProperties(ignoreUnknown = true)
public class SkillsResponse {
     private List<SkillsRanking> data;

     //empty constructor for JSON converting
     public SkillsResponse() {

    }

    public List<SkillsRanking> getData() {
        return data;
    }
}
