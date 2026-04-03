package com.merstats.vex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamResponse {
    private List<VexTeam> data;

    public List<VexTeam> getData() {
        return data;
    }
    public void setData(List<VexTeam> data) {
        this.data = data;
    }
}
