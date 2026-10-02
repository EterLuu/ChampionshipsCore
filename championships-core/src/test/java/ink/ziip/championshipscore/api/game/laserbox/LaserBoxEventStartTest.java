package ink.ziip.championshipscore.api.game.laserbox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.object.game.GameRunMode;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.schedule.TwoVTwoVector;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.api.team.TeamManager;
import ink.ziip.championshipscore.api.visibility.PlayerVisibilityManager;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ConfigurationStateExtension.class)
class LaserBoxEventStartTest {
    @Test void allPairingsStartInEventModeBehindOnePreloadGateEvenWithOfflineMembers() throws Exception {
        withFixture(fixture -> {
            var instances = fixture.manager.joinLaserBoxInstances("laserbox", fixture.pairs, true, GameRunMode.EVENT);
            assertEquals(fixture.copies, instances);
            Object gate = field(instances.getFirst().getClass(), "coordinatedStartGate").get(instances.getFirst());
            assertSame(gate, field(instances.getLast().getClass(), "coordinatedStartGate").get(instances.getLast()));
            assertFalse(((CompletableFuture<?>) gate).isDone());
            for (int i = 0; i < instances.size(); i++) {
                var area = instances.get(i);
                assertEquals(GameStageEnum.LOADING, area.getGameStageEnum());
                assertTrue(area.isEventRun());
                assertTrue((boolean) field(area.getClass(), "introductionEnabledForNextStart").get(area));
                assertSame(fixture.pairs.get(i).getTeamOne(), area.getRightChampionshipTeam());
                assertSame(fixture.pairs.get(i).getTeamTwo(), area.getLeftChampionshipTeam());
                for (UUID id : area.getParticipantUniqueIds()) assertSame(area, fixture.manager.getBasePlayerArea(id));
            }
            Chunk chunk = proxy(Chunk.class, (p, m, a) -> {
                if (m.getName().equals("addPluginChunkTicket")) return true;
                throw new AssertionError(m.getName());
            });
            fixture.preloads.subList(0, fixture.preloads.size() - 1).forEach(future -> future.complete(chunk));
            assertFalse(((CompletableFuture<?>) gate).isDone());
            fixture.preloads.getLast().complete(chunk);
            assertTrue(instances.stream().allMatch(area -> area.getStartPreloadFuture().isDone()));
            assertFalse(((CompletableFuture<?>) gate).isDone());
            // Main-thread callbacks are queued, including the final release of the shared gate.
            assertFalse(fixture.queued.isEmpty());
            fixture.queued.getLast().run();
            assertTrue(((CompletableFuture<?>) gate).isDone());
        });
    }

    @Test void insufficientCopiesAndDuplicateTeamsDoNotReserveAnyParticipants() throws Exception {
        withFixture(fixture -> {
            assertNull(fixture.manager.joinLaserBoxInstances("laserbox", List.of(fixture.pairs.getFirst(),
                    fixture.pairs.getFirst()), true, GameRunMode.EVENT));
            set(fixture.copies.getLast(), "gameStageEnum", GameStageEnum.PROGRESS);
            assertNull(fixture.manager.joinLaserBoxInstances("laserbox", fixture.pairs, true, GameRunMode.EVENT));
            for (var pair : fixture.pairs) for (var team : List.of(pair.getTeamOne(), pair.getTeamTwo()))
                for (UUID id : team.getMembers()) assertNull(fixture.manager.getBasePlayerArea(id));
            assertTrue(fixture.preloads.isEmpty());
        });
    }

    private record Fixture(GameManager manager, LaserBoxConfig config, List<LaserBoxArea> copies,
                           List<TwoVTwoVector> pairs, List<CompletableFuture<Chunk>> preloads, List<Runnable> queued) { }

    private static void withFixture(CheckedConsumer action) throws Exception {
        Field serverField = field(Bukkit.class, "server");
        Object previousServer = serverField.get(null);
        try {
            var plugin = allocate(ChampionshipsCore.class);
            set(plugin, "logger", Logger.getAnonymousLogger());
            List<CompletableFuture<Chunk>> preloads = new ArrayList<>();
            List<Runnable> queued = new ArrayList<>();
            World world = proxy(World.class, (p, m, a) -> switch (m.getName()) {
                case "getName" -> "laserbox";
                case "getChunkAtAsync" -> {
                    CompletableFuture<Chunk> future = new CompletableFuture<>(); preloads.add(future); yield future;
                }
                case "equals" -> p == a[0];
                case "hashCode" -> System.identityHashCode(p);
                default -> throw new AssertionError(m.getName());
            });
            BukkitTask task = proxy(BukkitTask.class, (p, m, a) -> null);
            BukkitScheduler scheduler = proxy(BukkitScheduler.class, (p, m, a) -> {
                if (m.getName().equals("runTask")) { queued.add((Runnable) a[1]); return task; }
                throw new AssertionError(m.getName());
            });
            Server server = proxy(Server.class, (p, m, a) -> switch (m.getName()) {
                case "getWorld" -> world;
                case "getPlayer" -> null;
                case "isPrimaryThread" -> false;
                case "getScheduler" -> scheduler;
                default -> throw new AssertionError(m.getName());
            });
            serverField.set(null, server);
            set(plugin, "server", server);
            GameManager manager = allocate(GameManager.class);
            set(manager, "plugin", plugin);
            set(manager, "enabledGames", Set.of(GameTypeEnum.LaserBox));
            for (String name : List.of("teamStatus", "playerStatus", "playerSpectatorStatus",
                    "roundTransitionHolds", "spectatorTransitionHolds")) set(manager, name, new ConcurrentHashMap<>());
            set(plugin, "gameManager", manager);
            set(plugin, "visibilityManager", new PlayerVisibilityManager(plugin));
            TeamManager teams = allocate(TeamManager.class);
            set(teams, "cachedTeams", new ConcurrentHashMap<>());
            set(plugin, "teamManager", teams);
            PrepareSessionManager prepare = allocate(PrepareSessionManager.class);
            set(prepare, "plugin", plugin); set(prepare, "mapLocks", Map.of());
            set(plugin, "prepareSessionManager", prepare);
            LaserBoxConfig config = new LaserBoxConfig(plugin, "laserbox");
            set(config, "configuration", new YamlConfiguration());
            config.bindConfiguredWorld("laserbox");
            config.setAreaName("laserbox"); config.setCopyCount(2);
            config.setAreaPos1(new Vector(0, 0, 0)); config.setAreaPos2(new Vector(31, 20, 31));
            config.setRightSpawnPoint(new Location(world, 1, 2, 1));
            config.setLeftSpawnPoint(new Location(world, 2, 2, 2));
            config.setSpectatorSpawnPoint(new Location(world, 3, 2, 3));
            config.setSupplyPoints(List.of("laserbox:4:2:4:0:0"));
            set(config, "preparePublished", true); set(config, "prepareDirty", false);
            List<LaserBoxArea> copies = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                var area = allocate(LaserBoxArea.class);
                set(area, "plugin", plugin); set(area, "scheduler", scheduler); set(area, "gameConfig", config);
                set(area, "gameTypeEnum", GameTypeEnum.LaserBox); set(area, "gameStageEnum", GameStageEnum.WAITING);
                set(area, "copyIndex", index); set(area, "startChunkTickets", new HashSet<>());
                copies.add(area);
            }
            LaserBoxManager maps = new LaserBoxManager(plugin);
            set(maps, "areas", new ConcurrentHashMap<>(Map.of("laserbox", copies.getFirst())));
            set(maps, "instancesByMap", new ConcurrentHashMap<>(Map.of("laserbox", new ArrayList<>(copies))));
            set(manager, "laserBoxManager", maps); set(manager, "areaManagers", Map.of(GameTypeEnum.LaserBox, maps));
            MessageConfig.GAME_LASER_BOX = "LaserBox";
            var pairs = List.of(new TwoVTwoVector(new TestTeam(1), new TestTeam(2)),
                    new TwoVTwoVector(new TestTeam(3), new TestTeam(4)));
            action.accept(new Fixture(manager, config, copies, pairs, preloads, queued));
        } finally { serverField.set(null, previousServer); }
    }

    private interface CheckedConsumer { void accept(Fixture fixture) throws Exception; }
    private static final class TestTeam extends ChampionshipTeam {
        TestTeam(int id) { super(id, "team-" + id, "red", "#FFFFFF", Set.of(new UUID(id, 1)), null); }
    }
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
    private static Field field(Class<?> type, String name) throws Exception {
        for (; type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void set(Object target, String name, Object value) throws Exception { field(target.getClass(), name).set(target, value); }
    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null)).allocateInstance(type));
    }
}
