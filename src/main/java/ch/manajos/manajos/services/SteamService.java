package ch.manajos.manajos.services;

import ch.manajos.manajos.dto.SteamGameDetails;
import ch.manajos.manajos.dto.SteamGameDetailsResponse;
import ch.manajos.manajos.dto.SteamGameResponse;
import ch.manajos.manajos.dto.SteamUserResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        // 1️⃣ Charger le cache depuis le fichier JSON
        Map<String, Object> cache = jsonCacheService.loadCache();
        if (cache.containsKey("topGames")) {
            return (List<SteamGameResponse>) cache.get("topGames");
        }

        // 2️⃣ Si cache vide, appel API Steam
        List<SteamGameResponse> games = fetchTopGamesFromSteam();

        // 3️⃣ Enregistrer les résultats dans le cache JSON
        cache.put("topGames", games);
        jsonCacheService.saveCache(cache);

        return games;
    }

    // ✅ Méthode pour récupérer les jeux depuis Steam
    private List<SteamGameResponse> fetchTopGamesFromSteam() {
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

        return games != null ? games : Collections.emptyList();
    }


    private List<SteamGameResponse> parseTopGames(List<Map<String, Object>> ranks) {
        return ranks.stream().map(entry -> {
            SteamGameResponse game = new SteamGameResponse();

            // Vérification et conversion propre d'Integer vers Long
            Object appIdObj = entry.get("appid");
            game.setAppId((appIdObj instanceof Integer) ? (int) ((Integer) appIdObj) : (Integer) appIdObj);

            // Conversion sécurisée des valeurs numériques
            game.setPlayerCount((Integer) ((Integer) entry.get("peak_in_game")));
            game.setRank((Integer) ((Number) entry.get("rank")).intValue());

            return game;
        }).collect(Collectors.toList());
    }


    @Scheduled(fixedRate = 3600000) // 1 heure = 3600000 ms
    public void updateCache() {
        System.out.println("Mise à jour du cache...");
        List<SteamGameResponse> updatedGames = fetchTopGamesFromSteam();

        // Sauvegarde dans le fichier JSON
        Map<String, Object> cache = new HashMap<>();
        cache.put("topGames", updatedGames);
        jsonCacheService.saveCache(cache);

        System.out.println("Cache mis à jour !");
    }

    public SteamGameDetails getGameDetails(Integer appId) {
        // 1️⃣ Vérifier si les données sont en cache
        Object cachedData = jsonCacheService.getGameDetailsFromCache(appId);
        if (cachedData != null) {
            return (SteamGameDetails) cachedData;
        }

        // 2️⃣ Sinon, appeler l'API Steam
        SteamGameDetails details = fetchGameDetailsFromSteam(appId);

        // 3️⃣ Stocker dans le cache
        jsonCacheService.updateGameDetailsCache(appId, details);

        return details;
    }

    private SteamGameDetails fetchGameDetailsFromSteam(Integer appId) {
        Map<String, Map<String, Object>> detailsResponse = webClient.get()
                .uri("https://store.steampowered.com/api/appdetails?appids={appId}", appId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Map<String, Object>>>() {
                })
                .block();

        if (detailsResponse != null && detailsResponse.containsKey(appId.toString())) {
            Object gameDataObj = detailsResponse.get(appId.toString()).get("data");

            if (gameDataObj instanceof Map) {
                Map<String, Object> gameData = (Map<String, Object>) gameDataObj;

                // Conversion propre de steam_appid
                Object appIdObj = gameData.get("steam_appid");
                Integer safeAppId = (appIdObj instanceof Integer) ? ((Integer) appIdObj) : (Integer) appIdObj;
                gameData.put("steam_appid", safeAppId);

                return jsonCacheService.getObjectMapper().convertValue(gameData, SteamGameDetails.class);
            }
        }
        return null;

    }

    // 🔄 Mise à jour automatique des détails de jeux populaires
    @Scheduled(fixedRate = 3600000) // Toutes les heures
    public void updateGameDetailsCache() {
        System.out.println("Mise à jour du cache des détails de jeux...");

        List<SteamGameResponse> topGames = getTopGames();
        for (SteamGameResponse game : topGames) {
            SteamGameDetails details = fetchGameDetailsFromSteam(game.getAppId());
            jsonCacheService.updateGameDetailsCache(game.getAppId(), details);
        }

        System.out.println("Cache des détails de jeux mis à jour !");
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