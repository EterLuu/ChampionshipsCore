package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** A single budget shared by every lane, including first-time map binding and retired frames. */
final class SliderWork {
    record Job(BooleanSupplier valid, BooleanSupplier next, int quantum, boolean removal) {}

    private final ArrayDeque<Job> jobs = new ArrayDeque<>();
    private final SliderSweep sweep = new SliderSweep();
    private final Plugin plugin;
    private BukkitTask task;

    SliderWork(Plugin plugin) {
        this.plugin = plugin;
    }

    void add(BooleanSupplier valid, BooleanSupplier next, int quantum, boolean removal) {
        add(new Job(valid, next, quantum, removal));
    }

    void add(Job job) {
        if (!plugin.isEnabled() || Bukkit.isStopping()) {
            if (job.removal) drain(job);
            return;
        }
        jobs.addLast(job);
        schedule();
    }

    /** Complete only this screen's outstanding retirement before its lane starts the next room. */
    void finishRemoval(Job job) {
        if (job != null && job.removal && jobs.remove(job)) drain(job);
    }

    void expose(SliderSweep.Card card) {
        if (!plugin.isEnabled() || Bukkit.isStopping()) return;
        sweep.add(card);
        schedule();
    }

    boolean removing() {
        return jobs.stream().anyMatch(Job::removal);
    }

    private void schedule() {
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void tick() {
        if (!sweep.isEmpty())
            sweep.slice(System::nanoTime, System.nanoTime() + SliderSweep.MICROS * 1000L);
        if (!jobs.isEmpty()) slice(jobs, System::nanoTime, 64, 5_000_000L);
        if (jobs.isEmpty() && sweep.isEmpty()) {
            task.cancel();
            task = null;
        }
    }

    static void slice(ArrayDeque<Job> jobs, LongSupplier nanos, int limit, long budget) {
        long start = nanos.getAsLong();
        // Visit each card once. Eight tiles are one visible column; rotate the starting card
        // when either budget runs out so a slow, cold tracker does not starve the other lane.
        int visits = jobs.size();
        while (visits-- > 0 && limit > 0 && nanos.getAsLong() - start < budget) {
            var job = jobs.removeFirst();
            if (!job.valid.getAsBoolean()) continue;
            boolean finished = false;
            int quantum = job.quantum;
            for (int i = 0; i < quantum && limit > 0; i++) {
                limit--;
                if (job.next.getAsBoolean()) {
                    finished = true;
                    break;
                }
                if (nanos.getAsLong() - start >= budget) break;
            }
            if (!finished) jobs.addLast(job);
        }
    }

    private static void drain(Job job) {
        if (job.valid.getAsBoolean())
            while (!job.next.getAsBoolean()) {
                /* exact retired entities */
            }
    }

    // During plugin shutdown there will be no further Bukkit ticks to finish retirement.
    void unload() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (var job : jobs) if (job.removal) drain(job);
        jobs.clear();
        sweep.clear();
    }
}
