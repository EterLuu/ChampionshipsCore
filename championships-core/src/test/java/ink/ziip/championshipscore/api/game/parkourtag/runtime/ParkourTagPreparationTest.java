package ink.ziip.championshipscore.api.game.parkourtag.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.parkourtag.config.ParkourTagConfig;
import ink.ziip.championshipscore.api.game.parkourtag.geometry.ParkourTagGeometry;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

class ParkourTagPreparationTest {
    @Test
    void spendsTwentySecondsAtTheTeamPreparationSpotsBeforeEnteringTheCages() throws Exception {
        Fixture fixture = fixture();
        fixture.area.startGamePreparation();
        assertEquals(fixture.match.getRightPrepareSpot(), fixture.right.teleported);
        assertEquals(fixture.match.getLeftPrepareSpot(), fixture.left.teleported);
        assertEquals(GameStageEnum.PREPARATION, fixture.area.getGameStageEnum());
        assertEquals(0, fixture.area.starts);
        fixture.match.setRightAreaChaser(new UUID(1, 1));
        fixture.match.setLeftAreaChaser(new UUID(2, 1));
        for (int second = 0; second < 20; second++) {
            fixture.tick.get().run();
            assertEquals(0, fixture.area.starts);
        }
        assertEquals(0, fixture.area.getTimer());
        fixture.tick.get().run();
        assertEquals(1, fixture.area.starts);
        assertEquals(GameStageEnum.COUNTDOWN, fixture.area.getGameStageEnum());
        assertTrue(fixture.cancelled.get());
        assertEquals(new UUID(1, 1), fixture.area.rightChoice);
        assertEquals(new UUID(2, 1), fixture.area.leftChoice);
        assertEquals(20, fixture.area.countdowns.getFirst());
        assertEquals(0, fixture.area.countdowns.getLast());
    }

    @Test
    void anInterruptedPreparationCannotStartTheGameLater() throws Exception {
        Fixture fixture = fixture();
        fixture.area.startGamePreparation();
        fixture.area.setGameStageEnum(GameStageEnum.END);
        fixture.tick.get().run();
        assertTrue(fixture.cancelled.get());
        assertEquals(0, fixture.area.starts);
    }

    @Test
    void aReconnectDuringSelectionReturnsToTheTeamPreparationSpot() throws Exception {
        Fixture fixture = fixture();
        List<Location> teleports = new ArrayList<>();
        List<GameMode> modes = new ArrayList<>();
        Player player =
                (Player)
                        Proxy.newProxyInstance(
                                Player.class.getClassLoader(),
                                new Class<?>[] {Player.class},
                                (proxy, method, arguments) ->
                                        switch (method.getName()) {
                                            case "getUniqueId" -> new UUID(1, 1);
                                            case "teleport" -> {
                                                teleports.add((Location) arguments[0]);
                                                yield true;
                                            }
                                            case "setGameMode" -> {
                                                modes.add((GameMode) arguments[0]);
                                                yield null;
                                            }
                                            default -> throw new AssertionError(method.getName());
                                        });
        fixture.area.handlePlayerJoin(new PlayerJoinEvent(player, (String) null));
        assertEquals(List.of(fixture.match.getRightPrepareSpot()), teleports);
        assertEquals(List.of(GameMode.ADVENTURE), modes);
        fixture.match.setRightAreaChaser(player.getUniqueId());
        fixture.area.setGameStageEnum(GameStageEnum.COUNTDOWN);
        fixture.area.handlePlayerJoin(new PlayerJoinEvent(player, (String) null));
        assertEquals(fixture.match.getRightAreaChaserSpawn(), teleports.getLast());
    }

    private record Fixture(
            TestArea area,
            TestTeam right,
            TestTeam left,
            ParkourTagMatch match,
            AtomicReference<Runnable> tick,
            AtomicBoolean cancelled) {}

    private static Fixture fixture() throws Exception {
        var plugin = allocate(ChampionshipsCore.class);
        var area = allocate(TestArea.class);
        set(area, "plugin", plugin);
        set(area, "gameTypeEnum", GameTypeEnum.ParkourTag);
        set(area, "gameStageEnum", GameStageEnum.PREPARATION);
        set(area, "countdowns", new ArrayList<>());
        set(area, "temporaryChaserGlowTasks", new HashMap<>());
        var tick = new AtomicReference<Runnable>();
        var cancelled = new AtomicBoolean();
        BukkitTask task =
                (BukkitTask)
                        Proxy.newProxyInstance(
                                BukkitTask.class.getClassLoader(),
                                new Class<?>[] {BukkitTask.class},
                                (proxy, method, arguments) -> {
                                    if (method.getName().equals("cancel")) cancelled.set(true);
                                    return null;
                                });
        BukkitScheduler scheduler =
                (BukkitScheduler)
                        Proxy.newProxyInstance(
                                BukkitScheduler.class.getClassLoader(),
                                new Class<?>[] {BukkitScheduler.class},
                                (proxy, method, arguments) -> {
                                    if (method.getName().equals("runTaskTimer")) {
                                        assertEquals(0L, arguments[2]);
                                        assertEquals(20L, arguments[3]);
                                        tick.set((Runnable) arguments[1]);
                                        return task;
                                    }
                                    if (method.getName().equals("runTaskLater")) return task;
                                    throw new AssertionError(method.getName());
                                });
        set(area, "scheduler", scheduler);
        var config = new ParkourTagConfig(plugin, "arena");
        config.setRightPrepareSpot(new Location(null, 1, 64, 1));
        config.setLeftPrepareSpot(new Location(null, 101, 64, 1));
        config.setRightAreaChaserSpawnPoint(new Location(null, 10, 64, 10));
        config.setLeftAreaChaserSpawnPoint(new Location(null, 110, 64, 10));
        config.setAreaPos1(new Vector());
        config.setAreaPos2(new Vector(200, 100, 100));
        config.setLeftAreaAreaPos1(new Vector());
        config.setLeftAreaAreaPos2(new Vector(50, 100, 100));
        config.setRightAreaAreaPos1(new Vector(100, 0, 0));
        config.setRightAreaAreaPos2(new Vector(150, 100, 100));
        set(area, "gameConfig", config);
        var right = new TestTeam(1);
        var left = new TestTeam(2);
        var match = new ParkourTagMatch(0, right, left, ParkourTagGeometry.from(config));
        set(area, "match", match);
        return new Fixture(area, right, left, match, tick, cancelled);
    }

    private static final class TestArea extends ParkourTagArea {
        int starts;
        UUID rightChoice, leftChoice;
        List<Integer> countdowns;

        private TestArea() {
            super(null, null);
        }

        @Override
        protected void startGameIntroduction(Runnable onComplete) {
            onComplete.run();
        }

        @Override
        public void changeGameModelForAllGamePlayers(GameMode mode) {}

        @Override
        public void resetPlayerHealthFoodEffectLevelInventory() {}

        @Override
        protected void announceGamePreparation(String message, String title, String subtitle) {}

        @Override
        protected void showPreparationCountdown(int seconds) {
            countdowns.add(seconds);
        }

        @Override
        public boolean notAreaPlayer(Player player) {
            return false;
        }

        @Override
        protected void startGameProgress() {
            starts++;
            rightChoice = currentMatch().getRightAreaChaser();
            leftChoice = currentMatch().getLeftAreaChaser();
            setGameStageEnum(GameStageEnum.COUNTDOWN);
        }
    }

    private static final class TestTeam extends ChampionshipTeam {
        Location teleported;

        private TestTeam(int id) {
            super(id, "team-" + id, "red", "#FFFFFF", Set.of(new UUID(id, 1)), null);
        }

        @Override
        public void teleportAllPlayers(Location target) {
            teleported = target;
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        for (; type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        field(target.getClass(), name).set(target, value);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(
                ((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null))
                        .allocateInstance(type));
    }
}
