package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.ChampionshipsCore;
import org.bukkit.scheduler.BukkitTask;

/**
 * Drives the periodic re-roll of the hub blueprint library. Runs {@code onRefresh} every
 * {@code intervalSeconds}, starting after the first interval (the initial list is seeded by the caller at
 * round start). A thin wrapper over a single repeating task so the area can start/stop it cleanly.
 */
public class BlueprintRefreshScheduler {
    private final ChampionshipsCore plugin;
    private final int intervalSeconds;
    private final Runnable onRefresh;
    private BukkitTask task;

    public BlueprintRefreshScheduler(ChampionshipsCore plugin, int intervalSeconds, Runnable onRefresh) {
        this.plugin = plugin;
        this.intervalSeconds = Math.max(1, intervalSeconds);
        this.onRefresh = onRefresh;
    }

    public void start() {
        stop();
        long period = intervalSeconds * 20L;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, onRefresh, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
