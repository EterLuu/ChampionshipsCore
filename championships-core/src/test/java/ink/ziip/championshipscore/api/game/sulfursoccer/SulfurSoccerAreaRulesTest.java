package ink.ziip.championshipscore.api.game.sulfursoccer;

import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SulfurSoccerAreaRulesTest {
    @Nested
    class SulfurSoccerAreaRulesCases {
        private SulfurSoccerArea area;

        @Test void startRosterValidationUsesPersistedMembersWhenAPlayerIsOffline() throws Exception {
            var team = new ChampionshipTeam(1, "red", "red", "", Set.of(UUID.randomUUID()), null) { };
            var method = SulfurSoccerArea.class.getDeclaredMethod("readyTeam", ChampionshipTeam.class);
            method.setAccessible(true);
            assertTrue((Boolean) method.invoke(null, team));
        }

        @BeforeEach void setup() throws Exception {
            var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
            area = (SulfurSoccerArea) ((sun.misc.Unsafe) field.get(null)).allocateInstance(SulfurSoccerArea.class);
            set("gameConfig", SulfurSoccerConfigTest.config());
            set("field", new BoundingBox(-20, 0, -10, 21, 9, 11));
            set("pitchFloorY", 1.0);
            set("pearlReadyTicks", new HashMap<UUID, Integer>());
            set("pearls", new HashMap<UUID, EnderPearl>());
        }

        @Test void rejectsPearlsOutsideThePitchInOtherWorldsAndAboveTwoBlocksButAllowsTheExactLimit() {
            World world = world("soccer");
            assertTrue(area.validPearlDestination(new Location(world, 0, 1, 0)));
            assertTrue(area.validPearlDestination(new Location(world, 0, 3, 0)));
            for (Location location : new Location[]{new Location(world, 0, 3.001, 0), new Location(world, 21, 1, 0),
                    new Location(world, 0, 1, 11), new Location(world, -20.01, 1, 0), new Location(world, 0, -1, 0),
                    new Location(world, Double.NaN, 1, 0), new Location(world("other"), 0, 1, 0), new Location(null, 0, 1, 0)})
                assertFalse(area.validPearlDestination(location), String.valueOf(location.toVector()));
            assertFalse(area.validPearlDestination(null));
        }

        @Test void pearlCooldownIsIndependentPerPlayerAndLastsExactlyEightSecondsAcrossCountdowns() throws Exception {
            UUID id = UUID.randomUUID(), other = UUID.randomUUID();
            List<Integer> visualCooldowns = new ArrayList<>();
            Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (p, m, a) -> switch (m.getName()) {
                case "getUniqueId" -> id;
                case "setCooldown" -> { visualCooldowns.add((Integer) a[1]); yield null; }
                case "isOnline" -> false;
                default -> throw new AssertionError(m.getName());
            });
            EnderPearl pearl = (EnderPearl) Proxy.newProxyInstance(EnderPearl.class.getClassLoader(), new Class<?>[]{EnderPearl.class},
                    (p, m, a) -> m.getName().equals("getUniqueId") ? UUID.randomUUID() : null);
            List<Runnable> tasks = new ArrayList<>();
            BukkitScheduler scheduler = (BukkitScheduler) Proxy.newProxyInstance(BukkitScheduler.class.getClassLoader(), new Class<?>[]{BukkitScheduler.class}, (p, m, a) -> {
                assertEquals("runTask", m.getName());
                tasks.add((Runnable) a[1]);
                return null;
            });
            set("scheduler", scheduler);
            area.recordPearlLaunch(player, pearl);
            assertEquals(List.of(160), visualCooldowns);
            assertEquals(160, area.pearlCooldown(id));
            assertEquals(0, area.pearlCooldown(other));
            set("gameStageEnum", GameStageEnum.COUNTDOWN);
            set("ticks", 159);
            assertEquals(1, area.pearlCooldown(id));
            set("ticks", 160);
            assertEquals(0, area.pearlCooldown(id));
            assertEquals(1, tasks.size());
            assertDoesNotThrow(tasks.getFirst()::run, "A disconnected player must not receive an asynchronous inventory update");
        }

        @Test void openingKickoffResetsEveryPlayersPearlToAFullEightSeconds() throws Exception {
            UUID id = UUID.randomUUID();
            List<Integer> cooldowns = new ArrayList<>();
            Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (p, m, a) -> switch (m.getName()) {
                case "getUniqueId" -> id;
                case "setCooldown" -> { cooldowns.add((Integer) a[1]); yield null; }
                default -> throw new AssertionError(m.getName());
            });
            set("ticks", 4200);
            area.startPearlCooldown(player);
            assertEquals(160, area.pearlCooldown(id));
            assertEquals(List.of(160), cooldowns);
            set("ticks", 4359);
            assertEquals(1, area.pearlCooldown(id));
            set("ticks", 4360);
            assertEquals(0, area.pearlCooldown(id));
            area.startPearlCooldown(player);
            assertEquals(160, area.pearlCooldown(id));
        }

        @Test void openingCountdownHasTheGameTitleAndLaterCountdownsContainOnlyNumbers() throws Exception {
            String originalUpcoming = MessageConfig.SULFUR_SOCCER_UPCOMING_TITLE;
            String originalCount = MessageConfig.GAME_START_COUNT_DOWN_TITLE;
            try {
                MessageConfig.SULFUR_SOCCER_UPCOMING_TITLE = "硫方足球即将开始";
                MessageConfig.GAME_START_COUNT_DOWN_TITLE = "%time%";
                set("openingKickoff", true);
                assertEquals("硫方足球即将开始", area.getFinalCountdownTitle(5));
                assertEquals("&e5", area.getFinalCountdownSubtitle("SulfurSoccer", 5));
                set("openingKickoff", false);
                assertEquals("5", area.getFinalCountdownTitle(5));
                assertEquals("", area.getFinalCountdownSubtitle("SulfurSoccer", 5));
            } finally {
                MessageConfig.SULFUR_SOCCER_UPCOMING_TITLE = originalUpcoming;
                MessageConfig.GAME_START_COUNT_DOWN_TITLE = originalCount;
            }
        }

        @Test void discardsQueuedPearlRefillsAfterResetAndWhenPlayHasEnded() throws Exception {
            UUID id = UUID.randomUUID();
            List<Integer> cooldowns = new ArrayList<>();
            Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (p, m, a) -> switch (m.getName()) {
                case "getUniqueId" -> id;
                case "isOnline" -> true;
                case "setCooldown" -> { cooldowns.add((Integer) a[1]); yield null; }
                case "getInventory" -> throw new AssertionError("A stale refill must not mutate the inventory");
                default -> throw new AssertionError(m.getName());
            });
            set("rightChampionshipTeam", new ChampionshipTeam(1, "red", "red", "", Set.of(id), null) { });
            set("gameStageEnum", GameStageEnum.PREPARATION);
            var match = new SulfurSoccerMatch(5); match.startWarmup(180); set("match", match);
            List<Runnable> tasks = new ArrayList<>();
            set("scheduler", Proxy.newProxyInstance(BukkitScheduler.class.getClassLoader(), new Class<?>[]{BukkitScheduler.class}, (p, m, a) -> {
                tasks.add((Runnable) a[1]); return null;
            }));
            EnderPearl pearl = (EnderPearl) Proxy.newProxyInstance(EnderPearl.class.getClassLoader(), new Class<?>[]{EnderPearl.class},
                    (p, m, a) -> UUID.randomUUID());
            area.recordPearlLaunch(player, pearl);
            assertEquals(List.of(160), cooldowns);
            set("pearlGeneration", 1L);
            assertDoesNotThrow(tasks.getFirst()::run);
            set("pearlGeneration", 0L);
            set("gameStageEnum", GameStageEnum.END);
            assertDoesNotThrow(tasks.getFirst()::run);
        }

        @Test void warmupAllowsPlayDuringPreparationButOtherPreparationAndCountdownDoNot() throws Exception {
            var match = new SulfurSoccerMatch(5);
            set("match", match);
            set("gameStageEnum", GameStageEnum.PREPARATION);
            assertFalse(area.isPlayPhase());
            match.startWarmup(180);
            assertTrue(area.isPlayPhase());
            assertEquals(180, area.getTimer());
            assertEquals("试踢 03:00（不计分）", area.getStateText());
            set("paused", true);
            assertEquals("等待选手重连", area.getStateText());
            set("gameStageEnum", GameStageEnum.COUNTDOWN);
            assertFalse(area.isPlayPhase());
        }

        private static World world(String name) {
            return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class}, (p, m, a) -> {
                if (m.getName().equals("getName")) return name;
                throw new AssertionError(m.getName());
            });
        }

        private void set(String name, Object value) throws Exception {
            for (Class<?> type = area.getClass(); type != null; type = type.getSuperclass()) {
                try { var field = type.getDeclaredField(name); field.setAccessible(true); field.set(area, value); return; }
                catch (NoSuchFieldException ignored) { }
            }
            throw new NoSuchFieldException(name);
        }
    }

    @Nested
    class SulfurSoccerSpawnsCases {
        @Test void redistributesFourMidfieldPointsIntoThreeAndFindsTheIsolatedKeeperQuartz() throws Exception {
            var config = SulfurSoccerConfigTest.config();
            config.setRightSpawnPoints(List.of("soccer:4:1:-6:90:0", "soccer:4:1:-2:90:0", "soccer:4:1:2:90:0", "soccer:4:1:6:90:0"));
            config.setLeftSpawnPoints(List.of("soccer:-4:1:-6:-90:0", "soccer:-4:1:-2:-90:0", "soccer:-4:1:2:-90:0", "soccer:-4:1:6:-90:0"));
            var world = world(Map.of("12:0:0", Material.QUARTZ_BLOCK, "-12:0:0", Material.SMOOTH_QUARTZ,
                    "16:0:0", Material.QUARTZ_BLOCK, "16:0:1", Material.QUARTZ_BLOCK,
                    "19:0:0", Material.QUARTZ_BLOCK));
            var layout = SulfurSoccerSpawns.resolve(world, config);
            assertEquals(List.of(new Vector(4, 1, -6), new Vector(4, 1, 0), new Vector(4, 1, 6), new Vector(12.5, 1, 0.5)),
                    layout.right().stream().map(p -> p.toVector()).toList());
            assertEquals(new Vector(-11.5, 1, 0.5), layout.left().get(3).toVector());
            assertEquals(1, layout.floorY());
            assertTrue(layout.right().stream().allMatch(p -> p.getWorld() == world && p.getYaw() == 90));
            assertEquals(4, config.getRightSpawnPoints().size(), "Resolving a match must not rewrite the published configuration");
        }

        @Test void worksWhenThePitchRunsAlongZAndTheFourthReferenceIsAlreadyTheKeeper() throws Exception {
            var config = SulfurSoccerConfigTest.config();
            config.setAreaPos1(new Vector(-10, 0, -20)); config.setAreaPos2(new Vector(10, 8, 20));
            config.setRightAreaPos1(new Vector(-10, 0, 0)); config.setRightAreaPos2(new Vector(10, 8, 20));
            config.setLeftAreaPos1(new Vector(-10, 0, -20)); config.setLeftAreaPos2(new Vector(10, 8, -1));
            config.setRightGoalPos1(new Vector(-2, 0, 18)); config.setRightGoalPos2(new Vector(2, 3, 20));
            config.setLeftGoalPos1(new Vector(-2, 0, -20)); config.setLeftGoalPos2(new Vector(2, 3, -18));
            config.setRightSpawnPoints(List.of("soccer:-6:1:4:180:0", "soccer:0:1:4:180:0", "soccer:6:1:4:180:0", "soccer:0.5:1:12.5:180:0"));
            config.setLeftSpawnPoints(List.of("soccer:-6:1:-4:0:0", "soccer:0:1:-4:0:0", "soccer:6:1:-4:0:0", "soccer:0.5:1:-11.5:0:0"));
            var layout = SulfurSoccerSpawns.resolve(world(Map.of("0:0:12", Material.QUARTZ_BLOCK, "0:0:-12", Material.QUARTZ_BLOCK)), config);
            assertEquals(List.of(new Vector(-6, 1, 4), new Vector(0, 1, 4), new Vector(6, 1, 4), new Vector(0.5, 1, 12.5)),
                    layout.right().stream().map(p -> p.toVector()).toList());
            assertEquals(180, layout.right().get(3).getYaw());
        }

        @Test void doesNotMistakeQuartzLinesOrBlockedMarkersForKeeperSpawns() throws Exception {
            var config = SulfurSoccerConfigTest.config();
            for (Map<String, Material> blocks : List.of(Map.<String, Material>of(),
                    Map.of("12:0:0", Material.QUARTZ_BLOCK, "13:0:0", Material.QUARTZ_BLOCK),
                    Map.of("12:0:0", Material.QUARTZ_BLOCK, "12:2:0", Material.STONE))) {
                var error = assertThrows(IllegalArgumentException.class, () -> SulfurSoccerSpawns.resolve(world(blocks), config));
                assertTrue(error.getMessage().contains("独立石英"));
            }
        }

        @Test void usesTheConfiguredKeeperMarkerWhenMoreThanOneIsolatedQuartzExists() throws Exception {
            var config = SulfurSoccerConfigTest.config();
            var world = world(Map.of("12:0:0", Material.QUARTZ_BLOCK, "16:0:0", Material.QUARTZ_BLOCK,
                    "-12:0:0", Material.QUARTZ_BLOCK));
            var error = assertThrows(IllegalArgumentException.class, () -> SulfurSoccerSpawns.resolve(world, config));
            assertTrue(error.getMessage().contains("第 4 个出生点"));
            config.setRightSpawnPoints(List.of("soccer:4:1:-6:90:0", "soccer:4:1:0:90:0", "soccer:4:1:6:90:0", "soccer:12.5:1:0.5:90:0"));
            var layout = SulfurSoccerSpawns.resolve(world, config);
            assertEquals(new Vector(12.5, 1, 0.5), layout.right().get(3).toVector());
        }

        @Test void acceptsThePublishedStadiumsWhiteConcreteMarkerAndIgnoresConnectedWhiteLines() throws Exception {
            var config = SulfurSoccerConfigTest.config();
            var world = world(Map.of("12:0:0", Material.WHITE_CONCRETE, "-12:0:0", Material.WHITE_CONCRETE,
                    "16:0:0", Material.WHITE_CONCRETE, "16:0:1", Material.WHITE_CONCRETE));
            var layout = SulfurSoccerSpawns.resolve(world, config);
            assertEquals(new Vector(12.5, 1, 0.5), layout.right().get(3).toVector());
            assertEquals(new Vector(-11.5, 1, 0.5), layout.left().get(3).toVector());
        }

        private static World world(Map<String, Material> blocks) {
            return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class}, (proxy, method, args) -> {
                if (!method.getName().equals("getBlockAt")) throw new AssertionError(method.getName());
                Material material = blocks.getOrDefault(args[0] + ":" + args[1] + ":" + args[2], Material.AIR);
                return Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class}, (p, m, a) -> switch (m.getName()) {
                    case "getType" -> material;
                    case "isPassable" -> material == Material.AIR;
                    default -> throw new AssertionError(m.getName());
                });
            });
        }
    }
}
