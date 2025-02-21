package ch.manajos.manajos.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class JsonCacheService {
    private static final String CACHE_FILE_PATH = "topGamesData.json";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> loadCache() {
        try {
            File file = new File(CACHE_FILE_PATH);
            if (file.exists()) {
                return objectMapper.readValue(file, Map.class);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new HashMap<>();
    }

    public void saveCache(Map<String, Object> data) {
        try {
            objectMapper.writeValue(new File(CACHE_FILE_PATH), data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void updateGameDetailsCache(Integer appId, Object gameDetails) {
        Map<String, Object> cache = loadCache();
        Map<String, Object> gameDetailsCache = (Map<String, Object>) cache.getOrDefault("gameDetails", new HashMap<>());

        Map<String, Object> gameData = new HashMap<>();
        gameData.put("data", gameDetails);
        gameData.put("timestamp", Instant.now().getEpochSecond()); // Stocke le temps en secondes

        gameDetailsCache.put(appId.toString(), gameData);
        cache.put("gameDetails", gameDetailsCache);

        saveCache(cache);
    }

    public Object getGameDetailsFromCache(Integer appId) {
        Map<String, Object> cache = loadCache();
        Map<String, Object> gameDetailsCache = (Map<String, Object>) cache.get("gameDetails");

        if (gameDetailsCache != null && gameDetailsCache.containsKey(appId.toString())) {
            Map<String, Object> gameData = (Map<String, Object>) gameDetailsCache.get(appId.toString());

            long timestamp = (long) gameData.get("timestamp");
            long currentTime = Instant.now().getEpochSecond();

            if (currentTime - timestamp <= 3600) { // Moins d'1 heure
                return gameData.get("data");
            }
        }

        return null;
    }
    // Méthode pour obtenir l'ObjectMapper
    public ObjectMapper getObjectMapper() {
        return this.objectMapper;
    }
}
