package ink.ziip.championshipscore.api.game.hotycodydusky.mechanics;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

class BasinLootTest {
    @Test
    void tournamentPoolUsesOnlyTheTwelveEnabledDrops() {
        assertEquals(
                Set.of(
                        "MUSHROOM",
                        "CHORUS",
                        "PEARL",
                        "HORN",
                        "TRIDENT",
                        "VEST",
                        "KEY",
                        "COMPASS",
                        "SMOKE",
                        "BAIT",
                        "WIND",
                        "DECOY"),
                BasinLoot.pool(false).stream().map(Enum::name).collect(Collectors.toSet()));
        assertEquals(14, BasinLoot.pool(true).size());
        assertTrue(BasinLoot.pool(true).containsAll(List.of(BasinLoot.CLOCK, BasinLoot.TOTEM)));
        assertEquals(5, BasinLoot.SPAWN_INTERVAL);
        assertEquals(75, BasinLoot.CHANCE_PERCENT);
        assertEquals(10, BasinLoot.LIMIT);
    }

    @Test
    void bundledTemplateIsAnEmptyDraftAndConfiguredOriginsTranslateItemSpawns() throws Exception {
        try (var in = getClass().getResourceAsStream("/hotycodydusky/template.yml")) {
            var yaml =
                    YamlConfiguration.loadConfiguration(
                            new InputStreamReader(
                                    Objects.requireNonNull(in), StandardCharsets.UTF_8));
            assertEquals(4, yaml.getInt("dont-edit-this.version"));
            assertFalse(yaml.getBoolean("prepare.published"));
            assertTrue(yaml.getBoolean("prepare.dirty"));
            assertFalse(yaml.getBoolean("prepare.world-built"));
            assertFalse(yaml.contains("scenery-template"));
            assertTrue(yaml.getList("item-spawns").isEmpty());
        }
        var points = List.of(new Vector(28.5, 3.5, 17.5), new Vector(46.5, 3.5, 51.5));
        var size = new Vector(62, 25, 66);
        var origins =
                List.of(
                        new Vector(-32, 60, -36),
                        new Vector(-32, 60, -182),
                        new Vector(115, 60, -182),
                        new Vector(115, 60, -36));
        Set<Vector> locations = new HashSet<>();
        for (Vector origin : origins) {
            for (Vector offset : points) {
                assertTrue(offset.isInAABB(new Vector(), size));
                assertTrue(locations.add(origin.clone().add(offset)));
            }
        }
        assertEquals(8, locations.size());
        assertTrue(locations.contains(new Vector(-3.5, 63.5, -18.5)));
        assertTrue(locations.contains(new Vector(14.5, 63.5, 15.5)));
    }
}
