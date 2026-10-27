package ink.ziip.championshipscore.api.schedule;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.daily.DailyManager;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.instance.CountdownBlockDisappearance;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.parkourwarrior.runtime.ParkourWarriorTeamArea;

import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class EventRoundSettlementTest {
    @Test
    void currentFinishedRunsReleaseOnceBeforeAdvancing() throws Exception {
        Area first = area(), second = area();
        var settlement = new EventRoundSettlement(List.of(first, second));
        AtomicInteger advanced = new AtomicInteger();
        settlement.complete(
                true,
                () -> {
                    assertEquals(1, first.resets);
                    assertEquals(1, second.resets);
                    advanced.incrementAndGet();
                });
        settlement.complete(true, advanced::incrementAndGet);
        assertEquals(1, advanced.get());
        assertTrue(first.held);
        assertTrue(second.held);
    }

    @Test
    void cancellationAlsoRejectsACallbackAlreadyQueuedForExecution() throws Exception {
        Area area = area();
        var settlement = new EventRoundSettlement(List.of(area));
        AtomicInteger cancellations = new AtomicInteger();
        BukkitTask task =
                proxy(
                        BukkitTask.class,
                        (p, m, a) -> {
                            if (m.getName().equals("cancel")) cancellations.incrementAndGet();
                            return null;
                        });
        settlement.cancel();
        settlement.attach(task);
        settlement.complete(true, () -> fail("Cancelled settlement advanced the event"));
        assertEquals(1, cancellations.get());
        assertEquals(0, area.resets);
    }

    @Test
    void replacedDisposedAndReopenedRunsCannotBeReleased() throws Exception {
        for (String state : List.of("generation", "disposed", "stage")) {
            Area area = area();
            var settlement = new EventRoundSettlement(List.of(area));
            switch (state) {
                case "generation" -> set(area, "startGeneration", 2L);
                case "disposed" -> set(area, "disposed", true);
                case "stage" -> set(area, "gameStageEnum", GameStageEnum.PROGRESS);
            }
            settlement.complete(true, () -> fail(state));
            assertEquals(0, area.resets, state);
        }
    }

    @Test
    void partialReplacementReleasesUnchangedArenasWithoutStrandingRoundHolds() throws Exception {
        Area original = area(), replaced = area();
        var settlement = new EventRoundSettlement(List.of(original, replaced));
        set(replaced, "startGeneration", 3L);
        settlement.complete(true, () -> fail("An incomplete round advanced"));
        assertEquals(1, original.resets);
        assertFalse(original.held);
        assertEquals(0, replaced.resets);
    }

    private static Area area() throws Exception {
        Area area = allocate(Area.class);
        var plugin = allocate(ChampionshipsCore.class);
        set(plugin, "gameManager", allocate(Game.class));
        var daily = allocate(DailyManager.class);
        set(daily, "sessionByInstance", new IdentityHashMap<>());
        set(plugin, "dailyManager", daily);
        set(area, "plugin", plugin);
        set(area, "gameStageEnum", GameStageEnum.END);
        set(area, "postGamePending", true);
        var blocks = allocate(CountdownBlockDisappearance.class);
        set(blocks, "snapshots", new ArrayList<>());
        set(area, "countdownBlockDisappearance", blocks);
        return area;
    }

    private static final class Area extends ParkourWarriorTeamArea {
        int resets;
        boolean held;

        private Area() {
            super(null, null);
        }

        @Override
        public Collection<UUID> getParticipantUniqueIds() {
            return List.of();
        }

        @Override
        public void resetGame() {
            resets++;
        }

        @Override
        public void releaseAllSpectators() {}
    }

    private static final class Game extends GameManager {
        private Game() {
            super(null);
        }

        @Override
        public void holdParticipantsForNextRound(BaseGameInstance area, Collection<UUID> players) {
            ((Area) area).held = true;
        }

        @Override
        public void holdSpectatorsForNextRound(BaseGameInstance area) {}

        @Override
        public void releaseInstanceParticipants(BaseGameInstance area) {}
    }
}
