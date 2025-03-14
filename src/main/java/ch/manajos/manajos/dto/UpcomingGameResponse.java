package ch.manajos.manajos.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpcomingGameResponse {
    @JsonProperty("id")
    private Long id;
    
    private String name;
    
    private String image;
    
    @JsonProperty("released")
    private LocalDate releaseDate;
    
    private String price;
    
    // Store information if available
    private String store;
    
    // Explicit getters and setters (if Lombok isn't working)
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getImage() {
        return image;
    }
    
    public void setImage(String image) {
        this.image = image;
    }
    
    public LocalDate getReleaseDate() {
        return releaseDate;
    }
    
    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }
    
    public String getPrice() {
        return price;
    }
    
    public void setPrice(String price) {
        this.price = price;
    }
    
    public String getStore() {
        return store;
    }
    
    public void setStore(String store) {
        this.store = store;
    }
} 