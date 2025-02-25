package ch.manajos.manajos.utils;

import ch.manajos.manajos.ManajosApplication;
import ch.manajos.manajos.dto.SteamGameResponse;
import ch.manajos.manajos.services.SteamService;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;

public class TestGameDetailsGenerator {

    public static void main(String[] args) {
        // 1. Start the Spring Boot context so we have access to SteamService
        ConfigurableApplicationContext context =
                SpringApplication.run(ManajosApplication.class, args);

        try {
            // 2. Grab the SteamService bean from the Spring context
            SteamService steamService = context.getBean(SteamService.class);

            // 3. Fetch the top games (this will also cache topGames if not cached yet)
            System.out.println("Fetching top games from Steam...");
            List<SteamGameResponse> topGames = steamService.getTopGames();

            // 4. Limit to 100 (if topGames returns more than 100)
            int limit = Math.min(topGames.size(), 100);
            List<SteamGameResponse> top100 = topGames.subList(0, limit);

            System.out.println("Found " + top100.size() + " games. Loading details...");

            // 5. For each game, call getGameDetails() to trigger caching of details
            for (SteamGameResponse game : top100) {
                try {
                    System.out.println("Loading details for ["
                            + game.getAppId() + "] " + game.getName());
                    steamService.getGameDetails(game.getAppId());
                } catch (Exception e) {
                    System.err.println("Failed to load details for appId="
                            + game.getAppId() + " : " + e.getMessage());
                }
            }

            System.out.println("Done loading details for top 100 games!");

        } finally {
            // 6. Close the context (optional, but good practice)
            context.close();
        }
    }
}
