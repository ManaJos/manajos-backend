package ch.manajos.manajos.dto;

import lombok.Data;

import java.util.List;

@Data
public class MonthlyGamesResponse {
    private String month;
    private int year;
    private List<UpcomingGameResponse> games;
    
    public MonthlyGamesResponse(String month, int year, List<UpcomingGameResponse> games) {
        this.month = month;
        this.year = year;
        this.games = games;
    }
    
    // Explicit getters and setters (if Lombok isn't working)
    public String getMonth() {
        return month;
    }
    
    public void setMonth(String month) {
        this.month = month;
    }
    
    public int getYear() {
        return year;
    }
    
    public void setYear(int year) {
        this.year = year;
    }
    
    public List<UpcomingGameResponse> getGames() {
        return games;
    }
    
    public void setGames(List<UpcomingGameResponse> games) {
        this.games = games;
    }
} 