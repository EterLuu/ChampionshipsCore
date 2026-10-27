package ink.ziip.championshipscore.api.game.tntrun.runtime;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;

import java.util.*;

class TNTRunFloorTrackerTest {
    @Test
    void sixtyFourPlayersUseOneTaskAndDisappearEightTicksAfterSampling() {
        var f = new Fixture();
        for (int i = 0; i < 64; i++) f.add(i % 8 + .5, 70, i / 8 + .5);
        f.start();
        f.step();
        assertEquals(192, f.reads, "One air, floor and support probe per player at block centres");
        assertEquals(1, f.schedules);
        f.advance(7);
        assertTrue(f.changes.isEmpty());
        f.step();
        assertEquals(128, f.changes.size());
        assertEquals(64, f.sounds);
        assertEquals(Set.of(9), new HashSet<>(f.removalTicks.values()));
        assertEquals(1, f.schedules, "No per-block tasks or background jobs");
    }

    @Test
    void repeatedStandingFootstepsDoNotExtendDeadlineAndPlayersShareRemoval() {
        var f = new Fixture();
        f.add(.5, 70, .5);
        f.add(.5, 70, .5);
        f.start();
        f.advance(18);
        assertEquals(2, f.changes.size());
        assertEquals(1, f.sounds);
        assertEquals(Set.of(9), new HashSet<>(f.removalTicks.values()));
    }

    @Test
    void movingPlayersKeepExactDeadlinesThroughRepeatedBucketWraps() throws Exception {
        var f = new Fixture();
        var people = new ArrayList<Person>();
        for (int i = 0; i < 64; i++) people.add(f.add(i % 8 + .5, 70, i / 8 + .5));
        f.start();
        for (int tick = 1; tick <= 200; tick++) {
            for (int i = 0; i < 64; i++) people.get(i).x = tick * 8 + i % 8 + .5;
            f.step();
            assertEquals(Math.max(0, tick - 8) * 128, f.changes.size());
        }
        f.removalTicks.forEach((cell, tick) -> assertEquals(cell.x / 8 + 8, tick));
        assertEquals(
                512,
                ((Map<?, ?>) field(TNTRunFloorTracker.class, "pending").get(f.tracker)).size());
        f.tracker.close();
        assertTrue(
                ((Map<?, ?>) field(TNTRunFloorTracker.class, "pending").get(f.tracker)).isEmpty());
        assertTrue(
                ((List<?>) field(TNTRunFloorTracker.class, "due").get(f.tracker))
                        .stream().allMatch(bucket -> ((Collection<?>) bucket).isEmpty()));
    }

    @Test
    void spectatorsDisconnectsAndWorldChangesCannotCommitOldFootsteps() {
        for (String change : List.of("spectator", "offline", "connection", "world")) {
            var f = new Fixture();
            var p = f.add(.5, 70, .5);
            f.start();
            f.step();
            f.participants.clear();
            switch (change) {
                case "spectator" -> p.mode = GameMode.SPECTATOR;
                case "offline" -> p.online = false;
                case "connection" -> f.connections.remove(p.id);
                case "world" -> p.world = UUID.randomUUID();
            }
            f.advance(8);
            assertTrue(f.changes.isEmpty(), change);
        }
    }

    @Test
    void replacementConnectionStartsItsOwnEightTickWindow() {
        var f = new Fixture();
        var person = f.add(.5, 70, .5);
        f.start();
        f.advance(4);
        f.participants.clear();
        f.connect(person);
        f.advance(8);
        assertTrue(f.changes.isEmpty());
        f.step();
        assertEquals(2, f.changes.size());
        assertEquals(Set.of(13), new HashSet<>(f.removalTicks.values()));
    }

    @Test
    void laterEligibleContributorKeepsItsOwnEightTickDeadline() {
        var f = new Fixture();
        var first = f.add(.5, 70, .5);
        f.start();
        f.advance(4);
        first.mode = GameMode.SPECTATOR;
        f.add(.5, 70, .5);
        f.advance(8);
        assertTrue(f.changes.isEmpty());
        f.step();
        assertEquals(2, f.changes.size());
    }

    @Test
    void closedChangedGenerationAndDisabledPluginCannotReadOrRemoveTerrain() {
        for (String state : List.of("close", "generation", "disable")) {
            var f = new Fixture();
            f.add(.5, 70, .5);
            f.start();
            f.step();
            switch (state) {
                case "close" -> f.tracker.close();
                case "generation" -> f.current = false;
                case "disable" -> f.enabled = false;
            }
            f.advance(9);
            assertTrue(f.changes.isEmpty(), state);
            assertEquals(1, f.locationReads);
            assertTrue(f.cancellations > 0);
        }
    }

    @Test
    void oldSamplerCannotConsumeNewTrackersRemovalAtSameCoordinates() {
        var f = new Fixture();
        f.add(.5, 70, .5);
        f.start();
        f.step();
        Runnable old = f.poll;
        f.tracker.close();
        f.start();
        for (int i = 0; i < 8; i++) {
            old.run();
            f.step();
        }
        assertTrue(f.changes.isEmpty());
        f.step();
        assertEquals(2, f.changes.size());
    }

    @Test
    void changedTerrainAndUnloadedChunksAreNotRemoved() {
        for (String change : List.of("floor", "support", "unload")) {
            var f = new Fixture();
            f.add(.5, 70, .5);
            f.start();
            f.step();
            f.participants.clear();
            switch (change) {
                case "floor" -> f.blocks.put(new Cell(0, 69, 0), Material.STONE);
                case "support" -> f.blocks.put(new Cell(0, 68, 0), Material.STONE);
                case "unload" -> f.loaded = false;
            }
            f.advance(8);
            assertEquals(change.equals("support") ? 1 : 0, f.changes.size(), change);
        }
        var f = new Fixture();
        f.add(.5, 70, .5);
        f.loaded = false;
        f.start();
        f.advance(9);
        assertEquals(0, f.reads);
        assertTrue(f.changes.isEmpty());
    }

    @Test
    void replacedFloorGetsNewDeadlineWithoutOldBucketConsumingIt() {
        var f = new Fixture();
        f.add(.5, 70, .5);
        f.start();
        f.advance(4);
        f.blocks.put(new Cell(0, 69, 0), Material.STONE);
        f.advance(8);
        assertTrue(f.changes.isEmpty());
        f.step();
        assertEquals(2, f.changes.size());
        assertEquals(Set.of(13), new HashSet<>(f.removalTicks.values()));
    }

    @Test
    void negativeChunkEdgesAndLightKeepOriginalCornerOrder() {
        var f = new Fixture();
        f.add(0, 70, 0);
        f.blocks.put(new Cell(0, 70, -1), Material.LIGHT);
        f.blocks.put(new Cell(-1, 70, 0), Material.STONE);
        f.blocks.put(new Cell(0, 70, 0), Material.STONE);
        f.start();
        f.step();
        f.participants.clear();
        f.advance(8);
        assertEquals(List.of(new Cell(-1, 70, 0), new Cell(-1, 69, 0)), f.changes);
    }

    @Test
    void heightBoundariesNeverReadOrDeleteOutsideWorld() {
        var f = new Fixture();
        f.add(.5, -64, .5);
        f.blocks.put(new Cell(0, -64, 0), Material.STONE);
        f.start();
        f.advance(9);
        assertEquals(List.of(new Cell(0, -64, 0)), f.changes);
        f = new Fixture();
        f.add(.5, 321, .5);
        f.add(.5, -65, .5);
        f.start();
        f.advance(9);
        assertEquals(0, f.reads);
        assertTrue(f.changes.isEmpty());
    }

    record Cell(int x, int y, int z) {}

    static final class Person {
        final UUID id = UUID.randomUUID();
        UUID world;
        double x, y, z;
        GameMode mode = GameMode.ADVENTURE;
        boolean online = true;
        Player player;
    }

    static final class Fixture {
        final UUID worldId = UUID.randomUUID();
        final Map<UUID, Player> connections = new HashMap<>();
        final List<Player> participants = new ArrayList<>();
        final Map<Cell, Material> blocks = new HashMap<>();
        final List<Cell> changes = new ArrayList<>();
        final Map<Cell, Integer> removalTicks = new LinkedHashMap<>();
        final World world;
        final Server server;
        final Plugin plugin;
        final BukkitScheduler scheduler;
        final Thread owner = Thread.currentThread();
        Runnable poll;
        TNTRunFloorTracker tracker;
        boolean current = true, enabled = true, loaded = true;
        int tick, reads, locationReads, sounds, schedules, cancellations;

        Fixture() {
            world =
                    proxy(
                            World.class,
                            (p, m, a) -> {
                                assertSame(owner, Thread.currentThread());
                                return switch (m.getName()) {
                                    case "getUID" -> worldId;
                                    case "getMinHeight" -> -64;
                                    case "getMaxHeight" -> 320;
                                    case "isChunkLoaded" -> loaded;
                                    case "getBlockAt" -> {
                                        assertTrue(loaded, "Must not load chunks for detection");
                                        assertTrue((int) a[1] >= -64 && (int) a[1] < 320);
                                        reads++;
                                        yield block(new Cell((int) a[0], (int) a[1], (int) a[2]));
                                    }
                                    case "playSound" -> {
                                        sounds++;
                                        yield null;
                                    }
                                    default ->
                                            throw new AssertionError(
                                                    "Unexpected world operation: " + m.getName());
                                };
                            });
            BukkitTask task =
                    proxy(
                            BukkitTask.class,
                            (p, m, a) -> {
                                if (m.getName().equals("cancel")) {
                                    cancellations++;
                                    return null;
                                }
                                throw new AssertionError(m.getName());
                            });
            scheduler =
                    proxy(
                            BukkitScheduler.class,
                            (p, m, a) -> {
                                assertEquals(
                                        "runTaskTimer",
                                        m.getName(),
                                        "Only one synchronous sampler is needed");
                                assertSame(owner, Thread.currentThread());
                                assertEquals(0L, a[2]);
                                assertEquals(1L, a[3]);
                                schedules++;
                                poll = (Runnable) a[1];
                                return task;
                            });
            server =
                    proxy(
                            Server.class,
                            (p, m, a) -> {
                                assertSame(owner, Thread.currentThread());
                                return switch (m.getName()) {
                                    case "getScheduler" -> scheduler;
                                    case "getPlayer" -> connections.get(a[0]);
                                    case "getWorld" -> worldId.equals(a[0]) ? world : null;
                                    default -> throw new AssertionError(m.getName());
                                };
                            });
            plugin =
                    proxy(
                            Plugin.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getServer" -> server;
                                        case "isEnabled" -> enabled;
                                        default -> throw new AssertionError(m.getName());
                                    });
        }

        Person add(double x, double y, double z) {
            Person person = new Person();
            person.world = worldId;
            person.x = x;
            person.y = y;
            person.z = z;
            connect(person);
            return person;
        }

        void connect(Person person) {
            person.player =
                    proxy(
                            Player.class,
                            (p, m, a) -> {
                                assertSame(owner, Thread.currentThread());
                                return switch (m.getName()) {
                                    case "getUniqueId" -> person.id;
                                    case "isOnline" -> person.online;
                                    case "getGameMode" -> person.mode;
                                    case "getWorld" ->
                                            person.world.equals(worldId)
                                                    ? world
                                                    : proxy(
                                                            World.class,
                                                            (w, method, args) ->
                                                                    method.getName()
                                                                                    .equals(
                                                                                            "getUID")
                                                                            ? person.world
                                                                            : null);
                                    case "getLocation" -> {
                                        locationReads++;
                                        yield new Location(world, person.x, person.y, person.z);
                                    }
                                    default -> throw new AssertionError(m.getName());
                                };
                            });
            connections.put(person.id, person.player);
            participants.add(person.player);
        }

        private Block block(Cell cell) {
            return proxy(
                    Block.class,
                    (p, m, a) -> {
                        assertSame(owner, Thread.currentThread());
                        return switch (m.getName()) {
                            case "getX" -> cell.x;
                            case "getY" -> cell.y;
                            case "getZ" -> cell.z;
                            case "getType" ->
                                    blocks.getOrDefault(
                                            cell,
                                            cell.y == 69
                                                    ? Material.SANDSTONE
                                                    : cell.y == 68 ? Material.TNT : Material.AIR);
                            case "setType" -> {
                                assertEquals(1, a.length, "Keep normal physics updates");
                                blocks.put(cell, (Material) a[0]);
                                changes.add(cell);
                                removalTicks.put(cell, tick);
                                yield null;
                            }
                            default -> throw new AssertionError(m.getName());
                        };
                    });
        }

        void start() {
            tracker =
                    new TNTRunFloorTracker(
                            plugin,
                            () -> List.copyOf(participants),
                            p -> p.getGameMode() != GameMode.SPECTATOR,
                            () -> current);
            tracker.start();
        }

        void step() {
            tick++;
            poll.run();
        }

        void advance(int ticks) {
            for (int i = 0; i < ticks; i++) step();
        }
    }
}
