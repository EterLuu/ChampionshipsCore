package ink.ziip.championshipscore.api.game.battlebox;

import ink.ziip.championshipscore.api.game.arena.ArenaGrid;
import ink.ziip.championshipscore.api.game.arena.RowArenaGrid;
import org.bukkit.util.Vector;

/**
 * Placement grid for Battle Box's parallel match copies. One arena schematic (a full two-team box) is
 * stamped out in a straight row by {@code prepare}; each copy hosts one independent team-vs-team match. The
 * same row drives both the offline paste and the runtime per-match geometry ({@code BattleBoxMatch}):
 * copy {@code k}'s spawns/wool/area are copy 0's configured template shifted by {@link #delta(int)}.
 *
 * <p>Copies run along +X spaced {@link #STEP}; the gap must exceed an arena's footprint so they don't
 * overlap.
 */
public final class BattleBoxLayout {
    /** Minimum corner where copy 0 is pasted. */
    public static final Vector FIRST = new Vector(0, 100, 0);
    /** Per-copy displacement along +X. */
    public static final Vector STEP = new Vector(256, 0, 0);

    /** Shared row placement used for both pasting and geometry derivation. */
    public static final ArenaGrid GRID = new RowArenaGrid(FIRST, STEP);

    private BattleBoxLayout() {
    }

    /** Minimum-corner paste origin for copy {@code index} (0-based). */
    public static Vector origin(int index) {
        return GRID.origin(index);
    }

    /** Translation from copy 0 to copy {@code index}; copy-0 geometry shifted by this gives copy {@code index}. */
    public static Vector delta(int index) {
        return GRID.delta(index);
    }
}
