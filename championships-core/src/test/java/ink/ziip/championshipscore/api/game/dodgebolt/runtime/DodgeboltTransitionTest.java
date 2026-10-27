package ink.ziip.championshipscore.api.game.dodgebolt.runtime;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.model.GameStageEnum;

import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class DodgeboltTransitionTest {
    @Test
    void onlyNewestRoundTransitionCanRun() throws Exception {
        var area = allocate(DodgeboltArea.class);
        List<Runnable> tasks = new ArrayList<>();
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
        var schedule =
                DodgeboltArea.class.getDeclaredMethod(
                        "scheduleRoundTransition", Runnable.class, long.class);
        schedule.setAccessible(true);
        schedule.invoke(area, (Runnable) () -> fail("obsolete restart"), 20L);
        AtomicInteger current = new AtomicInteger();
        schedule.invoke(area, (Runnable) current::incrementAndGet, 60L);
        tasks.forEach(Runnable::run);
        assertEquals(1, current.get());
    }

    @Test
    void endedDisposedOrReusedArenaRejectsQueuedRestartAndChampionTasks() throws Exception {
        for (String state : List.of("end", "dispose", "new-run")) {
            var area = allocate(DodgeboltArea.class);
            List<Runnable> tasks = new ArrayList<>();
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
            var method =
                    DodgeboltArea.class.getDeclaredMethod(
                            "scheduleRoundTransition", Runnable.class, long.class);
            method.setAccessible(true);
            method.invoke(area, (Runnable) () -> fail(state), 20L);
            switch (state) {
                case "end" -> set(area, "gameStageEnum", GameStageEnum.END);
                case "dispose" -> set(area, "disposed", true);
                case "new-run" -> set(area, "startGeneration", 1L);
            }
            tasks.getFirst().run();
        }
    }
}
