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

    // We’ll use our own ObjectMapper here for reading the cached files
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
     * Returns a list of (date, peak) data points for the specified game,
     * collected from the topGames cache files of the last N days (default 30).
     *
     * Example usage: GET /api/steam/games/730/peak-history?days=30
     */
    @GetMapping("/games/{appId}/peak-history")
    public List<PeakDataPoint> getPeakHistory(
            @PathVariable("appId") Long appId,
            @RequestParam(value = "days", defaultValue = "30") int days
    ) {
        List<PeakDataPoint> history = new ArrayList<>();

        // 1. Locate the folder where topGames cache files are saved
        File cacheDir = new File("src/main/resources/cache/topGames/");
        if (!cacheDir.exists() || !cacheDir.isDirectory()) {
            return history; // empty if no directory
        }

        // 2. Get all .json files in the topGames cache directory
        File[] files = cacheDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return history; // empty if no cache files
        }

        // 3. Sort files by lastModified ascending
        //    (oldest first, or flip for newest first—up to you)
        List<File> sortedFiles = new ArrayList<>();
        for (File f : files) {
            sortedFiles.add(f);
        }
        sortedFiles.sort(Comparator.comparingLong(File::lastModified));

        // 4. Determine the cutoff time in milliseconds
        long now = System.currentTimeMillis();
        long cutoff = now - (days * 86400000L); // days * ms/day

        // 5. For each file:
        //    - skip if it's older than the cutoff
        //    - parse the JSON array of SteamGameResponse
        //    - find the game with matching appId
        //    - record (date, peak)
        for (File f : sortedFiles) {
            long lastModified = f.lastModified();
            if (lastModified < cutoff) {
                // file is older than "days" days
                continue;
            }

            try {
                // read the entire list of topGames from this file
                List<SteamGameResponse> games = objectMapper.readValue(
                        f,
                        new TypeReference<List<SteamGameResponse>>() {}
                );

                // find the matching game
                games.stream()
                        .filter(g -> g.getAppId().equals(appId))
                        .findFirst()
                        .ifPresent(g -> {
                            // We'll store the file's lastModified time
                            // as a string, or convert it to an ISO date if you prefer
                            PeakDataPoint point = new PeakDataPoint();
                            point.setTimestamp(lastModified); // raw ms
                            point.setPeak(g.getPlayerCount());
                            history.add(point);
                        });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return history;
    }

    /**
     * DTO for returning a single (time, peak) data point in JSON.
     * We store time as a "timestamp" in ms, but you can also store a LocalDate
     * or format it as a String.
     */
    public static class PeakDataPoint {
        private long timestamp;   // e.g. 1698428260912
        private Integer peak;     // e.g. 1613600

        public long getTimestamp() {
            return timestamp;
        }
        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public Integer getPeak() {
            return peak;
        }
        public void setPeak(Integer peak) {
            this.peak = peak;
        }
    }
}
