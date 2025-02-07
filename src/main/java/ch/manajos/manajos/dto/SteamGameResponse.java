package ch.manajos.manajos.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SteamGameResponse {
    @JsonProperty("appid")
    private Long appId;

    private String name;

    @JsonProperty("peak_in_game")
    private Integer playerCount;

    private Integer rank;

    // Explicit setters (if Lombok isn't working)
    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public void setPlayerCount(Integer playerCount) {
        this.playerCount = playerCount;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    // Add missing getter for appId
    public Long getAppId() {
        return appId;
    }

    // Add missing setter for name
    public void setName(String name) {
        this.name = name;
    }

    // Optionally, you can add a getter for name if needed
    public String getName() {
        return name;
    }
}
