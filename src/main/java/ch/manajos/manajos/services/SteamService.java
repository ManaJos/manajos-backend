package ch.manajos.manajos.services;

import ch.manajos.manajos.dto.SteamGameDetails;
import ch.manajos.manajos.dto.SteamGameResponse;
import ch.manajos.manajos.dto.SteamUserResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SteamService {
    private final WebClient webClient;

    @Value("${steam.api.key}")
    private String steamApiKey;

    public SteamService(WebClient webClient) {
        this.webClient = webClient;
    }

    @Cacheable(value = "topGames")
    public List<SteamGameResponse> getTopGames() {
        // 1. Get basic game data
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

        // 2. Enrich with game names
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
                        } else {
                            game.setName("Name unavailable");
                        }
                    }
                } catch (Exception e) {
                    game.setName("Name unavailable");
                }
            });
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

    @Cacheable(value = "gameDetails", key = "#appId")
    public SteamGameDetails getGameDetails(Long appId) {
        return webClient.get()
                .uri("https://store.steampowered.com/api/appdetails?appids={appId}", appId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, GameDetailsResponse>>() {})
                .block()
                .get(appId.toString())
                .getData();
    }

    public SteamUserResponse getUserInfo(String steamId64) {
        return webClient.get()
                .uri("/ISteamUser/GetPlayerSummaries/v2/?key={key}&steamids={id}", steamApiKey, steamId64)
                .retrieve()
                .bodyToMono(UserResponseWrapper.class)
                .map(wrapper -> wrapper.getResponse().getPlayers().get(0))
                .block();
    }

    // Response wrapper classes
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

    private static class GameDetailsResponse {
        @JsonProperty("success")
        private boolean success;
        @JsonProperty("data")
        private SteamGameDetails data;

        public boolean isSuccess() { return success; }
        public SteamGameDetails getData() { return data; }
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