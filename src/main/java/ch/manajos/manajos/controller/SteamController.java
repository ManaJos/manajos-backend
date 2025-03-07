package ch.manajos.manajos.controller;

import ch.manajos.manajos.dto.SteamGameDetails;
import ch.manajos.manajos.dto.SteamGameResponse;
import ch.manajos.manajos.dto.SteamUserResponse;
import ch.manajos.manajos.services.SteamService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/steam")
public class SteamController {
    private final SteamService steamService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SteamController(SteamService steamService) {
        this.steamService = steamService;
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
     * DTO for returning a single (timestamp, peak) data point.
     */

}
