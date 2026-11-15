package ink.ziip.championshipscore.api.game.riptiderush.config;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideHitwCatalog;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelTemplate;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelType;
import ink.ziip.championshipscore.api.game.riptiderush.support.RiptideTestFixtures;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class RiptideRushConfigTest {
    @Test
    void repeatedReadsReuseImmutableTemplatesAndCallerEditsCannotChangeThePool() throws Exception {
        var config = RiptideTestFixtures.config();
        var template = RiptideHitwCatalog.templates().getFirst();
        var row = new HashMap<>(template.serialize());
        var building = new HashMap<>(template.blueprint().serialize());
        row.put("building", building);
        var input = new ArrayList<>(List.of(row));
        config.setPool(new ArrayList<>(input));
        var resolved = config.resolvePool();
        row.put("enabled", false);
        building.put("schematic", "invalid");
        input.clear();
        assertSame(resolved, config.resolvePool());
        assertEquals(List.of(template), resolved);
        assertThrows(UnsupportedOperationException.class, () -> resolved.clear());
        assertThrows(UnsupportedOperationException.class, () -> config.getPool().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> config.getPool().getFirst().put("enabled", false));
        assertThrows(
                UnsupportedOperationException.class,
                () -> ((Map<?, ?>) config.getPool().getFirst().get("building")).clear());
    }

    @Test
    void editsReloadsAndReflectiveRollbackInvalidateParsedPool() throws Exception {
        var config = RiptideTestFixtures.config();
        var before = config.resolvePool();
        var savedRows = config.getPool();
        var changed = RiptideLevelTemplate.create("replacement", RiptideLevelType.MATH);
        config.setTemplates(List.of(changed));
        assertEquals(List.of(changed), config.resolvePool());
        assertNotSame(before, config.resolvePool());
        var field = RiptideRushConfig.class.getDeclaredField("pool");
        field.setAccessible(true);
        field.set(config, savedRows);
        assertEquals(before, config.resolvePool());
        var yaml = RiptideTestFixtures.defaults();
        yaml.set("course.pool", List.of(changed.serialize()));
        config.loadFromConfiguration(yaml);
        assertEquals(List.of(changed), config.resolvePool());
        assertSame(config.resolvePool(), config.resolvePool());
    }

    @Test
    void invalidReplacementDoesNotPoisonTheLastValidPool() throws Exception {
        var config = RiptideTestFixtures.config();
        var before = config.resolvePool();
        assertThrows(
                IllegalArgumentException.class,
                () -> config.setTemplates(List.of(before.getFirst(), before.getFirst())));
        assertSame(before, config.resolvePool());
        assertThrows(
                IllegalArgumentException.class,
                () -> config.setPool(List.of(Map.of("id", "invalid"))));
        assertSame(before, config.resolvePool());
    }

    @Nested
    class WorldBindingAndGeometryCases {
        @Test
        void rawYamlLoadedBeforeDraftWorldBindsWhenWorldBecomesAvailable() throws Exception {
            var c = RiptideTestFixtures.config();
            var loadedWorld = new java.util.concurrent.atomic.AtomicReference<org.bukkit.World>();
            var server =
                    java.lang.reflect.Proxy.newProxyInstance(
                            org.bukkit.Server.class.getClassLoader(),
                            new Class<?>[] {org.bukkit.Server.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("getWorld")) return loadedWorld.get();
                                throw new UnsupportedOperationException(method.getName());
                            });
            var pluginField =
                    ink.ziip.championshipscore.configuration.config.BaseConfigurationFile.class
                            .getDeclaredField("plugin");
            pluginField.setAccessible(true);
            var serverField = org.bukkit.plugin.java.JavaPlugin.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(pluginField.get(c), server);
            var world = c.getStartPoint().getWorld();
            var yaml = new YamlConfiguration();
            try (var reader =
                    new java.io.InputStreamReader(
                            getClass().getResourceAsStream("/riptiderush/area.yml"),
                            java.nio.charset.StandardCharsets.UTF_8)) {
                yaml.load(reader);
            }
            yaml.set("world-name", world.getName());
            yaml.set("start-point.y", 80D);
            yaml.set("finish-point.y", 79.628D);
            c.loadFromConfiguration(yaml);
            assertEquals(80D, c.getStartPoint().getY());
            assertEquals(80D, c.getFinishPoint().getY());
            assertNull(c.getStartPoint().getWorld());
            assertNull(c.getFinishPoint().getWorld());
            assertTrue(
                    assertThrows(IllegalArgumentException.class, c::resolveGeometry)
                            .getMessage()
                            .contains("地图世界尚未加载"));

            loadedWorld.set(world);
            var geometry = c.resolveGeometry();
            assertSame(world, c.getStartPoint().getWorld());
            assertSame(world, c.getFinishPoint().getWorld());
            assertEquals(500, geometry.totalSteps());
            assertEquals(80D, geometry.centerAt(0).getY());
            assertEquals(80D, geometry.centerAt(500).getY());

            loadedWorld.set(null);
            assertThrows(IllegalArgumentException.class, c::resolveGeometry);
        }

        @Test
        void inMemoryGeometryRejectsMismatchedY() throws Exception {
            var c = RiptideTestFixtures.config();
            c.getFinishPoint().setY(81);
            assertThrows(IllegalArgumentException.class, c::resolveGeometry);
        }

        @Test
        void clearingWorldBindingDoesNotReloadAndEraseNewMapNameOrEdits() throws Exception {
            var c = RiptideTestFixtures.config();
            var field =
                    ink.ziip.championshipscore.configuration.config.BaseConfigurationFile.class
                            .getDeclaredField("configuration");
            field.setAccessible(true);
            field.set(c, new YamlConfiguration());
            c.setAreaName("my-new-map");
            c.setMathCount(4);
            var pool = c.resolvePool();
            c.bindConfiguredWorld("");
            assertEquals("my-new-map", c.getAreaName());
            assertEquals(4, c.getMathCount());
            assertEquals(pool, c.resolvePool());
            assertNull(c.getStartPoint());
            assertNull(c.getFinishPoint());
            assertEquals(330, c.getTimer());
        }
    }
}
