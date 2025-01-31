package ch.manajos.manajos.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SteamGameResponse {
    @JsonProperty("appid")
    private Long appId;
    private String name;  // This will be populated later
    @JsonProperty("peak_in_game")
    private Integer playerCount;
    private Integer rank;

    // Add explicit getters/setters if Lombok isn't working
    public Long getAppId() {
        return appId;
    }

    public void setName(String name) {
        this.name = name;
    }
}