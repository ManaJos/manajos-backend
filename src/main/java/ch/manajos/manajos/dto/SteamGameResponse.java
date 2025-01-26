package ch.manajos.manajos.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SteamGameResponse {
    @JsonProperty("appid")
    private Long appId;
    private String name;
    @JsonProperty("player_count")
    private Integer playerCount;
    private Integer rank;
}