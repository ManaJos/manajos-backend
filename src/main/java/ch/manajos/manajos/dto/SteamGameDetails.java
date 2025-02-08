package ch.manajos.manajos.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class SteamGameDetails {
    @JsonProperty("type")
    private String type;

    @JsonProperty("name")
    private String name;

    @JsonProperty("steam_appid")
    private Long steamAppId;

    @JsonProperty("short_description")
    private String shortDescription;

    @JsonProperty("header_image")
    private String headerImage;

    // New field for peak in-game player count.
    @JsonProperty("peak_in_game")
    private Integer playerCount;

    // Existing price overview
    @JsonProperty("price_overview")
    private PriceOverview priceOverview;

    // New field for screenshots.
    @JsonProperty("screenshots")
    private List<Screenshot> screenshots;

    @Data
    public static class PriceOverview {
        @JsonProperty("currency")
        private String currency;
        @JsonProperty("initial")
        private Integer initial;
        @JsonProperty("final")
        private Integer finalPrice;
        @JsonProperty("discount_percent")
        private Integer discountPercent;
    }

    // New inner DTO to capture only the id and path_full for each screenshot.
    @Data
    public static class Screenshot {
        @JsonProperty("id")
        private Integer id;
        @JsonProperty("path_full")
        private String pathFull;
    }
}
