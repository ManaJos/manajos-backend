package ch.manajos.manajos.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SteamGameDetailsResponse {
    @JsonProperty("success")
    private boolean success;
    @JsonProperty("data")
    private SteamGameDetails data;

    public boolean isSuccess() { return success; }
    public SteamGameDetails getData() { return data; }

}