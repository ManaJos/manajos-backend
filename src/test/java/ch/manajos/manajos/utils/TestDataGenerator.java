package ch.manajos.manajos.utils;

import ch.manajos.manajos.dto.SteamGameResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates fake "topGames_TIMESTAMP.json" files in
 * "src/main/resources/cache/topGames/" for the last 7 days.
 * Each file has multiple SteamGameResponse objects, representing
 * a snapshot of "top" games on that day.
 */
public class TestDataGenerator {
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // We'll generate fake topGames data for the last 7 days
        // using a small set of example games.
        long now = System.currentTimeMillis();
        long oneDay = 86400000L;

        // Make sure the directory exists
        File cacheDir = new File("src/main/resources/cache/topGames/");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        for (int i = 1; i <= 60; i++) {
            // Create a list of fake "top" games
            List<SteamGameResponse> fakeList = new ArrayList<>();

            // 1) Counter-Strike 2
            SteamGameResponse cs2 = new SteamGameResponse();
            cs2.setAppId(730L);
            cs2.setName("Counter-Strike 2 (Fake Data Day " + i + ")");
            cs2.setImage("Fake Image URL for CS2");
            cs2.setRank(1);
            cs2.setPlayerCount(1_000_000 + i * 10_000);
            fakeList.add(cs2);

            // 2) Dota 2
            SteamGameResponse dota2 = new SteamGameResponse();
            dota2.setAppId(570L);
            dota2.setName("Dota 2 (Fake Data Day " + i + ")");
            dota2.setImage("Fake Image URL for Dota 2");
            dota2.setRank(2);
            dota2.setPlayerCount(500_000 + i * 5_000);
            fakeList.add(dota2);

            // 3) PUBG
            SteamGameResponse pubg = new SteamGameResponse();
            pubg.setAppId(578080L);
            pubg.setName("PUBG: BATTLEGROUNDS (Fake Data Day " + i + ")");
            pubg.setImage("Fake Image URL for PUBG");
            pubg.setRank(3);
            pubg.setPlayerCount(300_000 + i * 3_000);
            fakeList.add(pubg);

            // Optionally add more games here if you want

            // We'll name the file based on a timestamp i days in the past
            long fileTimestamp = now - (oneDay * i);
            String filename = "src/main/resources/cache/topGames/topGames_" + fileTimestamp + ".json";

            // Write the JSON array of SteamGameResponse
            File f = new File(filename);
            mapper.writeValue(f, fakeList);

            // Set the file's lastModified time so it lines up with the "day"
            f.setLastModified(fileTimestamp);

            System.out.println("Created " + filename + " with lastModified=" + fileTimestamp);
        }
    }
}
