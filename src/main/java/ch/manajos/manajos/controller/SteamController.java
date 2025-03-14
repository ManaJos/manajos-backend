package ch.manajos.manajos.controller;

import ch.manajos.manajos.dto.MonthlyGamesResponse;
import ch.manajos.manajos.dto.SteamGameDetails;
import ch.manajos.manajos.dto.SteamGameResponse;
import ch.manajos.manajos.dto.SteamUserResponse;
import ch.manajos.manajos.dto.UpcomingGameResponse;
import ch.manajos.manajos.services.RawgApiService;
import ch.manajos.manajos.services.SteamService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/steam")
public class SteamController {
    private final SteamService steamService;
    private final RawgApiService rawgApiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SteamController(SteamService steamService, RawgApiService rawgApiService) {
        this.steamService = steamService;
        this.rawgApiService = rawgApiService;
    }

    @GetMapping("/")
    public String home() {
        return "Backend is running! Use /api/steam endpoints";
    }

    @GetMapping("/top-games")
    public List<SteamGameResponse> getTopGames() {
        return steamService.getTopGames();
    }

    @GetMapping("/games/{appId}")
    public SteamGameDetails getGameDetails(@PathVariable("appId") Long appId) {
        return steamService.getGameDetails(appId);
    }

    @GetMapping("/users/{steamId64}")
    public SteamUserResponse getUserInfo(@PathVariable("steamId64") String steamId64) {
        return steamService.getUserInfo(steamId64);
    }

    /**
     * Returns a list of (timestamp, peak) data points for the specified game,
     * collected from the topGames cache files of the last N days (default 30).
     * Example: GET /api/steam/games/730/peak-history?days=30
     */
    @GetMapping("/games/{appId}/peak-history")
    public List<SteamService.PeakDataPoint> getPeakHistory(
            @PathVariable("appId") Long appId,
            @RequestParam(value = "days", defaultValue = "30") int days
    ) {
        return steamService.getPeakHistory(appId, days);
    }
    
    /**
     * Returns upcoming game releases grouped by month for the next 12 months.
     * Example: GET /api/steam/upcoming-releases
     */
    @GetMapping("/upcoming-releases")
    public List<MonthlyGamesResponse> getUpcomingReleases() {
        // Get all upcoming games
        List<UpcomingGameResponse> allGames = rawgApiService.getUpcomingGames();
        
        // Group games by month and year
        Map<String, List<UpcomingGameResponse>> gamesByMonth = allGames.stream()
                .filter(game -> game.getReleaseDate() != null)
                .collect(Collectors.groupingBy(game -> {
                    Month month = game.getReleaseDate().getMonth();
                    int year = game.getReleaseDate().getYear();
                    return month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;
                }));
        
        // Convert to list of MonthlyGamesResponse
        List<MonthlyGamesResponse> result = new ArrayList<>();
        for (Map.Entry<String, List<UpcomingGameResponse>> entry : gamesByMonth.entrySet()) {
            String[] parts = entry.getKey().split(" ");
            String month = parts[0];
            int year = Integer.parseInt(parts[1]);
            
            result.add(new MonthlyGamesResponse(month, year, entry.getValue()));
        }
        
        // Sort by year and month
        result.sort(Comparator.comparing(MonthlyGamesResponse::getYear)
                .thenComparing(response -> Month.valueOf(response.getMonth().toUpperCase())));
        
        return result;
    }

    /**
     * DTO for returning a single (timestamp, peak) data point.
     */

}
