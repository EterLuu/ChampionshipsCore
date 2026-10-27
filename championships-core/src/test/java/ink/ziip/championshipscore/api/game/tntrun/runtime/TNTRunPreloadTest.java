package ink.ziip.championshipscore.api.game.tntrun.runtime;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.start.ArenaSelection;
import ink.ziip.championshipscore.api.game.tntrun.config.TNTRunConfig;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.*;

class TNTRunPreloadTest {
    @Test
    void everyChunkOfSelectedCopiesIsRequestedWithoutIncludingOtherCopiesOrGaps() throws Exception {
        try (var f = new Fixture()) {
            set(f.area, "arenaSelection", new ArenaSelection(List.of(1, 3)));
            Set<Cell> expected = new HashSet<>();
            for (int copy : List.of(1, 3)) {
                for (int x = -2 + copy * 12; x <= -1 + copy * 12; x++) {
                    for (int z = -2; z <= -1; z++) expected.add(new Cell(x, z));
                }
            }
            var points = f.area.getStartPreloadLocations();
            assertEquals(
                    expected,
                    points.stream()
                            .map(p -> new Cell(p.getBlockX() >> 4, p.getBlockZ() >> 4))
                            .collect(java.util.stream.Collectors.toSet()));
            assertEquals(expected.size(), points.size());
            assertTrue(points.stream().allMatch(p -> p.getWorld() == f.world));
        }
    }

    @Test
    void automaticSelectionCoversAllCopiesAndMissingWorldDoesNotLoadIt() throws Exception {
        try (var f = new Fixture()) {
            assertEquals(16, f.area.getStartPreloadLocations().size());
            f.worldAvailable = false;
            assertTrue(f.area.getStartPreloadLocations().isEmpty());
        }
    }

    record Cell(int x, int z) {}

    static final class Fixture implements AutoCloseable {
        final Object previous = field(Bukkit.class, "server").get(null);
        final TNTRunTeamArea area = allocate(TNTRunTeamArea.class);
        final World world =
                proxy(
                        World.class,
                        (p, m, a) -> {
                            throw new AssertionError(
                                    "Preload planning must not access chunks: " + m.getName());
                        });
        boolean worldAvailable = true;

        Fixture() throws Exception {
            field(Bukkit.class, "server")
                    .set(
                            null,
                            proxy(
                                    Server.class,
                                    (p, m, a) -> {
                                        assertEquals("getWorld", m.getName());
                                        assertEquals("arena", a[0]);
                                        return worldAvailable ? world : null;
                                    }));
            var config = allocate(TNTRunConfig.class);
            var yaml = new YamlConfiguration();
            yaml.set("world-name", "arena");
            set(config, "configuration", yaml);
            config.setCopies(4);
            config.setCopySize(new Vector(32, 100, 32));
            config.setCopyLayoutOrigin(new Vector(-32, 80, -32));
            config.setCopyLayoutStep(new Vector(192, 0, 0));
            set(area, "gameConfig", config);
        }

        public void close() throws Exception {
            field(Bukkit.class, "server").set(null, previous);
        }
    }
}
