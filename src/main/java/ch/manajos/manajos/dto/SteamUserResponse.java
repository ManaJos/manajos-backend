package ch.manajos.manajos.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SteamUserResponse {
    @JsonProperty("steamid")
    private String steamId;
    @JsonProperty("personaname")
    private String personaName;
    @JsonProperty("profileurl")
    private String profileUrl;
    @JsonProperty("avatarfull")
    private String avatarFull;
    @JsonProperty("loccountrycode")
    private String countryCode;
}