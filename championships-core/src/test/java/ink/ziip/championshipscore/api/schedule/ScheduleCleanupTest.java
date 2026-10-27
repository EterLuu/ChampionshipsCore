package ink.ziip.championshipscore.api.schedule;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.battlebox.BattleBoxScheduleManager;
import ink.ziip.championshipscore.api.schedule.hotycodydusky.HotyCodyDuskyScheduleManager;
import ink.ziip.championshipscore.api.schedule.laserbox.LaserBoxScheduleManager;
import ink.ziip.championshipscore.api.schedule.parkourtag.ParkourTagScheduleManager;

import org.bukkit.Server;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

class ScheduleCleanupTest {
    @ParameterizedTest
    @EnumSource(
            value = GameTypeEnum.class,
            names = {"BattleBox", "ParkourTag", "HotyCodyDusky", "LaserBox"})
    void stoppingCustomScheduleReleasesBothKindsOfHoldsAndCancelsClocks(GameTypeEnum game)
            throws Exception {
        var f = new Fixture(game);
        f.manager.getClass().getMethod("endSchedule").invoke(f.manager);
        f.assertReleased();
    }

    @ParameterizedTest
    @EnumSource(
            value = GameTypeEnum.class,
            names = {"BattleBox", "ParkourTag"})
    void failedPairingStartUsesTheSameCompleteCleanup(GameTypeEnum game) throws Exception {
        var f = new Fixture(game);
        var abort = f.manager.getClass().getDeclaredMethod("abortSchedule", String.class);
        abort.setAccessible(true);
        abort.invoke(f.manager, "fixture failed start");
        f.assertReleased();
    }

    private static final class Fixture {
        final GameTypeEnum game;
        final Object manager;
        final Game games;
        final AtomicInteger cancellations = new AtomicInteger();

        Fixture(GameTypeEnum game) throws Exception {
            this.game = game;
            var plugin = allocate(ChampionshipsCore.class);
            var scheduler = proxy(BukkitScheduler.class, (p, m, a) -> null);
            var server =
                    proxy(
                            Server.class,
                            (p, m, a) -> m.getName().equals("getScheduler") ? scheduler : null);
            set(plugin, "server", server);
            set(plugin, "logger", Logger.getAnonymousLogger());
            games = allocate(Game.class);
            games.spectators = new ArrayList<>();
            games.participants = new ArrayList<>();
            set(plugin, "gameManager", games);
            set(plugin, "scheduleManager", new ScheduleManager(plugin));
            manager =
                    switch (game) {
                        case BattleBox -> new BattleBoxScheduleManager(plugin);
                        case ParkourTag -> new ParkourTagScheduleManager(plugin);
                        case HotyCodyDusky -> new HotyCodyDuskyScheduleManager(plugin);
                        case LaserBox -> new LaserBoxScheduleManager(plugin);
                        default -> throw new AssertionError(game);
                    };
            BukkitTask task =
                    proxy(
                            BukkitTask.class,
                            (p, m, a) -> {
                                if (m.getName().equals("cancel")) cancellations.incrementAndGet();
                                return null;
                            });
            set(manager, "enabled", true);
            if (game == GameTypeEnum.LaserBox) set(manager, "countdownTask", task);
            else {
                set(manager, "firstStartTask", task);
                set(manager, "startTask", task);
            }
        }

        void assertReleased() throws Exception {
            assertEquals(List.of(game), games.participants);
            assertEquals(List.of(game), games.spectators);
            assertEquals(game == GameTypeEnum.LaserBox ? 1 : 2, cancellations.get());
            assertFalse((boolean) manager.getClass().getMethod("isEnabled").invoke(manager));
        }
    }

    private static final class Game extends GameManager {
        List<GameTypeEnum> spectators, participants;

        private Game() {
            super(null);
        }

        @Override
        public void releaseEventSpectatorsForGame(GameTypeEnum game) {
            spectators.add(game);
        }

        @Override
        public void releaseRoundTransitionHolds(GameTypeEnum game) {
            participants.add(game);
        }
    }
}
