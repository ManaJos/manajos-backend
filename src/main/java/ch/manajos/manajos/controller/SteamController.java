package ch.manajos.manajos.controller;

import ch.manajos.manajos.dto.SteamGameDetails;
import ch.manajos.manajos.dto.SteamGameResponse;
import ch.manajos.manajos.dto.SteamUserResponse;
import ch.manajos.manajos.services.SteamService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/steam")
public class SteamController {
    private final SteamService steamService;

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
}