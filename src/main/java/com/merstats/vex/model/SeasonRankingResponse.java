package com.merstats.vex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SeasonRankingResponse {

    private List<SeasonRanking> data;

    // Empty constructor for Jackson JSON deserialization
    public SeasonRankingResponse() {
    }

    public List<SeasonRanking> getData() {
        return data;
    }

    public void setData(List<SeasonRanking> data) {
        this.data = data;
    }
}