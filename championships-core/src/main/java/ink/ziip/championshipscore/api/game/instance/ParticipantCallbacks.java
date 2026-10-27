package ink.ziip.championshipscore.api.game.instance;

import ink.ziip.championshipscore.ChampionshipsCore;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;

/** Owns delayed player work for one Paper arena and validates its original run and connection. */
final class ParticipantCallbacks {
    private final BaseGameInstance instance;
    private final ChampionshipsCore plugin;
    private final BukkitScheduler scheduler;
    private final Set<BukkitTask> pending = new HashSet<>();

    ParticipantCallbacks(
            BaseGameInstance instance, ChampionshipsCore plugin, BukkitScheduler scheduler) {
        this.instance = instance;
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    BukkitTask schedule(Player player, Runnable action, long delayTicks, boolean gameplay) {
        long generation = instance.captureGameGeneration();
        BukkitTask[] handle = new BukkitTask[1];
        handle[0] =
                scheduler.runTaskLater(
                        plugin,
                        () -> {
                            if (!pending.remove(handle[0])) return;
                            if (instance.isCurrentParticipantConnection(generation, player)
                                    && (!gameplay || instance.isGameplayParticipant(player)))
                                action.run();
                        },
                        Math.max(1L, delayTicks));
        pending.add(handle[0]);
        return handle[0];
    }

    void cancel() {
        pending.forEach(BukkitTask::cancel);
        pending.clear();
    }
}
