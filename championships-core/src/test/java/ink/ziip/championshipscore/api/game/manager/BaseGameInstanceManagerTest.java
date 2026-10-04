package ink.ziip.championshipscore.api.game.manager;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.parkourtag.config.ParkourTagConfig;
import ink.ziip.championshipscore.api.game.parkourtag.runtime.ParkourTagArea;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class BaseGameInstanceManagerTest {
    @TempDir Path directory;

    @Test
    void registrationKeepsOwnedCopiesAndPublishesReadOnlySnapshots() throws Exception {
        var plugin = plugin();
        var manager = new Manager(plugin);
        var config = new ParkourTagConfig(plugin, "map");
        var first = area(config);
        var second = area(config);
        var supplied = new ArrayList<>(List.of(first, second));
        manager.register("map", supplied);
        supplied.clear();
        assertEquals(List.of(first, second), manager.getMapInstances("map"));
        assertEquals(2, manager.getRuntimeInstances().size());
        assertSame(first, manager.getArea("map"));
        assertThrows(
                UnsupportedOperationException.class, () -> manager.getMapInstances("map").clear());
    }

    @Test
    void detachingOneMapReleasesEveryCopyAndKeepsOtherMaps() throws Exception {
        var plugin = plugin();
        var manager = new Manager(plugin);
        var config = new ParkourTagConfig(plugin, "first");
        var first = area(config);
        var second = area(config);
        var unrelated = area(new ParkourTagConfig(plugin, "other"));
        manager.register("first", List.of(first, second));
        manager.register("other", List.of(unrelated));
        assertTrue(manager.detachAreaForRename("first"));
        assertEquals(1, first.disposals);
        assertEquals(1, second.disposals);
        assertEquals(0, unrelated.disposals);
        assertEquals(List.of(), manager.getMapInstances("first"));
        assertEquals(List.of(unrelated), List.copyOf(manager.getRuntimeInstances()));
    }

    @Test
    void anActiveCopyPreventsMapRemoval() throws Exception {
        var plugin = plugin();
        var manager = new Manager(plugin);
        var config = new ParkourTagConfig(plugin, "map");
        var first = area(config);
        var active = area(config);
        active.stage = GameStageEnum.PROGRESS;
        manager.register("map", List.of(first, active));
        assertFalse(manager.detachAreaForRename("map"));
        assertFalse(manager.deleteArea("map"));
        assertEquals(0, first.disposals);
        assertEquals(0, active.disposals);
    }

    @Test
    void deletionRemovesOnlyItsDefinitionAndOwnedCopies() throws Exception {
        var plugin = plugin();
        var manager = new Manager(plugin);
        var config = new ParkourTagConfig(plugin, "first");
        var first = area(config);
        var second = area(config);
        var otherConfig = new ParkourTagConfig(plugin, "other");
        var other = area(otherConfig);
        Path firstFile = directory.resolve(config.getFileName());
        Path otherFile = directory.resolve(otherConfig.getFileName());
        Files.createDirectories(firstFile.getParent());
        Files.writeString(firstFile, "{}");
        Files.writeString(otherFile, "{}");
        manager.register("first", List.of(first, second));
        manager.register("other", List.of(other));
        assertTrue(manager.deleteArea("first"));
        assertFalse(Files.exists(firstFile));
        assertTrue(Files.exists(otherFile));
        assertEquals(1, first.disposals);
        assertEquals(1, second.disposals);
        assertEquals(0, other.disposals);
        assertEquals(List.of(), manager.getMapInstances("first"));
    }

    @Test
    void clearingReleasesTheSecondaryIndexBeforeTheNextLoad() throws Exception {
        var plugin = plugin();
        var manager = new Manager(plugin);
        var config = new ParkourTagConfig(plugin, "map");
        var first = area(config);
        var second = area(config);
        manager.register("map", List.of(first, second));
        manager.clearAreas();
        assertTrue(manager.getRuntimeInstances().isEmpty());
        assertTrue(manager.getMapInstances("map").isEmpty());
        assertTrue(manager.getAreaNameList().isEmpty());
        assertEquals(1, first.disposals);
        assertEquals(1, second.disposals);
        var replacement = area(config);
        manager.register("map", List.of(replacement));
        assertEquals(List.of(replacement), manager.getMapInstances("map"));
    }

    private ChampionshipsCore plugin() throws Exception {
        var plugin = allocate(ChampionshipsCore.class);
        var field = JavaPlugin.class.getDeclaredField("dataFolder");
        field.setAccessible(true);
        field.set(plugin, directory.toFile());
        return plugin;
    }

    private static Area area(ParkourTagConfig config) throws Exception {
        var area = allocate(Area.class);
        area.config = config;
        return area;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class Manager extends BaseGameInstanceManager<Area> {
        private Manager(ChampionshipsCore plugin) {
            super(plugin);
        }

        void register(String name, List<Area> instances) {
            registerMapInstances(name, instances);
        }

        @Override
        public void load() {}

        @Override
        public boolean addArea(String name) {
            return false;
        }
    }

    private static final class Area extends ParkourTagArea {
        private ParkourTagConfig config;
        private GameStageEnum stage;
        private int disposals;

        private Area() {
            super(null, null);
        }

        @Override
        public void dispose() {
            disposals++;
        }

        @Override
        public GameStageEnum getGameStageEnum() {
            return stage == null ? GameStageEnum.WAITING : stage;
        }

        @Override
        public ParkourTagConfig getGameConfig() {
            return config;
        }
    }
}
