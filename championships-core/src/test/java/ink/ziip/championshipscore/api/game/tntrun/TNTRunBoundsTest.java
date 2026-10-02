package ink.ziip.championshipscore.api.game.tntrun;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TNTRunBoundsTest {
    @Nested
    class TNTRunPlayerBoundsCases {
        @Test
        void fallingBelowTheTemplateSurvivesUntilTheExplicitHeight() {
            TNTRunConfig config = preparedConfig();
            config.setEliminationY(60.5);

            Vector belowTemplate = new Vector(0, 70, 0);
            assertFalse(config.getCopyBoxes().getFirst().contains(belowTemplate));
            assertTrue(config.isInsidePlayerBounds(belowTemplate));
            assertTrue(config.isInsidePlayerBounds(new Vector(0, 60.5, 0)));
            assertFalse(config.isInsidePlayerBounds(new Vector(0, 60.499, 0)));
            assertEquals(80, config.getCopyBoxes().getFirst().getMinY(), "Terrain bounds must stay at the template");
        }

        @Test
        void higherAndNegativeEliminationHeightsAlsoOverrideTheTemplateBottom() {
            TNTRunConfig config = preparedConfig();
            config.setEliminationY(90.0);
            assertFalse(config.isInsidePlayerBounds(new Vector(0, 85, 0)));
            assertTrue(config.isInsidePlayerBounds(new Vector(0, 90, 0)));

            config.setEliminationY(-64.0);
            assertTrue(config.isInsidePlayerBounds(new Vector(0, -64, 0)));
            assertFalse(config.isInsidePlayerBounds(new Vector(0, -64.01, 0)));
        }

        @Test
        void allCopiesKeepTheirOwnFootprintAndTopWithoutIncludingTheGaps() {
            TNTRunConfig config = preparedConfig();
            config.setEliminationY(60.0);
            Vector second = config.getCopyGrid().origin(1).add(new Vector(10, -10, 10));
            assertTrue(config.isInsidePlayerBounds(second));
            assertFalse(config.isInsidePlayerBounds(new Vector(80, 70, 0)), "Gap between copies");
            assertTrue(config.isInsidePlayerBounds(new Vector(-20, 70, -10)));
            assertFalse(config.isInsidePlayerBounds(new Vector(20, 70, 0)), "Maximum X is exclusive");
            assertFalse(config.isInsidePlayerBounds(new Vector(0, 70, 10)), "Maximum Z is exclusive");
            assertFalse(config.isInsidePlayerBounds(new Vector(0, 120, 0)), "Template top is exclusive");
            assertFalse(config.isInsidePlayerBounds(null));
        }

        @Test
        void mapsWithoutExplicitHeightHaveNoPlayableBounds() {
            TNTRunConfig config = preparedConfig();
            assertNull(config.getEliminationY());
            assertEquals(80, config.getDefaultEliminationY());
            assertFalse(config.isInsidePlayerBounds(new Vector(0, 80, 0)));
            assertFalse(config.isInsidePlayerBounds(new Vector(0, 79.999, 0)));
        }

        @Test
        void mapsWithoutGeneratedCopiesHaveNoPlayableBounds() {
            TNTRunConfig config = config();
            config.setAreaPos1(new Vector(-20, 80, -10));
            config.setAreaPos2(new Vector(19, 119, 9));
            config.setEliminationY(60.0);
            assertFalse(config.isInsidePlayerBounds(new Vector(0, 70, 0)));
        }

        @Test
        void configurationAcceptsIntegerAndFractionalHeightsAndClearsRemovedValuesOnReload() {
            TNTRunConfig config = config();
            YamlConfiguration yaml = yaml();
            yaml.set("elimination-y", -64);
            config.loadFromConfiguration(yaml);
            assertEquals(-64.0, config.getEliminationY());
            yaml.set("elimination-y", 60.5);
            config.loadFromConfiguration(yaml);
            assertEquals(60.5, config.getEliminationY());
            yaml.set("elimination-y", null);
            config.loadFromConfiguration(yaml);
            assertNull(config.getEliminationY());
            assertFalse(config.isInsidePlayerBounds(new Vector(0, 70, 0)));
        }

        @Test
        void configurationRejectsNonFiniteHeights() {
            TNTRunConfig config = config();
            for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                YamlConfiguration yaml = yaml();
                yaml.set("elimination-y", value);
                assertThrows(IllegalArgumentException.class, () -> config.loadFromConfiguration(yaml));
            }
        }

        static TNTRunConfig preparedConfig() {
            TNTRunConfig config = config();
            config.setAreaPos1(new Vector(-20, 80, -10));
            config.setAreaPos2(new Vector(19, 119, 9));
            config.prepareCopyGrid(new Vector(40, 40, 20));
            config.setCopies(2);
            return config;
        }

        private static TNTRunConfig config() {
            try {
                var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                ChampionshipsCore plugin = (ChampionshipsCore) ((sun.misc.Unsafe) field.get(null))
                        .allocateInstance(ChampionshipsCore.class);
                return new TNTRunConfig(plugin, "test");
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        }

        private static YamlConfiguration yaml() {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("name", "test");
            yaml.set("timer", 180);
            yaml.set("area-pos1", new Vector(-20, 80, -10));
            yaml.set("area-pos2", new Vector(19, 119, 9));
            yaml.set("spectator-spawn-point", new Location(null, 0, 100, 0));
            yaml.set("spawn-points", List.of());
            yaml.set("prepare.published", true);
            yaml.set("prepare.dirty", false);
            yaml.set("prepare.revision", 1);
            yaml.set("prepare.world-built", true);
            return yaml;
        }
    }

    @Nested
    class TNTRunMovementBoundsCases {
        @Test
        void progressMovementBelowTheSchematicUsesTheIndependentEliminationHeight() throws Exception {
            HeadlessArea area = area(GameStageEnum.PROGRESS);
            Location location = new Location(world("arena"), 0, 70, 0);
            assertTrue(area.notInArea(location));
            assertFalse(area.notInPlayerArea(location));
            Player player = player(location);
            TNTRunHandler handler = new TNTRunHandler(null);
            handler.setTntRunTeamArea(area);

            handler.handleRoutedPlayerMoveLow(new PlayerMoveEvent(player, location.clone().add(0, 1, 0), location));
            assertEquals(0, area.teleports);
        }

        @Test
        void preparationStillReturnsPlayersWhoFallBelowTheTemplateToSpawn() throws Exception {
            HeadlessArea area = area(GameStageEnum.PREPARATION);
            Location location = new Location(world("arena"), 0, 70, 0);
            TNTRunHandler handler = new TNTRunHandler(null);
            handler.setTntRunTeamArea(area);
            handler.handleRoutedPlayerMoveLow(new PlayerMoveEvent(player(location), location.clone().add(0, 1, 0), location));
            assertEquals(1, area.teleports);
        }

        @Test
        void participantBoundsRejectMissingAndDifferentWorlds() throws Exception {
            HeadlessArea area = area(GameStageEnum.PROGRESS);
            assertTrue(area.notInPlayerArea(null));
            assertTrue(area.notInPlayerArea(new Location(null, 0, 70, 0)));
            assertTrue(area.notInPlayerArea(new Location(world("other"), 0, 70, 0)));
            assertTrue(area.notInPlayerArea(new Location(world("arena"), 0, 59.999, 0)));
        }

        @Test
        void spectatorVoidRecoveryKeepsItsOriginalHeightEvenWithALowerEliminationLine() throws Exception {
            HeadlessArea area = area(GameStageEnum.PROGRESS);
            area.config.setEliminationY(-100.0);
            area.spectator = true;
            Location location = new Location(world("arena"), 0, -70, 0);
            AtomicInteger teleports = new AtomicInteger();
            TNTRunHandler handler = new TNTRunHandler(null);
            handler.setTntRunTeamArea(area);
            assertFalse(area.notInPlayerArea(location));
            handler.handleRoutedPlayerMoveLow(new PlayerMoveEvent(player(location, teleports), location.clone(), location));
            assertEquals(1, teleports.get());
        }

        private static HeadlessArea area(GameStageEnum stage) throws Exception {
            var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            HeadlessArea area = (HeadlessArea) ((sun.misc.Unsafe) field.get(null)).allocateInstance(HeadlessArea.class);
            area.config = TNTRunPlayerBoundsCases.preparedConfig();
            area.config.setEliminationY(60.0);
            area.stage = stage;
            return area;
        }

        private static Player player(Location location) {
            return player(location, new AtomicInteger());
        }

        private static Player player(Location location, AtomicInteger teleports) {
            return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getLocation" -> location;
                        case "setFallDistance" -> null;
                        case "teleport" -> { teleports.incrementAndGet(); yield true; }
                        default -> throw new AssertionError("Unexpected player call: " + method.getName());
                    });
        }

        private static World world(String name) {
            return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getName" -> name;
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        default -> throw new AssertionError("Unexpected world call: " + method.getName());
                    });
        }

        private static final class HeadlessArea extends TNTRunTeamArea {
            TNTRunConfig config;
            GameStageEnum stage;
            int teleports;
            boolean spectator;

            private HeadlessArea() { super(null, null, false, "test"); }
            @Override public TNTRunConfig getGameConfig() { return config; }
            @Override public String getWorldName() { return "arena"; }
            @Override public boolean notAreaPlayer(Player player) { return false; }
            @Override public boolean isIntroductionPhase() { return false; }
            @Override public GameStageEnum getGameStageEnum() { return stage; }
            @Override public boolean isManagedSpectator(Player player) { return spectator; }
            @Override public Location getSpectatorSpawnLocation() { return new Location(null, 0, 100, 0); }
            @Override public void teleportPlayerToSpawnPoint(Player player) { teleports++; }
            @Override public void addDeathPlayer(Player player) { fail("Player must survive above elimination-y"); }
        }
    }
}
