package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;

/** Shared per-tick budget for all joint rigs. */
final class JointWork {
    static final int BUDGET = 1024;

    record Job(BooleanSupplier valid, BooleanSupplier next, int cost) {
        Job {
            if (cost < 1 || cost > BUDGET)
                throw new IllegalArgumentException("Invalid joint work cost: " + cost);
        }
    }

    private final ArrayDeque<Job> jobs = new ArrayDeque<>();
    private final Plugin plugin;
    private BukkitTask task;

    JointWork(Plugin plugin) {
        this.plugin = plugin;
    }

    void add(BooleanSupplier valid, BooleanSupplier next, int cost) {
        jobs.addLast(new Job(valid, next, cost));
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void tick() {
        slice(jobs);
        if (jobs.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    static void slice(ArrayDeque<Job> jobs) {
        int budget = BUDGET;
        while (!jobs.isEmpty()) {
            Job job = jobs.peekFirst();
            if (!job.valid.getAsBoolean()) {
                jobs.removeFirst();
                continue;
            }
            if (budget < job.cost) break;
            budget -= job.cost;
            if (job.next.getAsBoolean() && jobs.peekFirst() == job) jobs.removeFirst();
        }
    }

    void unload() {
        if (task != null) task.cancel();
        task = null;
        jobs.clear();
    }
}
