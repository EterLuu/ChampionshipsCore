package ink.ziip.championshipscore.api.game.sulfursoccer;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import io.papermc.paper.event.entity.EntityCollideWithEntityEvent;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SulfurCube;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SulfurSoccerHandlerTest {
    @Nested
    class SulfurSoccerHandlerCases {
        private SulfurSoccerArea area;
        private SulfurSoccerHandler handler;
        private final Player right = entity(Player.class);
        private final Player left = entity(Player.class);
        private final Player outsider = entity(Player.class);
        private final SulfurCube ball = entity(SulfurCube.class);

        @BeforeEach void setup() throws Exception {
            var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
            area = (SulfurSoccerArea) ((sun.misc.Unsafe) field.get(null)).allocateInstance(SulfurSoccerArea.class);
            set(area, "rightChampionshipTeam", new Team(1, right.getUniqueId()));
            set(area, "leftChampionshipTeam", new Team(2, left.getUniqueId()));
            set(area, "gameStageEnum", GameStageEnum.COUNTDOWN);
            set(area, "ball", ball);
            handler = new SulfurSoccerHandler(null);
            handler.setArea(area);
        }

        @Test void blocksBothTeamsAttackingPlayersIncludingAnOutsiderAttackingFinalists() {
            for (Player[] pair : new Player[][]{{right, left}, {left, right}, {right, outsider}, {outsider, right}}) {
                var event = new PrePlayerAttackEntityEvent(pair[0], pair[1], true);
                handler.onAttack(event);
                assertTrue(event.isCancelled());
            }
            var unrelated = new PrePlayerAttackEntityEvent(outsider, entity(Player.class), true);
            handler.onAttack(unrelated);
            assertFalse(unrelated.isCancelled());
        }

        @Test void blocksBallAttacksBeforeKickoffAndDuringPause() throws Exception {
            for (Player player : new Player[]{right, left, outsider}) {
                var event = new PrePlayerAttackEntityEvent(player, ball, true);
                handler.onAttack(event);
                assertTrue(event.isCancelled());
            }
            var match = new SulfurSoccerMatch(5); match.kickOff();
            set(area, "match", match); set(area, "gameStageEnum", GameStageEnum.PROGRESS); set(area, "paused", true);
            var event = new PrePlayerAttackEntityEvent(right, ball, true);
            handler.onAttack(event);
            assertTrue(event.isCancelled());
        }

        @Test void cancelsNativeAttackPushesAndContactWhileBallIsLocked() {
            var push = new EntityPushedByEntityAttackEvent(ball, EntityKnockbackEvent.Cause.ENTITY_ATTACK, right, new Vector(1, 0, 0));
            handler.onKnockback(push);
            assertTrue(push.isCancelled());
            var playerPush = new EntityPushedByEntityAttackEvent(left, EntityKnockbackEvent.Cause.ENTITY_ATTACK, right, new Vector(1, 0, 0));
            handler.onKnockback(playerPush);
            assertTrue(playerPush.isCancelled());
            var collision = new EntityCollideWithEntityEvent(ball, outsider);
            handler.onCollision(collision);
            assertTrue(collision.isCancelled());
        }

        @Test void protectsOnlyTheMatchBallFromRightClickChanges() {
            var event = new PlayerInteractEntityEvent(outsider, ball);
            handler.onBallInteract(event);
            assertTrue(event.isCancelled());
            var unrelated = new PlayerInteractEntityEvent(outsider, entity(SulfurCube.class));
            handler.onBallInteract(unrelated);
            assertFalse(unrelated.isCancelled());
        }

        @Test void penaltiesBlockPlayerContactKeeperAttacksAndSecondStrikes() throws Exception {
            var match = new SulfurSoccerMatch(5); match.kickOff();
            var shootout = new SulfurSoccerShootout(List.of(right.getUniqueId()), List.of(left.getUniqueId()));
            set(area, "match", match);
            set(area, "shootout", shootout);
            set(area, "gameStageEnum", GameStageEnum.PROGRESS);
            for (Player player : new Player[]{right, left, outsider}) {
                var attack = new PrePlayerAttackEntityEvent(player, ball, true);
                handler.onAttack(attack);
                assertTrue(attack.isCancelled(), "Neither player can strike during preparation");
                var collision = new EntityCollideWithEntityEvent(ball, player);
                handler.onCollision(collision);
                assertTrue(collision.isCancelled(), "Contact must never push a penalty ball");
            }
            for (int tick = 0; tick < 60; tick++) shootout.tick();
            var keeperPush = new EntityPushedByEntityAttackEvent(ball, EntityKnockbackEvent.Cause.ENTITY_ATTACK, left, new Vector(1, 0, 0));
            handler.onKnockback(keeperPush);
            assertTrue(keeperPush.isCancelled());
            assertFalse(shootout.struck(), "A keeper's rejected push must not consume the shooter's attempt");
            assertTrue(shootout.strike(right.getUniqueId()));
            var secondAttack = new PrePlayerAttackEntityEvent(right, ball, true);
            handler.onAttack(secondAttack);
            assertTrue(secondAttack.isCancelled());
            var secondPush = new EntityPushedByEntityAttackEvent(ball, EntityKnockbackEvent.Cause.ENTITY_ATTACK, right, new Vector(1, 0, 0));
            handler.onKnockback(secondPush);
            assertTrue(secondPush.isCancelled());
            assertFalse(area.canUsePearl(right));
        }

        @Test void hotbarChangesAfterEndingCannotRebuildTheRemovedGlassPanes() throws Exception {
            set(area, "shootout", new SulfurSoccerShootout(List.of(right.getUniqueId()), List.of(left.getUniqueId())));
            set(area, "gameStageEnum", GameStageEnum.END);
            assertFalse(area.isShootout());
            assertFalse(area.selectPenaltyDirection(left, 0));
            assertDoesNotThrow(() -> handler.onHeldItem(new PlayerItemHeldEvent(left, 1, 0)));
        }

        @Test void refusesPearlLaunchesAndLandingsDuringCountdownAndPause() throws Exception {
            EnderPearl pearl = (EnderPearl) Proxy.newProxyInstance(EnderPearl.class.getClassLoader(), new Class<?>[]{EnderPearl.class},
                    (p, m, a) -> m.getName().equals("getShooter") ? right : null);
            for (boolean paused : new boolean[]{false, true}) {
                if (paused) {
                    var match = new SulfurSoccerMatch(5); match.kickOff();
                    set(area, "match", match); set(area, "gameStageEnum", GameStageEnum.PROGRESS); set(area, "paused", true);
                }
                var launch = new ProjectileLaunchEvent(pearl);
                handler.onPearlLaunch(launch);
                assertTrue(launch.isCancelled());
                var teleport = new PlayerTeleportEvent(right, new Location(null, 0, 1, 0), new Location(null, 1, 1, 0),
                        PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
                handler.onPearlTeleport(teleport);
                assertTrue(teleport.isCancelled());
                var pluginTeleport = new PlayerTeleportEvent(right, teleport.getFrom(), teleport.getTo(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                handler.onPearlTeleport(pluginTeleport);
                assertFalse(pluginTeleport.isCancelled(), "Returning a player to their spawn must remain possible while paused");
            }
        }

        @Test void pearlAndFallDamageNeverHurtParticipants() {
            DamageSource source = (DamageSource) Proxy.newProxyInstance(DamageSource.class.getClassLoader(), new Class<?>[]{DamageSource.class},
                    (p, m, a) -> null);
            for (Player player : new Player[]{right, left}) {
                var damage = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FALL, source, 5);
                handler.onDamage(damage);
                assertTrue(damage.isCancelled());
            }
            var unrelated = new EntityDamageEvent(outsider, EntityDamageEvent.DamageCause.FALL, source, 5);
            handler.onDamage(unrelated);
            assertFalse(unrelated.isCancelled());
        }

        private static void set(Object target, String name, Object value) throws Exception {
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                try { var field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); return; }
                catch (NoSuchFieldException ignored) { }
            }
            throw new NoSuchFieldException(name);
        }

        @SuppressWarnings("unchecked") private static <T extends Entity> T entity(Class<T> type) {
            UUID id = UUID.randomUUID();
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "equals" -> proxy == args[0];
                case "hashCode" -> id.hashCode();
                case "sendMessage" -> null;
                default -> throw new AssertionError(method.getName());
            });
        }
        private static final class Team extends ChampionshipTeam {
            Team(int id, UUID player) { super(id, "team" + id, "red", "", Set.of(player), null); }
        }
    }

    @Nested
    class SulfurSoccerPenaltyBarrierCases {
        @Test void directionChangesRestoreBlocksAndThinPanesStopSweptFastShotsOnlyInTheirLane() throws Exception {
            Map<Vector, BlockData> contents = new HashMap<>();
            Map<Vector, Block> blocks = new HashMap<>();
            BlockData air = data(Material.AIR, EnumSet.noneOf(BlockFace.class));
            World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class}, (p, m, a) -> {
                assertEquals("getBlockAt", m.getName());
                Vector point = new Vector((Integer) a[0], (Integer) a[1], (Integer) a[2]);
                return blocks.computeIfAbsent(point, key -> (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
                        (block, method, args) -> switch (method.getName()) {
                            case "getBlockData" -> contents.getOrDefault(key, air);
                            case "setBlockData" -> {
                                assertFalse((Boolean) args[1]);
                                contents.put(key, (BlockData) args[0]);
                                yield null;
                            }
                            case "hashCode" -> key.hashCode();
                            case "equals" -> block == args[0];
                            default -> throw new AssertionError(method.getName());
                        }));
            });
            var serverField = Bukkit.class.getDeclaredField("server"); serverField.setAccessible(true);
            Object previous = serverField.get(null);
            serverField.set(null, Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class}, (p, m, a) -> {
                assertEquals("createBlockData", m.getName());
                assertEquals(Material.GLASS_PANE, a[0]);
                return data(Material.GLASS_PANE, EnumSet.noneOf(BlockFace.class));
            }));
            try {
                var layout = SulfurSoccerPenaltyLayout.resolve(new BoundingBox(-21, 0, -10, 21, 8, 11),
                        new BoundingBox(18, 0, -3, 21, 4, 3), new BoundingBox(-21, 0, -3, -18, 4, 3), 1);
                var barrier = new SulfurSoccerPenaltyBarrier();
                barrier.select(world, layout, 1);
                for (Vector point : layout.paneBlocks(1)) {
                    assertEquals(Material.GLASS_PANE, contents.get(point).getMaterial());
                    assertEquals(Set.of(BlockFace.NORTH, BlockFace.SOUTH), ((MultipleFacing) contents.get(point)).getFaces());
                }
                BoundingBox ball = new BoundingBox(-0.5, 1, -0.5, 0.5, 2, 0.5);
                Vector from = new Vector(15, 1.5, 0), to = new Vector(20, 1.5, 0);
                assertTrue(barrier.entry(from, to, ball) < SulfurSoccerGeometry.entry(new BoundingBox(18, 0, -3, 21, 4, 3), from, to));
                assertEquals(Double.POSITIVE_INFINITY, barrier.entry(new Vector(15, 1.5, 2), new Vector(20, 1.5, 2), ball));
                barrier.select(world, layout, 0);
                for (Vector point : layout.paneBlocks(1)) assertEquals(Material.AIR, contents.get(point).getMaterial());
                for (Vector point : layout.paneBlocks(0)) assertEquals(Material.GLASS_PANE, contents.get(point).getMaterial());
                assertEquals(Double.POSITIVE_INFINITY, barrier.entry(from, to, ball));
                barrier.clear();
                assertTrue(contents.values().stream().allMatch(d -> d.getMaterial() == Material.AIR));
                assertEquals(Double.POSITIVE_INFINITY, barrier.entry(from, to, ball));
                assertDoesNotThrow(barrier::clear);
            } finally {
                serverField.set(null, previous);
            }
        }

        private static MultipleFacing data(Material material, EnumSet<BlockFace> faces) {
            return (MultipleFacing) Proxy.newProxyInstance(MultipleFacing.class.getClassLoader(), new Class<?>[]{MultipleFacing.class},
                    (p, m, a) -> switch (m.getName()) {
                        case "clone" -> data(material, faces.clone());
                        case "getMaterial" -> material;
                        case "getFaces" -> Set.copyOf(faces);
                        case "setFace" -> { if ((Boolean) a[1]) faces.add((BlockFace) a[0]); else faces.remove((BlockFace) a[0]); yield null; }
                        default -> throw new AssertionError(m.getName());
                    });
        }
    }

    @Nested
    class SulfurSoccerTeamColorsCases {
        private final Map<String, Material> blocks = new HashMap<>();
        private SulfurSoccerConfig config;
        private World world;

        @BeforeEach void setup() throws Exception {
            var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            var plugin = (ChampionshipsCore) ((sun.misc.Unsafe) field.get(null)).allocateInstance(ChampionshipsCore.class);
            config = new SulfurSoccerConfig(plugin, "soccer");
            config.setRightTeamColorBlocks(List.of("-24:88:-8"));
            config.setLeftTeamColorBlocks(List.of("24:88:8"));
            blocks.put("-24:88:-8", Material.LIGHT_GRAY_CONCRETE);
            blocks.put("24:88:8", Material.LIGHT_GRAY_CONCRETE);
            blocks.put("-24:89:-8", Material.OAK_STAIRS);
            blocks.put("0:85:0", Material.WHITE_CONCRETE);
            world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class}, (proxy, method, args) -> {
                if (!method.getName().equals("getBlockAt")) throw new AssertionError(method.getName());
                String key = args[0] + ":" + args[1] + ":" + args[2];
                return Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class}, (p, m, a) -> switch (m.getName()) {
                    case "getType" -> blocks.getOrDefault(key, Material.AIR);
                    case "setType" -> {
                        assertFalse((Boolean) a[1], "Recolouring must not trigger stair or lighting physics");
                        blocks.put(key, (Material) a[0]);
                        yield null;
                    }
                    default -> throw new AssertionError(m.getName());
                });
            });
        }

        @Test void recoloursBySideAcrossSuccessiveMatchesIncludingSwappedAndSharedColours() {
            SulfurSoccerArea.applyTeamColors(world, config, "orange", "purple");
            assertEquals(Material.ORANGE_CONCRETE, blocks.get("-24:88:-8"));
            assertEquals(Material.PURPLE_CONCRETE, blocks.get("24:88:8"));
            SulfurSoccerArea.applyTeamColors(world, config, "purple", "orange");
            assertEquals(Material.PURPLE_CONCRETE, blocks.get("-24:88:-8"));
            assertEquals(Material.ORANGE_CONCRETE, blocks.get("24:88:8"));
            SulfurSoccerArea.applyTeamColors(world, config, "light_blue", "light_blue");
            assertEquals(Material.LIGHT_BLUE_CONCRETE, blocks.get("-24:88:-8"));
            assertEquals(Material.LIGHT_BLUE_CONCRETE, blocks.get("24:88:8"));
            assertEquals(Material.OAK_STAIRS, blocks.get("-24:89:-8"));
            assertEquals(Material.WHITE_CONCRETE, blocks.get("0:85:0"));
        }

        @Test void invalidTeamColourFailsBeforeEitherSideChanges() {
            assertThrows(IllegalArgumentException.class, () -> SulfurSoccerArea.applyTeamColors(world, config, "orange", "unknown"));
            assertEquals(Material.LIGHT_GRAY_CONCRETE, blocks.get("-24:88:-8"));
            assertEquals(Material.LIGHT_GRAY_CONCRETE, blocks.get("24:88:8"));
        }

        @Test void skipsRemovedAccentsAndNeverReplacesWoodenStairs() {
            config.setRightTeamColorBlocks(List.of("-24:88:-8", "-24:89:-8", "-25:88:-8"));
            SulfurSoccerArea.applyTeamColors(world, config, "lime", "red");
            assertEquals(Material.OAK_STAIRS, blocks.get("-24:89:-8"));
            assertFalse(blocks.containsKey("-25:88:-8"));
        }

        @Test void recoloursOnlySmoothQuartzAroundEachGoalAndRestoresTheTemplate() {
            blocks.put("-2:2:-2", Material.SMOOTH_QUARTZ);
            blocks.put("3:2:3", Material.SMOOTH_QUARTZ);
            blocks.put("8:2:8", Material.SMOOTH_QUARTZ);
            var rightGoal = new BoundingBox(-1, 1, -1, 2, 3, 2);
            var leftGoal = new BoundingBox(2, 1, 2, 5, 3, 5);

            var rightOriginal = SulfurSoccerArea.applyGoalColors(world, rightGoal, Material.RED_CONCRETE);
            var leftOriginal = SulfurSoccerArea.applyGoalColors(world, leftGoal, Material.BLUE_CONCRETE);
            assertEquals(Material.RED_CONCRETE, blocks.get("-2:2:-2"));
            assertEquals(Material.BLUE_CONCRETE, blocks.get("3:2:3"));
            assertEquals(Material.SMOOTH_QUARTZ, blocks.get("8:2:8"));

            SulfurSoccerArea.restoreGoalColors(world, rightOriginal);
            SulfurSoccerArea.restoreGoalColors(world, leftOriginal);
            assertEquals(Material.SMOOTH_QUARTZ, blocks.get("-2:2:-2"));
            assertEquals(Material.SMOOTH_QUARTZ, blocks.get("3:2:3"));
        }
    }
}
