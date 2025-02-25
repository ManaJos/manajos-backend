package ch.manajos.manajos.utils;

import ch.manajos.manajos.dto.SteamGameResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TestDataGenerator {
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // We'll generate fake topGames data for the last 7 days
        // focusing on just appId=730, but you can add more if you want
        long now = System.currentTimeMillis();
        long oneDay = 86400000L; // ms in one day

        for (int i = 1; i <= 7; i++) {
            // Create a list with one fake game: CS2
            List<SteamGameResponse> fakeList = new ArrayList<>();

            SteamGameResponse cs = new SteamGameResponse();
            cs.setAppId(730L);
            cs.setName("Counter-Strike 2 (Fake Data Day " + i + ")");
            cs.setImage("Fake Image URL");
            cs.setRank(1);
            // Just pick some peak_in_game value that changes day to day
            cs.setPlayerCount(1000000 + i * 10000);

            fakeList.add(cs);

            // We'll name the file based on a timestamp i days in the past
            long fileTimestamp = now - (oneDay * i);
            String filename = "src/main/resources/cache/topGames/topGames_"
                    + fileTimestamp + ".json";

            File f = new File(filename);
            mapper.writeValue(f, fakeList);

            // Set the file's lastModified time to the same "i days ago"
            f.setLastModified(fileTimestamp);

            System.out.println("Created " + filename + " with lastModified " + fileTimestamp);
        }
    }
}
