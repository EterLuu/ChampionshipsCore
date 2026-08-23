package ink.ziip.championshipscore.api.game.bingo.world;

import ink.ziip.championshipscore.platform.bukkit.world.SafeScatterService;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** Compatibility facade; the reusable Paper/Folia implementation lives in the platform module. */
public final class SpawnScatterManager {
    private final SafeScatterService delegate;

    public SpawnScatterManager(Plugin plugin) {
        this.delegate = new SafeScatterService(plugin);
    }

    public void performScatterAsync(
            World world, List<Player> players, int radius, int maxTries, Runnable onComplete) {
        delegate.performScatterAsync(world, players, radius, maxTries, onComplete);
    }

    public void performScatterAsync(World world, List<Player> players, int radius, int jitter,
                                    int maxTries, Runnable onComplete) {
        delegate.performScatterAsync(world, players, radius, jitter, maxTries, onComplete);
    }
}
