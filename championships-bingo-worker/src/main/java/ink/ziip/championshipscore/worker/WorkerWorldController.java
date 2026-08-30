package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.bingo.BingoWorldRules;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Owns the environmental lifecycle of the dedicated Bingo worlds.
 *
 * <p>The worlds are generated before a match is committed and players may spend the introduction and
 * scatter countdown inside loaded chunks. Keeping normal world simulation enabled during that window
 * makes the effective starting state depend on how long the worker has been online. The waiting phase
 * therefore freezes environmental progression without removing chunk-generation entities such as
 * villagers. Normal survival rules are restored atomically on the global region thread when the match
 * begins.</p>
 */
final class WorkerWorldController {
    private final Plugin plugin;
    private final WorkerConfig config;

    WorkerWorldController(Plugin plugin, WorkerConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    void configureAndFreeze(World world) {
        BingoWorldRules.configure(world);
        apply(world, Phase.WAITING);
    }

    /** Re-establishes a deterministic pre-game state in every configured dimension. */
    boolean freeze() {
        List<World> worlds = configuredWorlds();
        if (worlds.size() != 3) return false;
        for (World world : worlds) apply(world, Phase.WAITING);
        return true;
    }

    /** Enables normal survival simulation immediately before the authoritative RUNNING transition. */
    boolean startMatch() {
        List<World> worlds = configuredWorlds();
        if (worlds.size() != 3) return false;
        for (World world : worlds) apply(world, Phase.RUNNING);
        return true;
    }

    private List<World> configuredWorlds() {
        World overworld = plugin.getServer().getWorld(config.overworld());
        World nether = plugin.getServer().getWorld(config.nether());
        World end = plugin.getServer().getWorld(config.end());
        if (overworld == null || nether == null || end == null) {
            plugin.getLogger().severe("Unable to change Bingo world phase: one or more dimensions are unavailable");
            return List.of();
        }
        return List.of(overworld, nether, end);
    }

    private void apply(World world, Phase phase) {
        BingoWorldRules.applyPhase(world,
                phase == Phase.RUNNING ? BingoWorldRules.Phase.RUNNING : BingoWorldRules.Phase.WAITING,
                world.getName().equals(config.overworld()));
    }

    enum Phase {
        WAITING,
        RUNNING
    }
}
