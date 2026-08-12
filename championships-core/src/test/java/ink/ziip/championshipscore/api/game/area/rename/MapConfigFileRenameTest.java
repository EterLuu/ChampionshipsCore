package ink.ziip.championshipscore.api.game.area.rename;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapConfigFileRenameTest {
    @TempDir
    Path directory;

    @Test
    void renamesFilenameAndInternalNameAndCanRestoreOriginalBytes() throws Exception {
        Path oldPath = directory.resolve("acerace.yml");
        Path newPath = directory.resolve("clouds.yml");
        String original = "dont-edit-this:\n  version: 18\nname: 王牌竞速\ntimer: 900\n";
        Files.writeString(oldPath, original);

        MapConfigFileRename.State state = MapConfigFileRename.rename(oldPath, newPath, "clouds");

        assertFalse(Files.exists(oldPath));
        assertTrue(Files.isRegularFile(newPath));
        assertEquals("clouds", YamlConfiguration.loadConfiguration(newPath.toFile()).getString("name"));

        MapConfigFileRename.rollback(state);

        assertTrue(Files.isRegularFile(oldPath));
        assertFalse(Files.exists(newPath));
        assertEquals(original, Files.readString(oldPath));
    }
}
