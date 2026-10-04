package ink.ziip.championshipscore.api.game.area.prepare.buildmart;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class BuildMartBlueprintWorkshopTest {
    @TempDir Path directory;

    @Test
    void acceptedEditPreservesMetadataAndReplacesOnlyBlueprintFields() throws Exception {
        Path file = directory.resolve("existing.yml");
        Files.writeString(file, "name: 原建筑\nstars: 3\ncustom: keep\nblocks: [old]\n");
        BuildMartBlueprintWorkshop.save(
                file,
                Files.readAllBytes(file),
                "原建筑",
                3,
                List.of("0,0,0=minecraft:stone"),
                () -> false);
        var yaml = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals("keep", yaml.getString("custom"));
        assertEquals("原建筑", yaml.getString("name"));
        assertEquals(3, yaml.getInt("stars"));
        assertEquals(List.of("0,0,0=minecraft:stone"), yaml.getStringList("blocks"));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void concurrentEditAndNameCollisionCannotOverwriteAnotherSubmission() throws Exception {
        MessageConfig.BUILD_MART_EDITOR_CONFLICT = "conflict";
        Path file = directory.resolve("new.yml");
        BuildMartBlueprintWorkshop.save(file, null, "新建筑", 1, List.of("new"), () -> false);
        byte[] first = Files.readAllBytes(file);
        assertThrows(
                IOException.class,
                () ->
                        BuildMartBlueprintWorkshop.save(
                                file, null, "other", 5, List.of("other"), () -> false));
        assertArrayEquals(first, Files.readAllBytes(file));
        Files.writeString(file, "name: external\n");
        assertThrows(
                IOException.class,
                () ->
                        BuildMartBlueprintWorkshop.save(
                                file, first, "stale", 3, List.of("stale"), () -> false));
        assertEquals("name: external\n", Files.readString(file));
    }

    @Test
    void cancelledSessionDoesNotWriteAndCleansAnyPendingTemporaryFile() throws Exception {
        MessageConfig.BUILD_MART_EDITOR_CANCELLED = "cancelled";
        Path file = directory.resolve("cancelled.yml");
        assertThrows(
                IOException.class,
                () ->
                        BuildMartBlueprintWorkshop.save(
                                file, null, "unused", 1, List.of("unused"), () -> true));
        int[] checks = {0};
        assertThrows(
                IOException.class,
                () ->
                        BuildMartBlueprintWorkshop.save(
                                file, null, "unused", 1, List.of("unused"), () -> ++checks[0] > 1));
        assertFalse(Files.exists(file));
        try (var files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void namesCannotEscapeTheBlueprintDirectory() {
        assertTrue(BuildMartBlueprintWorkshop.validName("风车 2"));
        assertTrue(BuildMartBlueprintWorkshop.validName("船-新"));
        for (String value :
                List.of(
                        "",
                        " ",
                        "../other",
                        "a/b",
                        "a\\b",
                        ".hidden",
                        " name",
                        "name ",
                        "a\nb",
                        "a".repeat(65)))
            assertFalse(BuildMartBlueprintWorkshop.validName(value), value);
        assertFalse(BuildMartBlueprintWorkshop.validName(null));
    }
}
