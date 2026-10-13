package ink.ziip.championshipscore.api.game.area.prepare;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.FourArenaConfig;
import ink.ziip.championshipscore.api.game.frostbite.config.FrostbiteConfig;
import ink.ziip.championshipscore.api.game.lasershowdown.config.LaserShowdownConfig;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.integration.worldedit.WorldEditManager;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@ExtendWith(ConfigurationStateExtension.class)
class FourArenaPrepareFlowTest {
    @TempDir Path directory;
    private Object previousServer;
    private ChampionshipsCore plugin;
    private World world;
    private Player player;
    private RecordingWorldEdit worldEdit;
    private FourArenaConfig config;
    private PrepareSession session;
    private boolean canSave;
    private int snapshots;
    private int worldSaves;

    @BeforeEach
    void setup() throws Exception {
        previousServer = field(Bukkit.class, "server").get(null);
        plugin = allocate(ChampionshipsCore.class);
        set(plugin, "dataFolder", directory.toFile());
        set(plugin, "logger", Logger.getAnonymousLogger());
        world =
                proxy(
                        World.class,
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "getName" -> "arena-world";
                                    case "getKey" -> NamespacedKey.minecraft("arena-world");
                                    case "getNearbyEntities" -> List.of();
                                    case "save" -> {
                                        worldSaves++;
                                        yield null;
                                    }
                                    case "equals" -> p == a[0];
                                    case "hashCode" -> System.identityHashCode(p);
                                    default -> throw new AssertionError(m.getName());
                                });
        Server server =
                proxy(
                        Server.class,
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "getWorld" -> world;
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
                                    case "getLocation" ->
                                            new Location(world, 72.5, 22, -38.5, 45F, 10F);
                                    default -> throw new AssertionError(m.getName());
                                });
        worldEdit = allocate(RecordingWorldEdit.class);
        worldEdit.selection = new Vector[] {new Vector(70, 20, -40), new Vector(102, 30, -8)};
        worldEdit.size = new Vector(33, 11, 33);
        worldEdit.pasted = new ArrayList<>();
        worldEdit.cleared = new ArrayList<>();
        set(plugin, "worldEditManager", worldEdit);
        canSave = true;
        MessageConfig.MAP_EDITOR_STEP_SCHEMATIC_SAVED = "saved %file%";
        MessageConfig.MAP_EDITOR_STEP_SCHEMATIC_SAVE_FAILED = "failed %detail%";
        MessageConfig.MAP_EDITOR_STEP_ARENA_TOTAL_SET = "total %count%, copies %copies%";
        MessageConfig.MAP_EDITOR_STEP_ARENA_TEMPLATE_MISSING = "missing template";
        MessageConfig.MAP_EDITOR_STEP_ARENA_INSTANCE_RUNNING = "instance running";
        MessageConfig.MAP_EDITOR_STEP_ARENA_GENERATE_FAILED = "failed %detail%";
        MessageConfig.MAP_EDITOR_STEP_POINT_ADDED_CURRENT = "added %count%";
    }

    private void create(GameTypeEnum game) throws Exception {
        config =
                game == GameTypeEnum.FrostbiteFrenzy
                        ? new FrostbiteConfig(plugin, "map-a")
                        : new LaserShowdownConfig(plugin, "map-a");
        set(config, "configuration", new YamlConfiguration());
        Path folder = directory.resolve(config.getFolderName());
        Files.createDirectories(folder);
        set(config, "configurationPath", folder.resolve("map-a.yml"));
        config.bindConfiguredWorld("arena-world");
        if (config instanceof LaserShowdownConfig laser) laser.setTimer(90);
        config.beginPrepareDraft();
        SetupTarget target =
                proxy(
                        SetupTarget.class,
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "plugin" -> plugin;
                                    case "config" -> config;
                                    case "name" -> "map-a";
                                    case "worldName" -> "arena-world";
                                    case "canSaveMap" -> canSave;
                                    case "saveMap" -> {
                                        snapshots++;
                                        yield CompletableFuture.completedFuture(true);
                                    }
                                    default -> throw new AssertionError(m.getName());
                                });
        PrepareFlowDefinition flow =
                game == GameTypeEnum.FrostbiteFrenzy
                        ? new FrostbitePrepareFlow()
                        : new LaserShowdownPrepareFlow();
        session = new PrepareSession(plugin, game, "map-a", target, flow);
    }

    @AfterEach
    void restoreServer() throws Exception {
        field(Bukkit.class, "server").set(null, previousServer);
    }

    @ParameterizedTest
    @EnumSource(
            value = GameTypeEnum.class,
            names = {"FrostbiteFrenzy", "LaserShowdown"})
    void generatesThreePhysicalCopiesAndTranslatesGameplayPointsAndBoundaries(GameTypeEnum game)
            throws Exception {
        create(game);
        session.step("schematic").capture(session, player);
        assertTrue(
                Files.isRegularFile(
                        directory.resolve(
                                config.getFolderName() + "schematics/map-a/arena.schem")));
        assertEquals(new Vector(70, 20, -40), config.getArenaMin());
        assertEquals(new Vector(102, 30, -8), config.getArenaMax());
        assertFalse(session.step("stamp").isSet(session));
        session.step("stamp").stamp(session, player, 4);
        assertEquals(
                List.of(
                        new Vector(246, 20, -40),
                        new Vector(70, 20, 136),
                        new Vector(246, 20, 136)),
                worldEdit.pasted);
        assertTrue(worldEdit.cleared.isEmpty());
        assertTrue(config.isCopyLayoutCurrent());
        assertTrue(session.step("stamp").isSet(session));
        if (config instanceof FrostbiteConfig frostbite) {
            frostbite.setSpectatorSpawnPoint(new Location(world, 80, 28, -30));
            frostbite.setSpawnPoints(Collections.nCopies(16, "arena-world:72.5:22:-38.5:45:10"));
            frostbite.setItemPoints(List.of("arena-world:75.5:22:-35.5:-90:5"));
            frostbite.validate();
            assertEquals(new Vector(248.5, 22, 137.5), frostbite.spawns(3).getFirst().toVector());
            assertEquals(
                    new Vector(251.5, 22, 140.5),
                    frostbite.point(frostbite.getItemPoints().getFirst(), 3).toVector());
            assertTrue(frostbite.contains(frostbite.spawns(3).getFirst(), 3));
            assertFalse(frostbite.contains(frostbite.spawns(3).getFirst(), 0));
        } else {
            LaserShowdownConfig laser = (LaserShowdownConfig) config;
            laser.setSpectatorSpawnPoint(new Location(world, 80, 28, -30));
            laser.ensurePlayerSpawnPoints()
                    .set("area1", List.of("arena-world:72.5:22:-38.5:45:10"));
            laser.ensurePlayerSpawnPoints().set("area2", List.of("arena-world:999:22:999:0:0"));
            laser.validate();
            Location fourth = laser.arenaSpawns().get(3).getFirst();
            assertEquals(new Location(world, 248.5, 22, 137.5, 45F, 10F), fourth);
            assertTrue(laser.contains(fourth, 3));
            assertFalse(laser.contains(fourth, 0));
            var area =
                    allocate(
                            ink.ziip.championshipscore.api.game.lasershowdown.runtime
                                    .LaserShowdownTeamArea.class);
            set(area, "gameConfig", laser);
            set(area, "areaLocations", new ArrayList<>());
            assertEquals(4, area.getArenaCount());
            laser.ensurePlayerSpawnPoints().set("area1", List.of());
            assertTrue(
                    area.getPlayableArenaIndices().isEmpty(),
                    "live edits must replace the spawn cache");
            laser.ensurePlayerSpawnPoints()
                    .set("area1", List.of("arena-world:72.5:22:-38.5:45:10"));
            assertEquals(List.of(0, 1, 2, 3), area.getPlayableArenaIndices());
        }
        config.saveOptions();
        assertTrue(session.getFlow().validate(session).isEmpty());
        assertTrue(session.getFlow().publish(session).join());
        assertEquals(game == GameTypeEnum.FrostbiteFrenzy ? 1 : 0, snapshots);
        assertEquals(game == GameTypeEnum.FrostbiteFrenzy ? 0 : 1, worldSaves);
        config.markPreparePublished();
        assertTrue(config.isPrepareReady());
        YamlConfiguration saved =
                YamlConfiguration.loadConfiguration(
                        directory.resolve(config.getFolderName() + "map-a.yml").toFile());
        assertEquals(new Vector(176, 0, 176), saved.getVector("copy-layout.step"));
        assertTrue(saved.getBoolean("copy-layout.generated"));
        FourArenaConfig reloaded =
                game == GameTypeEnum.FrostbiteFrenzy
                        ? new FrostbiteConfig(plugin, "map-a")
                        : new LaserShowdownConfig(plugin, "map-a");
        reloaded.loadFromConfiguration(saved);
        assertTrue(reloaded.isCopyLayoutCurrent());
        assertEquals(config.getCopyGrid().origin(3), reloaded.getCopyGrid().origin(3));
    }

    @ParameterizedTest
    @EnumSource(
            value = GameTypeEnum.class,
            names = {"FrostbiteFrenzy", "LaserShowdown"})
    void recapturingInvalidatesCopiesAndRegenerationClearsOnlyTheOldReplicas(GameTypeEnum game)
            throws Exception {
        create(game);
        session.step("schematic").capture(session, player);
        session.step("stamp").stamp(session, player, 4);
        config.markPreparePublished();
        worldEdit.pasted.clear();
        worldEdit.selection[1] = new Vector(134, 30, -8);
        worldEdit.size = new Vector(65, 11, 33);
        session.step("schematic").capture(session, player);
        assertFalse(config.isCopyLayoutCurrent());
        assertFalse(config.isPrepareReady());
        assertFalse(session.isStamped());
        session.step("stamp").stamp(session, player, 4);
        assertEquals(
                List.of(
                        new Cleared(new Vector(246, 20, -40), new Vector(33, 11, 33)),
                        new Cleared(new Vector(70, 20, 136), new Vector(33, 11, 33)),
                        new Cleared(new Vector(246, 20, 136), new Vector(33, 11, 33))),
                worldEdit.cleared);
        assertEquals(
                List.of(
                        new Vector(278, 20, -40),
                        new Vector(70, 20, 136),
                        new Vector(278, 20, 136)),
                worldEdit.pasted);
        assertTrue(config.isCopyLayoutCurrent());
        assertTrue(config.isPrepareDirty());
        session.step("schematic").capture(session, player);
        assertFalse(
                config.isCopyLayoutCurrent(),
                "even unchanged dimensions require regeneration after recapture");
    }

    @ParameterizedTest
    @EnumSource(
            value = GameTypeEnum.class,
            names = {"FrostbiteFrenzy", "LaserShowdown"})
    void missingTemplateWrongCountBusyMapAndDimensionMismatchCannotModifyTerrain(GameTypeEnum game)
            throws Exception {
        create(game);
        assertTrue(session.step("stamp").stamp(session, player, 4).contains("missing template"));
        session.step("schematic").capture(session, player);
        for (int count : new int[] {0, 1, 3, 5})
            session.step("stamp").stamp(session, player, count);
        canSave = false;
        assertTrue(session.step("stamp").stamp(session, player, 4).contains("instance running"));
        assertTrue(session.step("schematic").capture(session, player).contains("instance running"));
        canSave = true;
        worldEdit.size = new Vector(65, 11, 33);
        assertTrue(session.step("stamp").stamp(session, player, 4).contains("failed"));
        assertTrue(worldEdit.pasted.isEmpty());
        assertTrue(worldEdit.cleared.isEmpty());
        assertFalse(config.hasCopyLayout());
    }

    @ParameterizedTest
    @EnumSource(
            value = GameTypeEnum.class,
            names = {"FrostbiteFrenzy", "LaserShowdown"})
    void overlappingNewSourceIsProtectedFromOldReplicaCleanup(GameTypeEnum game) throws Exception {
        create(game);
        session.step("schematic").capture(session, player);
        session.step("stamp").stamp(session, player, 4);
        worldEdit.pasted.clear();
        worldEdit.selection = new Vector[] {new Vector(246, 20, -40), new Vector(278, 30, -8)};
        session.step("schematic").capture(session, player);
        assertTrue(session.step("stamp").stamp(session, player, 4).contains("failed"));
        assertTrue(worldEdit.pasted.isEmpty());
        assertTrue(worldEdit.cleared.isEmpty());
        assertFalse(config.isCopyLayoutCurrent());
    }

    @ParameterizedTest
    @EnumSource(
            value = GameTypeEnum.class,
            names = {"FrostbiteFrenzy", "LaserShowdown"})
    void failedPasteLeavesMapDirtyAndCannotPublishTheOldGeneratedRevision(GameTypeEnum game)
            throws Exception {
        create(game);
        session.step("schematic").capture(session, player);
        session.step("stamp").stamp(session, player, 4);
        config.markPreparePublished();
        worldEdit.failPaste = true;
        assertTrue(session.step("stamp").stamp(session, player, 4).contains("failed"));
        assertFalse(config.isPrepareReady());
        assertFalse(session.step("stamp").isSet(session));
        assertFalse(session.getFlow().validate(session).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(
            value = GameTypeEnum.class,
            names = {"FrostbiteFrenzy", "LaserShowdown"})
    void legacyPrebuiltMapsRetainTheirPublishedStateAndCoordinatesUntilTemplateCapture(
            GameTypeEnum game) throws Exception {
        create(game);
        config.setArenaMin(new Vector(70, 20, -40));
        config.setArenaMax(new Vector(102, 30, -8));
        config.markPrepareWorldBuilt();
        config.markPreparePublished();
        assertTrue(config.isPrepareReady());
        if (config instanceof FrostbiteConfig frostbite) {
            assertEquals(new Vector(256, 0, 256), frostbite.offset(3));
            assertEquals(new Vector(358, 30, 248), frostbite.getAreaPos2());
        } else {
            LaserShowdownConfig laser = (LaserShowdownConfig) config;
            laser.setAreaPos1(new Vector(-400, -40, -300));
            laser.setAreaPos2(new Vector(400, 120, 300));
            laser.ensurePlayerSpawnPoints().set("area1", List.of("arena-world:72.5:22:-38.5:0:0"));
            laser.ensurePlayerSpawnPoints()
                    .set("area2", List.of("arena-world:-75.5:22:138.5:90:0"));
            assertEquals(
                    new Vector(-75.5, 22, 138.5), laser.arenaSpawns().get(1).getFirst().toVector());
            assertEquals(new Vector(-400, -40, -300), laser.getAreaPos1());
        }
        worldEdit.failSave = true;
        assertTrue(session.step("schematic").capture(session, player).contains("failed"));
        assertTrue(config.isPrepareReady());
        assertFalse(config.isTemplateCaptured());
    }

    private record Cleared(Vector origin, Vector size) {}

    private static class RecordingWorldEdit extends WorldEditManager {
        Vector[] selection;
        Vector size;
        List<Vector> pasted;
        List<Cleared> cleared;
        boolean failSave;
        boolean failPaste;

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
        public void pasteSchematic(World world, File file, int x, int y, int z) throws IOException {
            if (failPaste) throw new IOException("paste failed");
            pasted.add(new Vector(x, y, z));
        }

        @Override
        public void clearCuboid(World world, Vector origin, Vector size) {
            cleared.add(new Cleared(origin.clone(), size.clone()));
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
