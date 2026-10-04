package ink.ziip.championshipscore.api.game.buildmart.mechanics;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.buildmart.model.BuildMartMaterialZone;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class BuildMartMaterialRefillSchedulerTest {
    private final TestClock clock = new TestClock();
    private final List<BuildMartMaterialZone> restored = new ArrayList<>();
    private final BuildMartMaterialRefillScheduler refills =
            new BuildMartMaterialRefillScheduler(clock.scheduler, null, restored::add);
    private final BuildMartMaterialZone first = zone(0);
    private final BuildMartMaterialZone second = zone(10);

    @Test
    void untouchedZonesNeverRefillAndAHarvestRefillsExactlyOnceAfterTwentySeconds() {
        clock.advance(2400);
        assertTrue(restored.isEmpty());

        refills.onHarvest(first);
        clock.advance(399);
        assertTrue(restored.isEmpty());
        clock.advance(1);
        assertEquals(List.of(first), restored);
        clock.advance(2400);
        assertEquals(List.of(first), restored);

        refills.onHarvest(first);
        clock.advance(400);
        assertEquals(List.of(first, first), restored);
    }

    @Test
    void repeatedHarvestResetsOnlyThatZonesDeadline() {
        refills.onHarvest(first);
        clock.advance(100);
        refills.onHarvest(second);
        clock.advance(299);
        // Config reads create new zone objects; the saved snapshot id identifies the same zone.
        refills.onHarvest(
                new BuildMartMaterialZone(first.snapshotId(), first.pos1(), first.pos2()));
        clock.advance(1);
        assertTrue(restored.isEmpty());
        clock.advance(100);
        assertEquals(List.of(second), restored);
        clock.advance(298);
        assertEquals(List.of(second), restored);
        clock.advance(1);
        assertEquals(List.of(second, first), restored);
    }

    @Test
    void cancelledCallbacksCannotRefillOrConsumeANewerHarvest() {
        refills.onHarvest(first);
        TestTask old = clock.tasks.getFirst();
        clock.advance(200);
        refills.onHarvest(first);
        assertTrue(old.isCancelled());
        old.callback.run();
        assertTrue(restored.isEmpty());
        clock.advance(400);
        assertEquals(List.of(first), restored);
    }

    @Test
    void clearingPendingRefillsCancelsEveryZoneAndAllowsAFreshRound() {
        refills.onHarvest(first);
        refills.onHarvest(second);
        List<TestTask> previousRound = List.copyOf(clock.tasks);
        clock.advance(399);
        refills.clear();
        assertTrue(previousRound.stream().allMatch(TestTask::isCancelled));
        refills.clear();

        refills.onHarvest(first);
        previousRound.forEach(task -> task.callback.run());
        clock.advance(399);
        assertTrue(restored.isEmpty());
        clock.advance(1);
        assertEquals(List.of(first), restored);
    }

    private static BuildMartMaterialZone zone(int x) {
        return new BuildMartMaterialZone(
                UUID.randomUUID(), new Vector(x, 0, 0), new Vector(x + 2, 2, 2));
    }

    private static final class TestClock {
        private long tick;
        private final List<TestTask> tasks = new ArrayList<>();
        private final BukkitScheduler scheduler =
                (BukkitScheduler)
                        Proxy.newProxyInstance(
                                BukkitScheduler.class.getClassLoader(),
                                new Class<?>[] {BukkitScheduler.class},
                                (p, method, args) -> {
                                    if (!method.getName().equals("runTaskLater"))
                                        throw new UnsupportedOperationException(method.getName());
                                    TestTask task =
                                            new TestTask(
                                                    tasks.size(),
                                                    tick + (long) args[2],
                                                    (Runnable) args[1]);
                                    tasks.add(task);
                                    return task;
                                });

        private void advance(long ticks) {
            long target = tick + ticks;
            for (TestTask task : tasks) {
                if (!task.cancelled && !task.completed && task.deadline <= target) {
                    tick = task.deadline;
                    task.completed = true;
                    task.callback.run();
                }
            }
            tick = target;
        }
    }

    private static final class TestTask implements BukkitTask {
        private final int id;
        private final long deadline;
        private final Runnable callback;
        private boolean cancelled;
        private boolean completed;

        private TestTask(int id, long deadline, Runnable callback) {
            this.id = id;
            this.deadline = deadline;
            this.callback = callback;
        }

        @Override
        public int getTaskId() {
            return id;
        }

        @Override
        public Plugin getOwner() {
            return null;
        }

        @Override
        public boolean isSync() {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
