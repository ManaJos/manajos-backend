package ch.manajos.manajos.services;

import ch.manajos.manajos.dto.UpcomingGameResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class RawgApiService {

    private final WebClient webClient;
    private final WebClient steamWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${rawg.api.key}")
    private String rawgApiKey;
    
    // Cache directory for upcoming games
    private static final String UPCOMING_GAMES_CACHE_DIR = "src/main/resources/cache/upcomingGames/";
    // Cache duration: 12 hours
    private static final long UPCOMING_GAMES_CACHE_DURATION = 43200000L;

    public RawgApiService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.rawg.io/api")
                .build();
                
        this.steamWebClient = WebClient.builder()
                .baseUrl("https://store.steampowered.com/api")
                .build();
    }
    
    /**
     * Get upcoming games for the next 12 months
     * @return List of upcoming games
     */
    public List<UpcomingGameResponse> getUpcomingGames() {
        // Try to load cached data first
        List<UpcomingGameResponse> cachedGames = loadUpcomingGamesCache();
        if (cachedGames != null) {
            return cachedGames;
        }
        
        // Calculate date range (today to 12 months from now)
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusMonths(12);
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String dateRange = today.format(formatter) + "," + endDate.format(formatter);
        
        List<UpcomingGameResponse> allGames = new ArrayList<>();
        String nextPageUrl = null;
        
        try {
            // First page
            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/games")
                            .queryParam("key", rawgApiKey)
                            .queryParam("dates", dateRange)
                            .queryParam("ordering", "released")
                            .queryParam("page_size", 40)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            
            if (response != null) {
                // Process results
                JsonNode results = response.get("results");
                if (results != null && results.isArray()) {
                    for (JsonNode game : results) {
                        allGames.add(parseGameResponse(game));
                    }
                }
                
                // Check if there's a next page
                if (response.has("next") && !response.get("next").isNull()) {
                    nextPageUrl = response.get("next").asText();
                }
                
                // Fetch up to 5 pages (200 games) to avoid rate limiting
                int pageCount = 1;
                while (nextPageUrl != null && pageCount < 5) {
                    String finalUrl = nextPageUrl;
                    response = webClient.get()
                            .uri(finalUrl)
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .block();
                    
                    if (response != null) {
                        // Process results
                        results = response.get("results");
                        if (results != null && results.isArray()) {
                            for (JsonNode game : results) {
                                allGames.add(parseGameResponse(game));
                            }
                        }
                        
                        // Check if there's a next page
                        if (response.has("next") && !response.get("next").isNull()) {
                            nextPageUrl = response.get("next").asText();
                        } else {
                            nextPageUrl = null;
                        }
                    } else {
                        nextPageUrl = null;
                    }
                    
                    pageCount++;
                }
            }
            
            // Enrich with price information
            enrichWithPrices(allGames);
            
            // Save to cache
            saveUpcomingGamesCache(allGames);
            
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
        
        return allGames;
    }
    
    /**
     * Load upcoming games from cache
     * @return List of upcoming games from cache, or null if cache is expired or doesn't exist
     */
    private List<UpcomingGameResponse> loadUpcomingGamesCache() {
        File cacheDir = new File(UPCOMING_GAMES_CACHE_DIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
            return null;
        }
        
        File cacheFile = new File(cacheDir, "upcomingGames.json");
        if (!cacheFile.exists()) {
            return null;
        }
        
        // Check if the cache is fresh (less than 12 hours old)
        if (System.currentTimeMillis() - cacheFile.lastModified() > UPCOMING_GAMES_CACHE_DURATION) {
            return null;
        }
        
        try {
            return objectMapper.readValue(cacheFile, new TypeReference<List<UpcomingGameResponse>>() {});
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Save upcoming games to cache
     * @param games List of upcoming games to save
     */
    private void saveUpcomingGamesCache(List<UpcomingGameResponse> games) {
        File cacheDir = new File(UPCOMING_GAMES_CACHE_DIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        
        File cacheFile = new File(cacheDir, "upcomingGames.json");
        try {
            objectMapper.writeValue(cacheFile, games);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Enrich games with price information from Steam
     */
    private void enrichWithPrices(List<UpcomingGameResponse> games) {
        for (UpcomingGameResponse game : games) {
            try {
                // Search for the game on Steam by name
                String gameName = game.getName();
                if (gameName == null || gameName.isEmpty()) {
                    continue;
                }
                
                // Search for the game on Steam
                JsonNode searchResponse = steamWebClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/storesearch/")
                                .queryParam("term", gameName)
                                .queryParam("l", "english")
                                .queryParam("cc", "us")
                                .build())
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block();
                
                if (searchResponse != null && searchResponse.has("items") && searchResponse.get("items").isArray() 
                        && searchResponse.get("items").size() > 0) {
                    // Get the first match
                    JsonNode firstMatch = searchResponse.get("items").get(0);
                    if (firstMatch.has("id")) {
                        String appId = firstMatch.get("id").asText();
                        
                        // Fetch price information
                        fetchAndSetPrice(game, appId);
                    }
                }
                
                // Add a small delay to avoid rate limiting (100ms)
                TimeUnit.MILLISECONDS.sleep(100);
                
            } catch (Exception e) {
                // Just log and continue, don't fail the whole process for one game
                System.err.println("Error fetching price for game " + game.getName() + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Fetch price for a specific game by Steam AppID
     */
    private void fetchAndSetPrice(UpcomingGameResponse game, String appId) {
        try {
            Map<String, Map<String, Object>> detailsResponse = steamWebClient.get()
                    .uri("/appdetails?appids={appId}&cc=us&filters=price_overview", appId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Map<String, Object>>>() {})
                    .block();

            if (detailsResponse != null && detailsResponse.containsKey(appId)) {
                Map<String, Object> responseData = detailsResponse.get(appId);
                if (responseData.containsKey("data") && responseData.get("data") instanceof Map) {
                    Map<String, Object> gameData = (Map<String, Object>) responseData.get("data");
                    
                    if (gameData.containsKey("price_overview")) {
                        Map<String, Object> priceData = (Map<String, Object>) gameData.get("price_overview");
                        if (priceData != null && priceData.containsKey("final_formatted")) {
                            game.setPrice((String) priceData.get("final_formatted"));
                            return;
                        }
                    } else if (gameData.containsKey("is_free") && (Boolean) gameData.get("is_free")) {
                        game.setPrice("Free");
                        return;
                    }
                }
            }
            
            // If we reach here, price information couldn't be found
            game.setPrice("Price unavailable");
            
        } catch (Exception e) {
            game.setPrice("Price unavailable");
        }
    }
    
    /**
     * Parse a game from the RAWG API response
     */
    private UpcomingGameResponse parseGameResponse(JsonNode gameNode) {
        UpcomingGameResponse game = new UpcomingGameResponse();
        
        // Set basic info
        game.setId(gameNode.get("id").asLong());
        game.setName(gameNode.get("name").asText());
        
        // Set image if available
        if (gameNode.has("background_image") && !gameNode.get("background_image").isNull()) {
            game.setImage(gameNode.get("background_image").asText());
        } else {
            game.setImage("Image unavailable");
        }
        
        // Set release date
        if (gameNode.has("released") && !gameNode.get("released").isNull()) {
            String releaseDateStr = gameNode.get("released").asText();
            game.setReleaseDate(LocalDate.parse(releaseDateStr));
        }
        
        // Set price as unavailable initially, will be enriched later
        game.setPrice("Price unavailable");
        
        // Set store info if available
        if (gameNode.has("stores") && gameNode.get("stores").isArray() && gameNode.get("stores").size() > 0) {
            JsonNode firstStore = gameNode.get("stores").get(0).get("store");
            if (firstStore != null) {
                game.setStore(firstStore.get("name").asText());
            } else {
                game.setStore("Store unavailable");
            }
        } else {
            game.setStore("Store unavailable");
        }
        
        return game;
    }
} 