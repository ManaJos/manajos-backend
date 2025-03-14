package ch.manajos.manajos.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SteamGameResponse {
    @JsonProperty("appid")
    private Long appId;
    private String name;

    // New field for the image
    private String image;

    @JsonProperty("peak_in_game")
    private Integer playerCount;

    private Integer rank;
    
    // New field for price
    private String price;

    // Explicit setters (if Lombok isn't working)
    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public Long getAppId() {
        return appId;
    }

    public void setPlayerCount(Integer playerCount) {
        this.playerCount = playerCount;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    // Explicit setter for name
    public void setName(String name) {
        this.name = name;
    }

    // Optionally, you can add a getter for name if needed
    public String getName() {
        return name;
    }

    // Explicit getter and setter for image
    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
    
    // Explicit getter and setter for price
    public String getPrice() {
        return price;
    }
    
    public void setPrice(String price) {
        this.price = price;
    }
}
