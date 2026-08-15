package ink.ziip.championshipscore.worker;

import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.entity.WanderingTrader;
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
    static final long START_TIME = 9000L;
    static final int NORMAL_RANDOM_TICK_SPEED = 3;

    private final Plugin plugin;
    private final WorkerConfig config;

    WorkerWorldController(Plugin plugin, WorkerConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    void configureAndFreeze(World world) {
        world.setDifficulty(Difficulty.NORMAL);
        world.setAutoSave(true);
        world.setGameRule(GameRules.SPECTATORS_GENERATE_CHUNKS, false);
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRules.PVP, true);
        world.setGameRule(GameRules.LOCATOR_BAR, false);
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
        boolean running = phase == Phase.RUNNING;
        world.setSpawnFlags(running, running);
        world.setGameRule(GameRules.ADVANCE_TIME, running);
        world.setGameRule(GameRules.ADVANCE_WEATHER, running);
        world.setGameRule(GameRules.SPAWN_MOBS, running);
        world.setGameRule(GameRules.SPAWN_MONSTERS, running);
        world.setGameRule(GameRules.SPAWN_PATROLS, running);
        world.setGameRule(GameRules.SPAWN_PHANTOMS, running);
        // Wandering traders have no role in Bingo and make the shared worlds accumulate
        // persistent entities between matches. Keep them disabled in both phases.
        world.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        for (WanderingTrader trader : world.getEntitiesByClass(WanderingTrader.class)) {
            trader.remove();
        }
        world.setGameRule(GameRules.SPAWN_WARDENS, running);
        world.setGameRule(GameRules.SPAWNER_BLOCKS_WORK, running);
        world.setGameRule(GameRules.RAIDS, running);
        world.setGameRule(GameRules.MOB_GRIEFING, running);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, running ? NORMAL_RANDOM_TICK_SPEED : 0);
        world.setGameRule(GameRules.MOB_DROPS, running);
        world.setGameRule(GameRules.ENTITY_DROPS, running);
        world.setGameRule(GameRules.BLOCK_DROPS, running);

        if (!running && world.getName().equals(config.overworld())) {
            world.setTime(START_TIME);
            world.setStorm(false);
            world.setThundering(false);
        }
    }

    enum Phase {
        WAITING,
        RUNNING
    }
}
