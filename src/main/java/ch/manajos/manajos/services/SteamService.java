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
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    // Duration limits in milliseconds: 24 hour for top games and 2 week for game details
    private static final long TOP_GAMES_CACHE_DURATION = 86400000L; // 24 hour in ms
    private static final long GAME_DETAILS_CACHE_DURATION = 1209600000L; // 2 week in ms

    public SteamService(WebClient webClient) {
        this.webClient = webClient;
    }

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

        // 2. Enrich with game names and images
        if (games != null) {
            games.parallelStream().forEach(game -> {
                try {
                    Map<String, Map<String, Object>> detailsResponse = webClient.get()
                            .uri("https://store.steampowered.com/api/appdetails?appids={appId}", game.getAppId())
                            .retrieve()
                            .bodyToMono(new ParameterizedTypeReference<Map<String, Map<String, Object>>>() {})
                            .block();

                    if (detailsResponse != null && detailsResponse.containsKey(game.getAppId().toString())) {
                        Object dataObject = detailsResponse.get(game.getAppId().toString()).get("data");
                        if (dataObject instanceof Map) {
                            Map<String, Object> gameData = (Map<String, Object>) dataObject;
                            game.setName((String) gameData.get("name"));
                            // Set the header image
                            game.setImage((String) gameData.get("header_image"));
                        } else {
                            game.setName("Name unavailable");
                            game.setImage("Image unavailable");
                        }
                    }
                } catch (Exception e) {
                    game.setName("Name unavailable");
                    game.setImage("Image unavailable");
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

    public SteamUserResponse getUserInfo(String steamId64) {
        return webClient.get()
                .uri("/ISteamUser/GetPlayerSummaries/v2/?key={key}&steamids={id}", steamApiKey, steamId64)
                .retrieve()
                .bodyToMono(UserResponseWrapper.class)
                .map(wrapper -> wrapper.getResponse().getPlayers().get(0))
                .block();
    }

    // --- Caching Helper Methods for topGames ---

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
            // Find the most recent file based on lastModified time
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
            // Use a timestamp in the filename so that previous files remain intact
            String filename = "topGames_" + System.currentTimeMillis() + ".json";
            File file = new File(cacheDir, filename);
            objectMapper.writeValue(file, games);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- Caching Helper Methods for gameDetails ---

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
            // Find the most recent file in the directory
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

    // --- Response Wrapper Classes ---

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
}
