package ch.manajos.manajos.services;

import ch.manajos.manajos.dto.SteamGameDetails;
import ch.manajos.manajos.dto.SteamGameDetailsResponse;
import ch.manajos.manajos.dto.SteamGameResponse;
import ch.manajos.manajos.dto.SteamUserResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SteamService {

    private final WebClient webClient;

    @Value("${steam.api.key}")
    private String steamApiKey;

    // ObjectMapper for JSON serialization/deserialization
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Directories for caching data
    private static final String TOP_GAMES_CACHE_DIR = "src/main/resources/cache/topGames/";
    private static final String GAME_DETAILS_CACHE_DIR = "src/main/resources/cache/gameDetails/";
    // Duration limits in milliseconds: 24 hours for top games and 2 weeks for game details
    private static final long TOP_GAMES_CACHE_DURATION = 86400000L;
    private static final long GAME_DETAILS_CACHE_DURATION = 1209600000L;

    public SteamService(WebClient webClient) {
        this.webClient = webClient;
    }

    // ----------------------------------------------------------------
    // 1. Existing: getTopGames()
    // ----------------------------------------------------------------
    public List<SteamGameResponse> getTopGames() {
        // Try to load cached data first
        List<SteamGameResponse> cachedGames = loadTopGamesCache();
        if (cachedGames != null) {
            return cachedGames;
        }

        // 1. Get basic game data from the API
        List<SteamGameResponse> games = webClient.get()
                .uri("/ISteamChartsService/GetMostPlayedGames/v1/")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Map<String, Object>>>() {})
                .map(response -> {
                    Map<String, Object> responseBody = response.get("response");
                    List<Map<String, Object>> ranks = (List<Map<String, Object>>) responseBody.get("ranks");
                    return parseTopGames(ranks);
                })
                .block();

        // 2. Enrich with game names, images, and prices
        if (games != null) {
            games.parallelStream().forEach(game -> {
                try {
                    Map<String, Map<String, Object>> detailsResponse = webClient.get()
                            .uri("https://store.steampowered.com/api/appdetails?appids={appId}&cc=us&filters=price_overview", game.getAppId())
                            .retrieve()
                            .bodyToMono(new ParameterizedTypeReference<Map<String, Map<String, Object>>>() {})
                            .block();

                    if (detailsResponse != null && detailsResponse.containsKey(game.getAppId().toString())) {
                        Object dataObject = detailsResponse.get(game.getAppId().toString()).get("data");
                        if (dataObject instanceof Map) {
                            Map<String, Object> gameData = (Map<String, Object>) dataObject;
                            game.setName((String) gameData.get("name"));
                            game.setImage((String) gameData.get("header_image"));
                            
                            // Add price information
                            if (gameData.containsKey("price_overview")) {
                                Map<String, Object> priceData = (Map<String, Object>) gameData.get("price_overview");
                                if (priceData != null && priceData.containsKey("final_formatted")) {
                                    game.setPrice((String) priceData.get("final_formatted"));
                                } else {
                                    game.setPrice("Price unavailable");
                                }
                            } else if (gameData.containsKey("is_free") && (Boolean) gameData.get("is_free")) {
                                game.setPrice("Free");
                            } else {
                                game.setPrice("Price unavailable");
                            }
                        } else {
                            game.setName("Name unavailable");
                            game.setImage("Image unavailable");
                            game.setPrice("Price unavailable");
                        }
                    }
                } catch (Exception e) {
                    game.setName("Name unavailable");
                    game.setImage("Image unavailable");
                    game.setPrice("Price unavailable");
                }
            });
        }

        // Save the new API response to cache (without overwriting previous files)
        if (games != null) {
            saveTopGamesCache(games);
        }

        return games != null ? games : Collections.emptyList();
    }

    private List<SteamGameResponse> parseTopGames(List<Map<String, Object>> ranks) {
        return ranks.stream().map(entry -> {
            SteamGameResponse game = new SteamGameResponse();
            game.setAppId(Long.parseLong(entry.get("appid").toString()));
            game.setPlayerCount(Integer.parseInt(entry.get("peak_in_game").toString()));
            game.setRank(Integer.parseInt(entry.get("rank").toString()));
            return game;
        }).collect(Collectors.toList());
    }

    // ----------------------------------------------------------------
    // 2. Existing: getGameDetails(appId)
    // ----------------------------------------------------------------
    public SteamGameDetails getGameDetails(Long appId) {
        // Try to load cached game details first
        SteamGameDetails cachedDetails = loadGameDetailsCache(appId);
        if (cachedDetails != null) {
            return cachedDetails;
        }

        // Fetch details from Steam Store API
        Map<String, SteamGameDetailsResponse> detailsResponse = Objects.requireNonNull(
                webClient.get()
                        .uri("https://store.steampowered.com/api/appdetails?appids={appId}", appId)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Map<String, SteamGameDetailsResponse>>() {})
                        .block()
        );
        SteamGameDetails details = detailsResponse.get(appId.toString()).getData();

        // Enrich with peak in-game player count from the top games list
        List<SteamGameResponse> topGames = getTopGames();
        topGames.stream()
                .filter(game -> game.getAppId().equals(appId))
                .findFirst()
                .ifPresent(match -> details.setPlayerCount(match.getPlayerCount()));

        // Save the new details data to cache (appending a new file)
        saveGameDetailsCache(appId, details);
        return details;
    }

    // ----------------------------------------------------------------
    // 3. NEW: getPeakHistory(appId, days)
    // ----------------------------------------------------------------
    /**
     * Reads all "topGames_*.json" files, parses the timestamp from the filename,
     * and returns a list of (timestamp, peak) for the specified appId.
     */
    public List<PeakDataPoint> getPeakHistory(Long appId, int days) {
        File cacheDir = new File(TOP_GAMES_CACHE_DIR);
        if (!cacheDir.exists()) {
            return Collections.emptyList();
        }

        File[] files = cacheDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        List<PeakDataPoint> result = new ArrayList<>();

        for (File file : files) {
            String filename = file.getName(); // e.g. "topGames_1740658015189.json"
            long fileTimestamp;
            try {
                // Extract the number after "topGames_" and before ".json"
                String timestampStr = filename.substring(
                        filename.indexOf('_') + 1,
                        filename.lastIndexOf('.')
                );
                fileTimestamp = Long.parseLong(timestampStr);
            } catch (Exception e) {
                continue;
            }

            // Read the JSON array of SteamGameResponse
            List<SteamGameResponse> gameList;
            try {
                gameList = objectMapper.readValue(file, new TypeReference<List<SteamGameResponse>>() {});
            } catch (IOException e) {
                continue;
            }

            // Find the matching appId in that list
            for (SteamGameResponse game : gameList) {
                if (game.getAppId().equals(appId)) {
                    PeakDataPoint pd = new PeakDataPoint(fileTimestamp, game.getPlayerCount());
                    result.add(pd);
                    break;
                }
            }
        }

        // Optionally filter by last N days
        if (days > 0) {
            long cutoff = System.currentTimeMillis() - (days * 86400000L);
            result = result.stream()
                    .filter(pd -> pd.getTimestamp() >= cutoff)
                    .collect(Collectors.toList());
        }

        // Sort ascending by timestamp
        result.sort(Comparator.comparingLong(PeakDataPoint::getTimestamp));

        return result;
    }

    // Simple POJO for returning (timestamp, peak)
    public static class PeakDataPoint {
        private long timestamp;
        private int peak;

        public PeakDataPoint() {}
        public PeakDataPoint(long timestamp, int peak) {
            this.timestamp = timestamp;
            this.peak = peak;
        }
        public long getTimestamp() {
            return timestamp;
        }
        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
        public int getPeak() {
            return peak;
        }
        public void setPeak(int peak) {
            this.peak = peak;
        }
    }

    // ----------------------------------------------------------------
    // 4. Existing: getUserInfo(...)
    // ----------------------------------------------------------------
    public SteamUserResponse getUserInfo(String steamId64) {
        return webClient.get()
                .uri("/ISteamUser/GetPlayerSummaries/v2/?key={key}&steamids={id}", steamApiKey, steamId64)
                .retrieve()
                .bodyToMono(UserResponseWrapper.class)
                .map(wrapper -> wrapper.getResponse().getPlayers().get(0))
                .block();
    }

    // ----------------------------------------------------------------
    // 5. Caching Helper Methods for topGames
    // ----------------------------------------------------------------
    private List<SteamGameResponse> loadTopGamesCache() {
        try {
            File cacheDir = new File(TOP_GAMES_CACHE_DIR);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
                return null;
            }
            File[] files = cacheDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (files == null || files.length == 0) {
                return null;
            }
            // Find the most recent file
            File latestFile = Files.list(Paths.get(TOP_GAMES_CACHE_DIR))
                    .map(path -> path.toFile())
                    .max(Comparator.comparingLong(File::lastModified))
                    .orElse(null);
            if (latestFile == null) {
                return null;
            }
            long age = System.currentTimeMillis() - latestFile.lastModified();
            if (age > TOP_GAMES_CACHE_DURATION) {
                return null;
            }
            return objectMapper.readValue(latestFile, new TypeReference<List<SteamGameResponse>>() {});
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void saveTopGamesCache(List<SteamGameResponse> games) {
        try {
            File cacheDir = new File(TOP_GAMES_CACHE_DIR);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            String filename = "topGames_" + System.currentTimeMillis() + ".json";
            File file = new File(cacheDir, filename);
            objectMapper.writeValue(file, games);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ----------------------------------------------------------------
    // 6. Caching Helper Methods for gameDetails
    // ----------------------------------------------------------------
    private SteamGameDetails loadGameDetailsCache(Long appId) {
        try {
            String dirPath = GAME_DETAILS_CACHE_DIR + appId + "/";
            File cacheDir = new File(dirPath);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
                return null;
            }
            File[] files = cacheDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (files == null || files.length == 0) {
                return null;
            }
            File latestFile = Files.list(Paths.get(dirPath))
                    .map(path -> path.toFile())
                    .max(Comparator.comparingLong(File::lastModified))
                    .orElse(null);
            if (latestFile == null) {
                return null;
            }
            long age = System.currentTimeMillis() - latestFile.lastModified();
            if (age > GAME_DETAILS_CACHE_DURATION) {
                return null;
            }
            return objectMapper.readValue(latestFile, SteamGameDetails.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void saveGameDetailsCache(Long appId, SteamGameDetails details) {
        try {
            String dirPath = GAME_DETAILS_CACHE_DIR + appId + "/";
            File cacheDir = new File(dirPath);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            String filename = "details_" + System.currentTimeMillis() + ".json";
            File file = new File(cacheDir, filename);
            objectMapper.writeValue(file, details);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ----------------------------------------------------------------
    // 7. Response Wrapper Classes
    // ----------------------------------------------------------------
    private static class TopGamesResponse {
        @JsonProperty("response")
        private Response response;
        public Response getResponse() { return response; }
        static class Response {
            @JsonProperty("ranks")
            private List<SteamGameResponse> games;
            public List<SteamGameResponse> getGames() { return games; }
        }
    }

    private static class UserResponseWrapper {
        @JsonProperty("response")
        private PlayerResponse response;
        public PlayerResponse getResponse() { return response; }

        static class PlayerResponse {
            @JsonProperty("players")
            private List<SteamUserResponse> players;
            public List<SteamUserResponse> getPlayers() { return players; }
        }
    }

    /**
     * Get price information for a game by its Steam App ID
     * @param appId The Steam App ID
     * @return The formatted price string or "Price unavailable" if not found
     */
    public String getGamePrice(Long appId) {
        try {
            Map<String, Map<String, Object>> detailsResponse = webClient.get()
                    .uri("https://store.steampowered.com/api/appdetails?appids={appId}&cc=us&filters=price_overview", appId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Map<String, Object>>>() {})
                    .block();

            if (detailsResponse != null && detailsResponse.containsKey(appId.toString())) {
                Map<String, Object> responseData = detailsResponse.get(appId.toString());
                if (responseData.containsKey("data") && responseData.get("data") instanceof Map) {
                    Map<String, Object> gameData = (Map<String, Object>) responseData.get("data");
                    
                    if (gameData.containsKey("price_overview")) {
                        Map<String, Object> priceData = (Map<String, Object>) gameData.get("price_overview");
                        if (priceData != null && priceData.containsKey("final_formatted")) {
                            return (String) priceData.get("final_formatted");
                        }
                    } else if (gameData.containsKey("is_free") && (Boolean) gameData.get("is_free")) {
                        return "Free";
                    }
                }
            }
        } catch (Exception e) {
            // Log the error and return default value
            e.printStackTrace();
        }
        
        return "Price unavailable";
    }
}