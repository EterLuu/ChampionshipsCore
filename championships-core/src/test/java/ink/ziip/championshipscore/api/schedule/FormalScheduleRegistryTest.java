package ink.ziip.championshipscore.api.schedule;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.finale.FinaleGameRegistry;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;

import org.bukkit.Server;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

class FormalScheduleRegistryTest {
    @Test
    void everyGameHasExactlyOneOrdinaryOrFinaleRegistration() {
        var ordinary = FormalScheduleRegistry.supportedGames();
        var finale = FinaleGameRegistry.gameTypes();
        assertTrue(java.util.Collections.disjoint(ordinary, finale));
        var supported = EnumSet.copyOf(ordinary);
        supported.addAll(finale);
        assertEquals(EnumSet.allOf(GameTypeEnum.class), supported);
    }

    @Test
    void everyOrdinaryGameRoutesStartFailureRoundStateAndStopToItsRegisteredOwner()
            throws Exception {
        var plugin = allocate(ChampionshipsCore.class);
        set(plugin, "server", proxy(Server.class, (p, m, a) -> null));
        var manager = new ScheduleManager(plugin);
        for (var game : FormalScheduleRegistry.supportedGames()) {
            var schedule = new RecordingSchedule();
            set(manager, "schedules", java.util.Map.of(game, schedule));
            assertEquals(
                    ScheduleManager.EventAction.UNAVAILABLE,
                    manager.startOrStopFormalEvent(game),
                    game.name());
            assertEquals("rejected", manager.getFormalEventStartFailure(game));
            assertFalse(manager.isFormalEventRunning(game));
            schedule.accept = true;
            assertEquals(
                    ScheduleManager.EventAction.STARTED,
                    manager.startOrStopFormalEvent(game),
                    game.name());
            assertTrue(manager.isFormalEventRunning(game));
            assertTrue(manager.hasNextRound(game));
            manager.endGameSchedule(game);
            assertEquals(1, schedule.stops);
            assertFalse(manager.isFormalEventRunning(game));
            assertFalse(manager.hasNextRound(game));
            manager.endGameSchedule(game);
            assertEquals(1, schedule.stops);
        }
    }

    private static final class RecordingSchedule implements FormalSchedule {
        boolean accept;
        boolean enabled;
        int stops;

        public void load() {}

        public void unload() {}

        public boolean startGame() {
            enabled = accept;
            return enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public boolean hasNextRound() {
            return enabled;
        }

        public void endSchedule() {
            stops++;
            enabled = false;
        }

        public String startFailureReason() {
            return "rejected";
        }
    }

    @Test
    void loadOwnsOneInstancePerGameAndUnloadReleasesTheRegistry() throws Exception {
        var plugin = allocate(ChampionshipsCore.class);
        var scheduler = proxy(BukkitScheduler.class, (p, m, a) -> null);
        set(
                plugin,
                "server",
                proxy(
                        Server.class,
                        (p, m, a) -> m.getName().equals("getScheduler") ? scheduler : null));
        var manager = new ScheduleManager(plugin);
        set(plugin, "scheduleManager", manager);
        manager.load();
        assertNotNull(manager.getBattleBoxScheduleManager());
        var first = manager.getBattleBoxScheduleManager();
        for (var game : FormalScheduleRegistry.supportedGames()) {
            assertTrue(manager.supportsFormalEvent(game), game.name());
            assertFalse(manager.isFormalEventRunning(game), game.name());
            assertFalse(manager.hasNextRound(game), game.name());
        }
        manager.unload();
        assertNull(manager.getBattleBoxScheduleManager());
        manager.load();
        assertNotSame(first, manager.getBattleBoxScheduleManager());
        manager.unload();
    }
}
