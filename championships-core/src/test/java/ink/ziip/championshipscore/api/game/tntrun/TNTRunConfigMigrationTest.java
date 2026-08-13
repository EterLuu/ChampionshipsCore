package ink.ziip.championshipscore.api.game.tntrun;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TNTRunConfigMigrationTest {
    @Test
    void completeCopyLayoutDoesNotInvalidatePublishedMapWithoutLegacyBounds() {
        YamlConfiguration old = publishedCopyMap();
        assertFalse(TNTRunConfig.requiresCopyLayoutRepublish(old));
    }

    @Test
    void incompleteCopyLayoutStillRequiresPrepareRepublish() {
        YamlConfiguration old = publishedCopyMap();
        old.set("copy-layout.step", null);
        assertTrue(TNTRunConfig.requiresCopyLayoutRepublish(old));
    }

    private static YamlConfiguration publishedCopyMap() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("copies", 4);
        configuration.set("prepare.published", true);
        configuration.set("prepare.dirty", false);
        configuration.set("copy-layout.origin", new Vector(0, 100, 0));
        configuration.set("copy-layout.step", new Vector(192, 0, 0));
        configuration.set("copy-size", new Vector(55, 101, 77));
        configuration.set("copy-spawn", "configured");
        return configuration;
    }
}
