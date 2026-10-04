package ink.ziip.championshipscore.api.game.area.prepare;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.laserbox.config.LaserBoxConfig;
import ink.ziip.championshipscore.api.game.laserbox.geometry.LaserBoxGeometry;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.api.game.spatial.ReplicatedSpatialLayout;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.integration.worldedit.WorldEditManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@ExtendWith(ConfigurationStateExtension.class)
class LaserBoxPrepareFlowTest {
    @TempDir Path directory;
    private Object previousServer;
    private LaserBoxConfig config;
    private PrepareSession session;
    private RecordingWorldEdit worldEdit;
    private Player player;
    private World world;
    private boolean canSave;
    private int worldSaves;

    @BeforeEach
    void setup() throws Exception {
        previousServer = field(Bukkit.class, "server").get(null);
        ChampionshipsCore plugin = allocate(ChampionshipsCore.class);
        set(plugin, "dataFolder", directory.toFile());
        set(plugin, "logger", Logger.getAnonymousLogger());
        world =
                proxy(
                        World.class,
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "getName" -> "shared-world";
                                    case "getNearbyEntities" -> List.of();
                                    case "save" -> {
                                        worldSaves++;
                                        yield null;
                                    }
                                    case "equals" -> p == a[0];
                                    case "hashCode" -> System.identityHashCode(p);
                                    default -> throw new AssertionError(m.getName());
                                });
        BukkitScheduler scheduler =
                proxy(
                        BukkitScheduler.class,
                        (p, m, a) -> {
                            if (m.getName().equals("runTask")) {
                                ((Runnable) a[1]).run();
                                return null;
                            }
                            throw new AssertionError(m.getName());
                        });
        Server server =
                proxy(
                        Server.class,
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "getWorld" -> world;
                                    case "getScheduler" -> scheduler;
                                    default -> throw new AssertionError(m.getName());
                                });
        field(Bukkit.class, "server").set(null, server);
        set(plugin, "server", server);
        player =
                proxy(
                        Player.class,
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "getWorld" -> world;
                                    case "teleport" -> true;
                                    default -> throw new AssertionError(m.getName());
                                });
        worldEdit = allocate(RecordingWorldEdit.class);
        worldEdit.selection = new Vector[] {new Vector(70, 20, -40), new Vector(102, 30, -8)};
        worldEdit.size = new Vector(33, 11, 33);
        worldEdit.pasted = new ArrayList<>();
        worldEdit.cleared = new ArrayList<>();
        set(plugin, "worldEditManager", worldEdit);
        config = new LaserBoxConfig(plugin, "map-a");
        set(config, "configuration", new YamlConfiguration());
        Files.createDirectories(directory.resolve("laserbox"));
        set(config, "configurationPath", directory.resolve("laserbox/map-a.yml"));
        config.bindConfiguredWorld("shared-world");
        config.beginPrepareDraft();
        canSave = true;
        SetupTarget target =
                proxy(
                        SetupTarget.class,
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "plugin" -> plugin;
                                    case "config" -> config;
                                    case "name" -> "map-a";
                                    case "worldName" -> "shared-world";
                                    case "canSaveMap" -> canSave;
                                    default -> throw new AssertionError(m.getName());
                                });
        MessageConfig.MAP_EDITOR_STEP_SCHEMATIC_SAVED = "saved %file%";
        MessageConfig.MAP_EDITOR_STEP_SCHEMATIC_SAVE_FAILED = "failed %detail%";
        MessageConfig.MAP_EDITOR_STEP_ARENA_TOTAL_SET = "total %count%, copies %copies%";
        MessageConfig.MAP_EDITOR_STEP_ARENA_TEMPLATE_MISSING = "missing template";
        MessageConfig.MAP_EDITOR_STEP_ARENA_INSTANCE_RUNNING = "instance running";
        MessageConfig.MAP_EDITOR_STEP_ARENA_COUNT_POSITIVE = "positive count required";
        MessageConfig.MAP_EDITOR_STEP_ARENA_GENERATE_FAILED = "failed %detail%";
        session =
                new PrepareSession(
                        plugin, GameTypeEnum.LaserBox, "map-a", target, new LaserBoxPrepareFlow());
    }

    @AfterEach
    void restoreServer() throws Exception {
        field(Bukkit.class, "server").set(null, previousServer);
    }

    @Test
    void capturesMapOwnedTemplateAndGeneratesOnlyAdditionalCopiesWithMatchingGeometry()
            throws Exception {
        session.step("schematic").capture(session, player);
        assertTrue(Files.isRegularFile(directory.resolve("laserbox/schematics/map-a/arena.schem")));
        assertEquals(new Vector(70, 20, -40), config.getAreaPos1());
        assertEquals(new Vector(102, 30, -8), config.getAreaPos2());
        assertFalse(session.step("stamp").isSet(session));

        session.step("stamp").stamp(session, player, 3);

        assertEquals(List.of(new Vector(246, 20, -40), new Vector(422, 20, -40)), worldEdit.pasted);
        assertTrue(worldEdit.cleared.isEmpty());
        assertEquals(3, config.getCopyCount());
        assertTrue(session.isStamped());
        assertTrue(config.isPrepareWorldBuilt());
        assertTrue(config.isPrepareDirty());
        YamlConfiguration saved =
                YamlConfiguration.loadConfiguration(
                        directory.resolve("laserbox/map-a.yml").toFile());
        assertEquals(3, saved.getInt("copy-count"));
        assertEquals(new Vector(70, 20, -40), saved.getVector("copy-layout.origin"));
        assertEquals(new Vector(176, 0, 0), saved.getVector("copy-layout.step"));
        assertEquals(new Vector(33, 11, 33), saved.getVector("copy-layout.size"));

        config.setRightSpawnPoint(new Location(world, 72.5, 22, -38.5));
        config.setLeftSpawnPoint(new Location(world, 98.5, 22, -12.5));
        config.setSpectatorSpawnPoint(new Location(world, 80.5, 28, -30.5));
        config.setSupplyPoints(List.of("shared-world:75:22:-35:0:0"));
        LaserBoxGeometry geometry =
                new ReplicatedSpatialLayout<>(
                                LaserBoxGeometry.from(config),
                                config.getCopyGrid(),
                                config.getCopyCount())
                        .geometry(2);
        assertEquals(new Vector(424.5, 22, -38.5), geometry.rightSpawn().toVector());
        assertEquals(new Vector(450.5, 22, -12.5), geometry.leftSpawn().toVector());
        assertEquals(new Vector(432.5, 28, -30.5), geometry.spectatorSpawn().toVector());
        assertEquals(List.of(new Vector(427, 22, -35)), geometry.supplyPoints());
        assertEquals(new Vector(422, 20, -40), geometry.boundaryMin());
        assertEquals(new Vector(454, 30, -8), geometry.boundaryMax());
        assertTrue(session.getFlow().validate(session).isEmpty());
        assertTrue(session.getFlow().publish(session).join());
        assertEquals(1, worldSaves);
    }

    @Test
    void regeneratingWiderTemplateWithFewerCopiesClearsThePreviousLayoutAndPreservesSource() {
        session.step("schematic").capture(session, player);
        session.step("stamp").stamp(session, player, 4);
        worldEdit.pasted.clear();
        worldEdit.selection[1] = new Vector(134, 30, -8);
        worldEdit.size = new Vector(65, 11, 33);
        session.step("schematic").capture(session, player);

        session.step("stamp").stamp(session, player, 2);

        assertEquals(
                List.of(
                        new Cleared(new Vector(246, 20, -40), new Vector(33, 11, 33)),
                        new Cleared(new Vector(422, 20, -40), new Vector(33, 11, 33)),
                        new Cleared(new Vector(598, 20, -40), new Vector(33, 11, 33))),
                worldEdit.cleared);
        assertEquals(List.of(new Vector(278, 20, -40)), worldEdit.pasted);
        assertEquals(2, config.getCopyCount());
        assertEquals(new Vector(208, 0, 0), config.getCopyLayoutStep());
        assertEquals(new Vector(65, 11, 33), config.getCopySize());
    }

    @Test
    void missingTemplateInvalidCountAndBusyMapDoNotChangeTheWorldOrCopyCount() {
        assertTrue(session.step("stamp").stamp(session, player, 3).contains("missing template"));
        session.step("schematic").capture(session, player);
        assertTrue(session.step("stamp").stamp(session, player, 0).contains("positive count"));
        canSave = false;
        assertTrue(session.step("stamp").stamp(session, player, 3).contains("instance running"));
        assertEquals(1, config.getCopyCount());
        assertFalse(session.isStamped());
        assertTrue(worldEdit.pasted.isEmpty());
        assertTrue(worldEdit.cleared.isEmpty());
    }

    @Test
    void failedTemplateCaptureDoesNotReplaceExistingBoundary() {
        config.setAreaPos1(new Vector(1, 2, 3));
        config.setAreaPos2(new Vector(4, 5, 6));
        worldEdit.failSave = true;
        assertTrue(session.step("schematic").capture(session, player).contains("failed"));
        assertEquals(new Vector(1, 2, 3), config.getAreaPos1());
        assertEquals(new Vector(4, 5, 6), config.getAreaPos2());
        assertFalse(Files.exists(directory.resolve("laserbox/schematics/map-a/arena.schem")));
    }

    private record Cleared(Vector origin, Vector size) {}

    /**
     * Records the WorldEdit boundary while running the actual schematic/stamp steps and grid
     * transforms.
     */
    private static class RecordingWorldEdit extends WorldEditManager {
        Vector[] selection;
        Vector size;
        List<Vector> pasted;
        List<Cleared> cleared;
        boolean failSave;

        private RecordingWorldEdit() {
            super(null);
        }

        @Override
        public Vector[] getPlayerSelection(Player player, boolean blockVector) {
            return new Vector[] {selection[0].clone(), selection[1].clone()};
        }

        @Override
        public void saveSelectionAsSchematic(Player player, File file) throws IOException {
            if (failSave) throw new IOException("selection unavailable");
            Files.write(file.toPath(), new byte[] {1});
        }

        @Override
        public Vector getSchematicDimensions(File file) {
            return size.clone();
        }

        @Override
        public void pasteSchematic(World world, File file, int x, int y, int z) {
            pasted.add(new Vector(x, y, z));
        }

        @Override
        public void clearCuboid(World world, Vector origin, Vector dimensions) {
            cleared.add(new Cleared(origin.clone(), dimensions.clone()));
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(
                ((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null))
                        .allocateInstance(type));
    }

    private static void set(Object target, String name, Object value) throws Exception {
        field(target.getClass(), name).set(target, value);
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }
}
