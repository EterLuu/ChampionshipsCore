package ink.ziip.championshipscore.api.game.acerace.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.acerace.geometry.AceRaceRespawnBindings;
import ink.ziip.championshipscore.api.game.acerace.mechanics.AceRaceEquipment;
import ink.ziip.championshipscore.api.game.acerace.model.AceRaceCourse;
import ink.ziip.championshipscore.api.game.acerace.model.AceRaceLine;
import ink.ziip.championshipscore.support.TestObjects;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class AceRaceCourseLoaderTest {
    @Test
    void gatesKeepStableOrderDefaultsAndEquipmentWhileInvalidSectionsAreReported()
            throws Exception {
        Fixture f = new Fixture();
        f.gate("later", 8, 80, "elytra");
        f.root.createSection("invalid").set("pos1", new Vector());
        f.root.set("scalar", "ignored");
        f.gate("earlier", 2, 20, "trident").set("fall-y", -10);
        f.gate("same-order", 2, 40, "dolphins_grace");
        f.gate("implicit", 0, 60, "unknown").set("order", null);
        AceRaceCourse course = f.load();
        assertEquals(
                List.of(2, 2, 4, 8), course.progressPoints().stream().map(p -> p.order()).toList());
        assertEquals(
                List.of(-10, -32, -32, -32),
                course.progressPoints().stream().map(p -> p.fallY()).toList());
        assertEquals(
                List.of(
                        AceRaceEquipment.TRIDENT,
                        AceRaceEquipment.DOLPHINS_GRACE,
                        AceRaceEquipment.NONE,
                        AceRaceEquipment.ELYTRA),
                course.progressPoints().stream().map(p -> p.equipment()).toList());
        assertEquals(1, f.warnings.size());
        assertTrue(f.warnings.getFirst().contains("invalid"));
    }

    @Test
    void skippedRowsDoNotShiftExplicitBindingsOrEditorSourceIndexes() throws Exception {
        Fixture f = new Fixture();
        f.gate("first", 1, 20, "none");
        f.gate("second", 2, 60, "none");
        f.config.setRespawnPoints(
                Arrays.asList(
                        "broken",
                        f.position(0),
                        "other:0:70:40:0:0",
                        "missing:0:70:40:0:0",
                        f.position(62),
                        null,
                        "course:NaN:70:0:0:0"));
        f.config.setRespawnProgressPoints(List.of(1, -1, 0, 0, 1, 0, 0));
        AceRaceCourse course = f.load();
        assertEquals(List.of(1, 4), course.respawns().stream().map(m -> m.configIndex()).toList());
        assertEquals(List.of(-1, 1), course.respawns().stream().map(m -> m.binding()).toList());
        assertEquals(0, course.loadedIndexForConfig(1));
        assertEquals(1, course.loadedIndexForConfig(4));
        assertEquals(-1, course.loadedIndexForConfig(2));
        assertEquals(1, course.bindingForConfig(4));
        // Five rejected rows and one gate without a recovery marker.
        assertEquals(6, f.warnings.size());
    }

    @Test
    void absentAndOutOfRangeOverridesUseExistingGeometricInference() throws Exception {
        Fixture f = new Fixture();
        f.gate("first", 1, 20, "none");
        f.gate("second", 2, 60, "none");
        f.config.setRespawnPoints(List.of(f.position(0), f.position(22), f.position(62)));
        f.config.setRespawnProgressPoints(Arrays.asList(-2, null, 999));
        AceRaceCourse course = f.load();
        List<Integer> expected =
                AceRaceRespawnBindings.bindRespawnPoints(
                        course.progressPoints(),
                        course.respawns().stream().map(m -> m.point()).toList(),
                        f.config.getStartSpawnPoint(),
                        new AceRaceLine(f.config.getFinishLinePos1(), f.config.getFinishLinePos2())
                                .center(f.world));
        assertEquals(expected, course.respawns().stream().map(m -> m.binding()).toList());
        assertEquals(List.of(-1, 0, 1), expected);
        assertTrue(f.warnings.isEmpty());
        assertEquals(Arrays.asList(-2, null, 999), f.config.getRespawnProgressPoints());
    }

    @Test
    void explicitBindingCanOverrideGeometryWithoutReorderingMarkers() throws Exception {
        Fixture f = new Fixture();
        f.gate("first", 1, 20, "none");
        f.gate("second", 2, 60, "none");
        f.config.setRespawnPoints(List.of(f.position(62), f.position(22), f.position(0)));
        f.config.setRespawnProgressPoints(List.of(-1, 1, 0));
        AceRaceCourse course = f.load();
        assertEquals(List.of(-1, 1, 0), course.respawns().stream().map(m -> m.binding()).toList());
        assertEquals(
                List.of(62D, 22D, 0D),
                course.respawns().stream().map(m -> m.point().destination().getZ()).toList());
        assertTrue(f.warnings.isEmpty());
    }

    @Test
    void editingAnEmptyCourseDoesNotMutateConfigurationOrInventGates() throws Exception {
        Fixture f = new Fixture();
        assertNull(f.config.getRespawnPoints());
        assertEquals(AceRaceCourse.EMPTY, f.load());
        assertNull(f.config.getRespawnPoints());
        assertNull(f.config.getRespawnProgressPoints());
        f.config.setRespawnPoints(List.of(f.position(0)));
        AceRaceCourse course = f.load();
        assertTrue(course.progressPoints().isEmpty());
        assertEquals(-1, course.respawns().getFirst().binding());
        assertTrue(f.warnings.isEmpty());
    }

    @Test
    void unloadedWorldKeepsFallbackFallHeightAndReportsUnresolvedMarkers() throws Exception {
        Fixture f = new Fixture();
        f.worldLoaded = false;
        f.gate("first", 1, 20, "none");
        f.config.setRespawnPoints(List.of(f.position(0)));
        AceRaceCourse course = f.load();
        assertEquals(-64, course.progressPoints().getFirst().fallY());
        assertTrue(course.respawns().isEmpty());
        assertEquals(1, f.warnings.size());
    }

    @Test
    void priorSnapshotDoesNotChangeWhenConfigurationIsEditedAndReloaded() throws Exception {
        Fixture f = new Fixture();
        ConfigurationSection gate = f.gate("first", 1, 20, "trident");
        f.config.setRespawnPoints(List.of(f.position(22)));
        AceRaceCourse original = f.load();
        gate.set("equipment", "elytra");
        gate.set("pos1", new Vector(-10, 70, 50));
        gate.set("pos2", new Vector(10, 70, 50));
        f.config.setRespawnPoints(List.of(f.position(52)));
        AceRaceCourse replacement = f.load();
        assertEquals(AceRaceEquipment.TRIDENT, original.progressPoints().getFirst().equipment());
        assertEquals(20, original.progressPoints().getFirst().pos1().getZ());
        assertEquals(22, original.respawns().getFirst().point().destination().getZ());
        assertEquals(AceRaceEquipment.ELYTRA, replacement.progressPoints().getFirst().equipment());
        assertEquals(50, replacement.progressPoints().getFirst().pos1().getZ());
        assertEquals(52, replacement.respawns().getFirst().point().destination().getZ());
    }

    @Test
    void runtimeLookupFailureIsNotSilentlyAcceptedAsABadConfigurationRow() throws Exception {
        Fixture f = new Fixture();
        f.gate("first", 1, 20, "none");
        f.config.setRespawnPoints(List.of(f.position(22), "broken", f.position(25)));
        f.failLookupAfter = 2;
        assertThrows(IllegalStateException.class, f::load);
        assertEquals(1, f.warnings.size());
        assertEquals(3, f.config.getRespawnPoints().size());
    }

    private static class Fixture {
        final AceRaceConfig config;
        final ConfigurationSection root = new YamlConfiguration();
        final List<String> warnings = new ArrayList<>();
        final World world = world("course");
        final World other = world("other");
        boolean worldLoaded = true;
        int lookups;
        int failLookupAfter = Integer.MAX_VALUE;
        final Server server =
                TestObjects.proxy(
                        Server.class,
                        (proxy, method, args) -> {
                            if (method.getName().equals("getWorld")) {
                                if (++lookups > failLookupAfter)
                                    throw new IllegalStateException("world lookup failed");
                                return worldLoaded
                                        ? Map.of("course", world, "other", other).get(args[0])
                                        : null;
                            }
                            throw new AssertionError(method.getName());
                        });

        Fixture() throws Exception {
            config = new AceRaceConfig(TestObjects.allocate(ChampionshipsCore.class), "test");
            config.setProgressPoints(root);
            config.setStartSpawnPoint(new Location(world, 0, 70, 0));
            config.setFinishLinePos1(new Vector(-10, 70, 80));
            config.setFinishLinePos2(new Vector(10, 70, 80));
        }

        ConfigurationSection gate(String key, int order, double z, String equipment) {
            ConfigurationSection section = root.createSection(key);
            section.set("order", order);
            section.set("pos1", new Vector(-10, 70, z));
            section.set("pos2", new Vector(10, 70, z));
            section.set("equipment", equipment);
            return section;
        }

        String position(int z) {
            return "course:0:70:" + z + ":0:0";
        }

        AceRaceCourse load() {
            return AceRaceCourseLoader.load(
                    config,
                    "course",
                    server,
                    (action, detail) -> warnings.add(action + ":" + detail));
        }

        static World world(String name) {
            UUID uuid = UUID.randomUUID();
            return TestObjects.proxy(
                    World.class,
                    (proxy, method, args) ->
                            switch (method.getName()) {
                                case "getName" -> name;
                                case "getUID" -> uuid;
                                case "getMinHeight" -> -32;
                                case "equals" -> proxy == args[0];
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "toString" -> name;
                                default -> throw new AssertionError(method.getName());
                            });
        }
    }
}
