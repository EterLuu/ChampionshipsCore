package ink.ziip.championshipscore.api.event;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ink.ziip.championshipscore.api.rank.ChampionshipArchiveSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChampionshipResultsExporterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesWebCompatibleResultsJsonToTheExportsDirectory() throws Exception {
        ChampionshipArchiveSnapshot.GameScore game =
                new ChampionshipArchiveSnapshot.GameScore("Bingo", "宾果", "Bingo", 1, 123.5D);
        ChampionshipArchiveSnapshot snapshot = new ChampionshipArchiveSnapshot(
                List.of(new ChampionshipArchiveSnapshot.TeamScore("红队", 1, 123.5D, List.of(game))),
                List.of(new ChampionshipArchiveSnapshot.PlayerScore(
                        "PlayerOne", "红队", 123.5D, false, List.of(game))));

        Path exported = ChampionshipResultsExporter.export(temporaryDirectory, "s4cc", snapshot);

        assertEquals(temporaryDirectory.resolve("exports/s4cc-results.json").toAbsolutePath(), exported);
        JsonObject json = JsonParser.parseString(Files.readString(exported)).getAsJsonObject();
        assertEquals("红队", json.getAsJsonArray("teams").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("PlayerOne", json.getAsJsonArray("players").get(0).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void rejectsUnsafeEventSlugs() {
        ChampionshipArchiveSnapshot snapshot = new ChampionshipArchiveSnapshot(List.of(), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> ChampionshipResultsExporter.export(temporaryDirectory, "../scores", snapshot));
    }
}
