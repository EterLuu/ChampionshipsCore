package ink.ziip.championshipscore.api.game.tntrun.runtime;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.tntrun.config.TNTRunConfig;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.scheduler.*;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.*;

class TNTRunRainLifecycleTest {
    @Test
    void asyncWaveUsesOnlySnapshotsAndStoppedWaveCannotSpawnEntities() throws Exception {
        try (var f = new Fixture()) {
            f.start();
            f.async();
            assertEquals(1, f.commits.size());
            f.stop();
            f.commits.getFirst().run();
            assertEquals(0, f.spawned);
        }
    }

    @Test
    void oneTrackerOwnsAllEntitiesAndStopCancelsAndRemovesTheWholeWave() throws Exception {
        try (var f = new Fixture()) {
            f.start();
            f.async();
            f.commits.getFirst().run();
            assertEquals(12, f.spawned);
            assertEquals(1, f.trackers.size());
            f.async();
            f.commits.getLast().run();
            assertEquals(24, f.spawned);
            assertEquals(1, f.trackers.size());
            f.trackers.getFirst().run();
            f.stop();
            assertEquals(24, f.removed);
            f.trackers.getFirst().run();
            assertEquals(24, f.removed);
        }
    }

    @Test
    void supersededRainCannotCommitIntoTheReplacementWave() throws Exception {
        try (var f = new Fixture()) {
            f.start();
            f.async();
            Runnable old = f.commits.getFirst();
            f.start();
            f.async();
            old.run();
            assertEquals(0, f.spawned);
            f.commits.getLast().run();
            assertEquals(12, f.spawned);
        }
    }

    private static final class Area extends TNTRunTeamArea {
        Area() {
            super(null, null, false, "test");
        }

        @Override
        public Block getBlockUnderLocation(Location location, double bias) {
            return null;
        }

        @Override
        public boolean notInArea(Location location) {
            return false;
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Object previous = field(Bukkit.class, "server").get(null);
        final Area area = allocate(Area.class);
        final List<Runnable> commits = new ArrayList<>(), trackers = new ArrayList<>();
        Runnable generator;
        boolean asynchronous;
        int spawned, removed;

        Fixture() throws Exception {
            World world =
                    proxy(
                            World.class,
                            (p, m, a) -> {
                                assertFalse(asynchronous, "async planning must not access World");
                                return switch (m.getName()) {
                                    case "getName" -> "fixture";
                                    case "spawnEntity" -> {
                                        spawned++;
                                        Location location = ((Location) a[0]).clone();
                                        yield proxy(
                                                TNTPrimed.class,
                                                (e, method, args) -> {
                                                    assertFalse(
                                                            asynchronous,
                                                            "async planning must not access"
                                                                    + " Entity");
                                                    return switch (method.getName()) {
                                                        case "hashCode" ->
                                                                System.identityHashCode(e);
                                                        case "equals" -> e == args[0];
                                                        case "setFuseTicks" -> null;
                                                        case "isValid" -> true;
                                                        case "getFuseTicks" -> 200;
                                                        case "getLocation" -> location;
                                                        case "remove" -> {
                                                            removed++;
                                                            yield null;
                                                        }
                                                        default ->
                                                                throw new AssertionError(
                                                                        method.getName());
                                                    };
                                                });
                                    }
                                    default -> throw new AssertionError(m.getName());
                                };
                            });
            BukkitTask task =
                    proxy(
                            BukkitTask.class,
                            (p, m, a) -> m.getName().equals("getTaskId") ? 1 : null);
            BukkitScheduler scheduler =
                    proxy(
                            BukkitScheduler.class,
                            (p, m, a) -> {
                                switch (m.getName()) {
                                    case "runTaskTimerAsynchronously" ->
                                            generator = (Runnable) a[1];
                                    case "runTaskTimer" -> trackers.add((Runnable) a[1]);
                                    case "runTask" -> commits.add((Runnable) a[1]);
                                    case "cancelTask" -> {
                                        return null;
                                    }
                                    default -> throw new AssertionError(m.getName());
                                }
                                return task;
                            });
            field(Bukkit.class, "server")
                    .set(
                            null,
                            proxy(
                                    Server.class,
                                    (p, m, a) ->
                                            switch (m.getName()) {
                                                case "getScheduler" -> scheduler;
                                                case "getWorld" -> {
                                                    assertFalse(asynchronous);
                                                    yield world;
                                                }
                                                default -> throw new AssertionError(m.getName());
                                            }));
            var plugin = allocate(ChampionshipsCore.class);
            var config = allocate(TNTRunConfig.class);
            var yaml = new YamlConfiguration();
            yaml.set("world-name", "fixture");
            set(config, "configuration", yaml);
            set(config, "copies", 1);
            set(config, "copySize", new Vector(100, 100, 100));
            set(config, "copyLayoutOrigin", new Vector(-50, 0, -50));
            set(config, "copyLayoutStep", new Vector(192, 0, 0));
            set(config, "copySpawn", new Location(world, 0, 50, 0));
            set(area, "plugin", plugin);
            set(area, "scheduler", scheduler);
            set(area, "gameConfig", config);
            set(area, "gameStageEnum", GameStageEnum.PROGRESS);
            set(area, "rainTnt", new HashSet<>());
        }

        void start() throws Exception {
            invoke("startTntRain");
        }

        void stop() throws Exception {
            invoke("cancelTerrainTasks");
        }

        void invoke(String name) throws Exception {
            var m = TNTRunTeamArea.class.getDeclaredMethod(name);
            m.setAccessible(true);
            m.invoke(area);
        }

        void async() {
            asynchronous = true;
            try {
                generator.run();
            } finally {
                asynchronous = false;
            }
        }

        public void close() throws Exception {
            field(Bukkit.class, "server").set(null, previous);
        }
    }
}
