package ink.ziip.championshipscore.api.game.dodgebolt.runtime;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.model.GameStageEnum;

import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class DodgeboltTransitionTest {
    private final List<Runnable> tasks = new ArrayList<>();

    @Test
    void onlyNewestRoundTransitionCanRun() throws Exception {
        var area = stoppingArea();
        schedule(area, () -> fail("obsolete restart"), 20L);
        AtomicInteger current = new AtomicInteger();
        schedule(area, current::incrementAndGet, 60L);
        tasks.forEach(Runnable::run);
        assertEquals(1, current.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"end", "dispose", "new-run"})
    void endedDisposedOrReusedArenaRejectsQueuedRestartAndChampionTasks(String state)
            throws Exception {
        var area = stoppingArea();
        schedule(area, () -> fail(state), 20L);
        switch (state) {
            case "end" -> set(area, "gameStageEnum", GameStageEnum.END);
            case "dispose" -> set(area, "disposed", true);
            case "new-run" -> set(area, "startGeneration", 1L);
        }
        tasks.getFirst().run();
    }

    private DodgeboltArea stoppingArea() throws Exception {
        var area = allocate(DodgeboltArea.class);
        set(area, "gameStageEnum", GameStageEnum.STOPPING);
        set(
                area,
                "scheduler",
                proxy(
                        BukkitScheduler.class,
                        (p, m, a) -> {
                            tasks.add((Runnable) a[1]);
                            return proxy(BukkitTask.class, (t, n, b) -> null);
                        }));
        return area;
    }

    private static void schedule(DodgeboltArea area, Runnable transition, long delay)
            throws Exception {
        var method =
                DodgeboltArea.class.getDeclaredMethod(
                        "scheduleRoundTransition", Runnable.class, long.class);
        method.setAccessible(true);
        method.invoke(area, transition, delay);
    }
}
