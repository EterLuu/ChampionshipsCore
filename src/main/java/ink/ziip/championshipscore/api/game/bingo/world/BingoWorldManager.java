package ink.ziip.championshipscore.api.game.bingo.world;

import ink.ziip.championshipscore.ChampionshipsCore;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.WorldCreator;

/**
 * Creates and holds the persistent bingo survival worlds: a normally-generated overworld plus its
 * nether and end dimensions. Unlike the other CC games (which copy a void-generated template each
 * round), bingo is whole-world exploration, so these worlds use vanilla terrain generation and are
 * created once if missing, then reused across rounds — only player progress is reset between rounds,
 * not the terrain.
 *
 * <p>{@link WorldCreator#createWorld()} loads the world from disk when it already exists and generates
 * a fresh one otherwise, so {@link #ensureWorlds()} is safe to call on every startup.
 */
public final class BingoWorldManager {
    public static final String OVERWORLD = "bingo";
    public static final String NETHER = "bingo_nether";
    public static final String END = "bingo_the_end";

    private final ChampionshipsCore plugin;
    private volatile World overworld;

    public BingoWorldManager(ChampionshipsCore plugin) {
        this.plugin = plugin;
    }

    /** Creates the bingo overworld/nether/end if they don't exist yet; loads them otherwise. Main thread. */
    public void ensureWorlds() {
        overworld = ensureWorld(OVERWORLD, World.Environment.NORMAL);
        ensureWorld(NETHER, World.Environment.NETHER);
        ensureWorld(END, World.Environment.THE_END);
    }

    public World overworld() {
        return overworld;
    }

    private World ensureWorld(String name, World.Environment environment) {
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            disableSpectatorChunkGen(existing);
            return existing;
        }

        // Vanilla terrain generation: deliberately no custom generator (the shared WorldManager uses a
        // VoidChunkGenerator for arena games; bingo needs real terrain to explore).
        WorldCreator creator = new WorldCreator(name);
        creator.environment(environment);
        World world = creator.createWorld();
        if (world == null) {
            plugin.getLogger().warning("[Bingo] 无法创建/加载世界 " + name);
            return null;
        }
        world.setAutoSave(true);
        disableSpectatorChunkGen(world);
        plugin.getLogger().info("[Bingo] 世界已就绪：" + name + "（" + environment + "）");
        return world;
    }

    /**
     * Spectators flying ahead of survivors shouldn't pre-generate terrain (lag + world bloat). A freshly
     * generated world defaults to {@code spectatorsGenerateChunks=true}; bake the rule in here, on both
     * first creation and subsequent loads, so it holds regardless of when players enter.
     */
    private void disableSpectatorChunkGen(World world) {
        try {
            world.setGameRule(GameRules.SPECTATORS_GENERATE_CHUNKS, false);
        } catch (Throwable ignored) {
        }
    }
}
