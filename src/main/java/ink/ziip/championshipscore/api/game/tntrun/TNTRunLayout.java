package ink.ziip.championshipscore.api.game.tntrun;

import ink.ziip.championshipscore.api.game.arena.ArenaGrid;
import ink.ziip.championshipscore.api.game.arena.RowArenaGrid;
import org.bukkit.util.Vector;

/**
 * Placement grid for TNT Run's load-balancing copies. The same arena is stamped out in a straight row by
 * the {@code prepare} command so players can be split across several identical maps (otherwise everyone
 * crowds one floor and the round ends instantly). The same row drives both the offline paste and the
 * runtime spawn derivation ({@code TNTRunConfig#getPlayerSpawnPoints}): copy {@code k}'s spawn is copy 0's
 * configured spawn shifted by {@link #delta(int)}.
 *
 * <p>Copies run along +X spaced {@link #STEP}; that gap must exceed the arena footprint so they don't
 * overlap (TNT Run maps are large, hence the wide spacing).
 */
public final class TNTRunLayout {
    /** Minimum corner where copy 0 is pasted. */
    public static final Vector FIRST = new Vector(0, 100, 0);
    /** Per-copy displacement along +X. */
    public static final Vector STEP = new Vector(1000, 0, 0);

    /** Shared row placement used for both pasting and spawn derivation. */
    public static final ArenaGrid GRID = new RowArenaGrid(FIRST, STEP);

    private TNTRunLayout() {
    }

    /** Minimum-corner paste origin for copy {@code index} (0-based). */
    public static Vector origin(int index) {
        return GRID.origin(index);
    }

    /** Translation from copy 0 to copy {@code index}; copy-0 spawn shifted by this gives copy {@code index}. */
    public static Vector delta(int index) {
        return GRID.delta(index);
    }
}
