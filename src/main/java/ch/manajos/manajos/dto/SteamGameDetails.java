package ch.manajos.manajos.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

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

    // Add more fields as needed from:
    // https://partner.steamgames.com/doc/store/getappdetails
    @JsonProperty("price_overview")
    private PriceOverview priceOverview;

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
}