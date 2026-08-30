package ink.ziip.championshipscore.platform.bukkit.bingo;

import ink.ziip.championshipscore.protocol.BingoRemix;
import ink.ziip.championshipscore.protocol.BingoVariantRules;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.entity.WanderingTrader;

import java.util.Collection;
import java.util.Objects;

/** Scheduler-neutral world policy shared by local and remote Bingo runtimes. */
public final class BingoWorldRules {
    public static final long START_TIME = 9000L;
    public static final int NORMAL_RANDOM_TICK_SPEED = 3;

    private BingoWorldRules() {
    }

    public static void configure(World world) {
        Objects.requireNonNull(world, "world");
        world.setDifficulty(Difficulty.NORMAL);
        world.setAutoSave(true);
        world.setGameRule(GameRules.SPECTATORS_GENERATE_CHUNKS, false);
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRules.PVP, true);
        world.setGameRule(GameRules.LOCATOR_BAR, false);
        disableWanderingTraders(world);
    }

    public static void applyPhase(World world, Phase phase, boolean resetOverworld) {
        Objects.requireNonNull(world, "world");
        boolean running = Objects.requireNonNull(phase, "phase") == Phase.RUNNING;
        world.setGameRule(GameRules.ADVANCE_TIME, running);
        world.setGameRule(GameRules.ADVANCE_WEATHER, running);
        world.setGameRule(GameRules.SPAWN_MOBS, running);
        world.setGameRule(GameRules.SPAWN_MONSTERS, running);
        world.setGameRule(GameRules.SPAWN_PATROLS, running);
        world.setGameRule(GameRules.SPAWN_PHANTOMS, running);
        world.setGameRule(GameRules.SPAWN_WARDENS, running);
        world.setGameRule(GameRules.SPAWNER_BLOCKS_WORK, running);
        world.setGameRule(GameRules.RAIDS, running);
        world.setGameRule(GameRules.MOB_GRIEFING, running);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, running ? NORMAL_RANDOM_TICK_SPEED : 0);
        world.setGameRule(GameRules.MOB_DROPS, running);
        world.setGameRule(GameRules.ENTITY_DROPS, running);
        world.setGameRule(GameRules.BLOCK_DROPS, running);
        disableWanderingTraders(world);
        if (!running && resetOverworld) {
            world.setTime(START_TIME);
            world.setStorm(false);
            world.setThundering(false);
        }
    }

    public static void applyVariant(World world, BingoVariantRules variant) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(variant, "variant");
        BingoRemix remix = variant.remix();
        boolean night = remix == BingoRemix.ETERNAL_NIGHT;
        boolean day = remix == BingoRemix.POLAR_DAY;
        world.setGameRule(GameRules.KEEP_INVENTORY, !variant.difficulty().clearsInventoryOnDeath());
        if (night || day) {
            world.setDifficulty(night ? Difficulty.HARD : Difficulty.EASY);
            world.setGameRule(GameRules.ADVANCE_TIME, false);
            world.setTime(night ? 18000L : 0L);
        } else {
            world.setDifficulty(Difficulty.NORMAL);
            world.setGameRule(GameRules.ADVANCE_TIME, true);
            if (world.getEnvironment() == World.Environment.NORMAL) world.setTime(START_TIME);
        }
    }

    public static void setPvp(Collection<World> worlds, boolean enabled) {
        for (World world : worlds) {
            if (world != null) world.setGameRule(GameRules.PVP, enabled);
        }
    }

    private static void disableWanderingTraders(World world) {
        world.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        for (WanderingTrader trader : world.getEntitiesByClass(WanderingTrader.class)) trader.remove();
    }

    public enum Phase {
        WAITING,
        RUNNING
    }
}
