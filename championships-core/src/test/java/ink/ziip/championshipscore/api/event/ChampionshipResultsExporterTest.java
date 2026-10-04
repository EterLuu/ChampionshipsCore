package ink.ziip.championshipscore.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.rank.ChampionshipArchiveSnapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class ChampionshipResultsExporterTest {
    @TempDir Path temporaryDirectory;

    @Test
    void writesWebCompatibleResultsJsonToTheExportsDirectory() throws Exception {
        ChampionshipArchiveSnapshot.GameScore game =
                new ChampionshipArchiveSnapshot.GameScore("Bingo", "宾果", "Bingo", 1, 123.5D);
        ChampionshipArchiveSnapshot snapshot =
                new ChampionshipArchiveSnapshot(
                        List.of(
                                new ChampionshipArchiveSnapshot.TeamScore(
                                        "红队", 1, 123.5D, List.of(game))),
                        List.of(
                                new ChampionshipArchiveSnapshot.PlayerScore(
                                        "PlayerOne",
                                        "00000000-0000-4000-8000-000000000001",
                                        "红队",
                                        123.5D,
                                        false,
                                        List.of(game))));

        Path exported =
                ChampionshipResultsExporter.export(
                        temporaryDirectory, event("s4cc"), snapshot, "红队");

        assertEquals(
                temporaryDirectory.resolve("exports/s4cc-results.json").toAbsolutePath(), exported);
        JsonObject json = JsonParser.parseString(Files.readString(exported)).getAsJsonObject();
        assertEquals(
                "红队",
                json.getAsJsonArray("teams").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(
                "PlayerOne",
                json.getAsJsonArray("players").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(
                "00000000-0000-4000-8000-000000000001",
                json.getAsJsonArray("players").get(0).getAsJsonObject().get("uuid").getAsString());
        assertEquals("红队", json.get("championTeamName").getAsString());
        assertEquals("s4cc", json.get("eventSlug").getAsString());
        assertEquals(
                "s3cc",
                json.getAsJsonArray("teams")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonArray("gameScores")
                        .get(0)
                        .getAsJsonObject()
                        .get("variantKey")
                        .getAsString());
    }

    @Test
    void rejectsUnsafeEventSlugs() {
        ChampionshipArchiveSnapshot snapshot =
                new ChampionshipArchiveSnapshot(List.of(), List.of());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ChampionshipResultsExporter.export(
                                temporaryDirectory, event("../scores"), snapshot, null));
    }

    @Test
    void refusesAnUnknownChampionInsteadOfUsingThePointsLeader() {
        ChampionshipArchiveSnapshot snapshot =
                new ChampionshipArchiveSnapshot(List.of(), List.of());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ChampionshipResultsExporter.export(
                                temporaryDirectory, event("s4cc"), snapshot, "Unknown"));
    }

    private static EventStateStore.ActiveEvent event(String slug) {
        return new EventStateStore.ActiveEvent(
                "00000000-0000-4000-8000-000000000002",
                slug,
                "Test",
                false,
                List.of(new EventStateStore.EventGame(GameTypeEnum.Bingo, "s3cc", "Bingo")),
                List.of(1D));
    }
}
