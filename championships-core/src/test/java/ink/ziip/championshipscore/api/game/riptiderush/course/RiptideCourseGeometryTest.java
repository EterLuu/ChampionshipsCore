package ink.ziip.championshipscore.api.game.riptiderush.course;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.riptiderush.support.RiptideTestFixtures;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

class RiptideCourseGeometryTest {
    @Nested
    class RiptideCourseGeometryCases {
        private static final World WORLD = world("raft-test");

        @Test
        void resolvesCardinalCourseAndMovingFootprint() {
            RiptideCourseGeometry geometry =
                    RiptideCourseGeometry.resolve(
                            new Location(WORLD, 1.5, 65, 1.5),
                            new Location(WORLD, 1.5, 65, 41.5),
                            7,
                            9);

            assertEquals(0, geometry.stepX());
            assertEquals(1, geometry.stepZ());
            assertEquals(40, geometry.totalSteps());
            assertEquals(10, geometry.levelStep(0, 3));
            assertEquals(20, geometry.levelStep(1, 3));
            assertEquals(30, geometry.levelStep(2, 3));
            assertEquals(1.5D, geometry.lateralOffset(new Location(WORLD, 3, 65, 21.5), 20));
            assertEquals(-1.5D, geometry.lateralOffset(new Location(WORLD, 0, 65, 21.5), 20));
            assertTrue(geometry.containsMovingRaft(new Location(WORLD, 2, 65, 12), 10, 0, 6));
            assertFalse(geometry.containsMovingRaft(new Location(WORLD, 2, 57, 12), 10, 0, 6));
        }

        @Test
        void rejectsDiagonalDifferentHeightAndEvenRaftDimensions() {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            RiptideCourseGeometry.resolve(
                                    new Location(WORLD, 1.5, 65, 1.5),
                                    new Location(WORLD, 20, 65, 20),
                                    7,
                                    9));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            RiptideCourseGeometry.resolve(
                                    new Location(WORLD, 1.5, 65, 1.5),
                                    new Location(WORLD, 1.5, 66, 20),
                                    7,
                                    9));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            RiptideCourseGeometry.resolve(
                                    new Location(WORLD, 1.5, 65, 1.5),
                                    new Location(WORLD, 1.5, 65, 20),
                                    6,
                                    9));
        }

        private static World world(String name) {
            return (World)
                    Proxy.newProxyInstance(
                            World.class.getClassLoader(),
                            new Class<?>[] {World.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "getName" -> name;
                                        case "equals" -> proxy == args[0];
                                        case "hashCode" -> System.identityHashCode(proxy);
                                        case "toString" -> name;
                                        default ->
                                                throw new UnsupportedOperationException(
                                                        method.getName());
                                    });
        }
    }

    @Nested
    class RiptideAutomaticLayoutCases {
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
