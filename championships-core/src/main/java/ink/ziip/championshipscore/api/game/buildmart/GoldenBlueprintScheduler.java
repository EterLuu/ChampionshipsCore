package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.ChampionshipsCore;
import org.bukkit.scheduler.BukkitTask;

/**
 * Rotates the global golden blueprint on a fixed window. Each tick fires {@code onRotate}, which the area
 * uses to expire the previous golden order (clearing unfinished golden zones as a penalty) and surface a
 * fresh one in the hub display. The first golden is spawned by the caller at round start; this only
 * handles the subsequent rotations.
 */
public class GoldenBlueprintScheduler {
    private final ChampionshipsCore plugin;
    private final int intervalSeconds;
    private final Runnable onRotate;
    private BukkitTask task;

    public GoldenBlueprintScheduler(ChampionshipsCore plugin, int intervalSeconds, Runnable onRotate) {
        this.plugin = plugin;
        this.intervalSeconds = Math.max(1, intervalSeconds);
        this.onRotate = onRotate;
    }

    public void start() {
        stop();
        long period = intervalSeconds * 20L;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, onRotate, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
