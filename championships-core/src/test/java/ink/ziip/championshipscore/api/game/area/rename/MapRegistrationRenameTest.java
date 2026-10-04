package ink.ziip.championshipscore.api.game.area.rename;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class MapRegistrationRenameTest {
    @TempDir Path directory;

    @Test
    void renamesFilenameAndInternalNameAndCanRestoreOriginalBytes() throws Exception {
        Path oldPath = directory.resolve("acerace.yml");
        Path newPath = directory.resolve("clouds.yml");
        String original = "dont-edit-this:\n  version: 18\nname: 王牌竞速\ntimer: 900\n";
        Files.writeString(oldPath, original);

        MapConfigFileRename.State state = MapConfigFileRename.rename(oldPath, newPath, "clouds");

        assertFalse(Files.exists(oldPath));
        assertTrue(Files.isRegularFile(newPath));
        assertEquals(
                "clouds", YamlConfiguration.loadConfiguration(newPath.toFile()).getString("name"));

        MapConfigFileRename.rollback(state);

        assertTrue(Files.isRegularFile(oldPath));
        assertFalse(Files.exists(newPath));
        assertEquals(original, Files.readString(oldPath));
    }

    @Test
    void rewritesBuildMartWorldReferencesAndCanRestoreOriginalBytes() throws Exception {
        Path oldPath = directory.resolve("area.yml");
        Path newPath = directory.resolve("skyline.yml");
        String original =
                "name: area\n"
                        + "world-name: buildmart_area\n"
                        + "portal: buildmart_area:10:64:10:0:0\n"
                        + "serialized:\n"
                        + "  world: buildmart_area\n"
                        + "  world_key: minecraft:buildmart_area\n";
        Files.writeString(oldPath, original);

        MapConfigFileRename.State state =
                MapConfigFileRename.rename(
                        oldPath, newPath, "skyline", "buildmart_area", "buildmart_skyline");

        YamlConfiguration renamed = YamlConfiguration.loadConfiguration(newPath.toFile());
        assertEquals("skyline", renamed.getString("name"));
        assertEquals("buildmart_skyline", renamed.getString("world-name"));
        assertEquals("buildmart_skyline:10:64:10:0:0", renamed.getString("portal"));
        assertEquals("buildmart_skyline", renamed.getString("serialized.world"));
        assertEquals("minecraft:buildmart_skyline", renamed.getString("serialized.world_key"));

        MapConfigFileRename.rollback(state);

        assertEquals(original, Files.readString(oldPath));
        assertFalse(Files.exists(newPath));
    }

    @Test
    void updatesEveryCaseInsensitiveFormalEventReferenceForTheRenamedGame() throws Exception {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(
                """
                formal-events:
                  TGTTOS:
                    maps: [cod, industry, COD]
                  SkyWars:
                    maps: [area2]
                """);

        assertTrue(
                FormalEventMapRename.replaceRegistrations(
                        configuration, GameTypeEnum.TGTTOS, "CoD", "coastal"));
        assertEquals(
                List.of("coastal", "industry", "coastal"),
                configuration.getStringList("formal-events.TGTTOS.maps"));
        assertEquals(List.of("area2"), configuration.getStringList("formal-events.SkyWars.maps"));
        assertFalse(
                FormalEventMapRename.replaceRegistrations(
                        configuration, GameTypeEnum.TGTTOS, "missing", "unused"));
    }

    @Test
    void onlyAnUnsharedDefaultWorldFollowsTheMapRegistration() {
        assertTrue(BuildMartWorldRename.ownsDefaultWorld("area", "buildmart_area", List.of()));
        assertFalse(
                BuildMartWorldRename.ownsDefaultWorld(
                        "area", "buildmart_area", List.of("buildmart_area")));
        assertFalse(BuildMartWorldRename.ownsDefaultWorld("area", "buildmart_shared", List.of()));
    }
}
