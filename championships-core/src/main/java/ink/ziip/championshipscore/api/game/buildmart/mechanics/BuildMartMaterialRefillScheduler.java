package ink.ziip.championshipscore.api.game.buildmart.mechanics;

import ink.ziip.championshipscore.api.game.buildmart.model.BuildMartMaterialZone;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Restores each harvested material zone once, after twenty seconds without another harvest. */
public final class BuildMartMaterialRefillScheduler {
    private static final long IDLE_TICKS = 20 * 20L;

    private final BukkitScheduler scheduler;
    private final Plugin plugin;
    private final Consumer<BuildMartMaterialZone> onRefill;
    private final Map<UUID, PendingRefill> pendingRefills = new HashMap<>();

    public BuildMartMaterialRefillScheduler(
            BukkitScheduler scheduler, Plugin plugin, Consumer<BuildMartMaterialZone> onRefill) {
        this.scheduler = scheduler;
        this.plugin = plugin;
        this.onRefill = onRefill;
    }

    public void onHarvest(BuildMartMaterialZone zone) {
        PendingRefill next = new PendingRefill();
        PendingRefill previous = pendingRefills.put(zone.snapshotId(), next);
        if (previous != null) previous.cancel();
        next.task =
                scheduler.runTaskLater(
                        plugin,
                        () -> {
                            // A cancelled callback cannot consume a newer harvest or survive a
                            // round reset.
                            if (pendingRefills.remove(zone.snapshotId(), next))
                                onRefill.accept(zone);
                        },
                        IDLE_TICKS);
    }

    public void clear() {
        pendingRefills.values().forEach(PendingRefill::cancel);
        pendingRefills.clear();
    }

    private static final class PendingRefill {
        private BukkitTask task;

        private void cancel() {
            if (task != null) task.cancel();
        }
    }
}
