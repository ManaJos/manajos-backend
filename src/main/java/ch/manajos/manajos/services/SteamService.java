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
        return Objects.requireNonNull(webClient.get()
                        .uri("/ISteamChartsService/GetMostPlayedGames/v1/")
                        .retrieve()
                        .bodyToMono(TopGamesResponse.class)
                        .block())
                .getResponse()
                .getGames();
    }

    @Cacheable(value = "gameDetails", key = "#appId")
    public SteamGameDetails getGameDetails(Long appId) {
        return webClient.get()
                .uri("https://store.steampowered.com/api/appdetails?appids={appId}", appId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, SteamGameDetailsResponse>>() {})
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