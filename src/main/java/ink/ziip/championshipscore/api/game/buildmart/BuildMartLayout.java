package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.api.game.arena.ArenaGrid;
import ink.ziip.championshipscore.api.game.arena.RingArenaGrid;
import org.bukkit.util.Vector;

/**
 * Fixed, deterministic placement grid for a prepared Build Mart map. The whole arena is stamped out once
 * by the {@code prepare} command: the hub schematic is pasted with its minimum corner at {@link #HUB}, and
 * each team base is pasted with its minimum corner at {@link #gridOrigin(int)}.
 *
 * <p>The base placement is a {@link RingArenaGrid} centred on the hub, so the same function drives both the
 * offline paste (in {@code prepare}) and the runtime anchor maths ({@code BuildMartConfig#getSeatBase}):
 * a team's anchors always line up with the base physically pasted for its seat. Seat 0 sits at
 * {@code (0,100,500)}, seat 1 at {@code (500,100,500)}, seat 2 at {@code (500,100,0)} and so on, with no
 * cap on team count.
 */
public final class BuildMartLayout {
    /** Minimum corner where the hub schematic is pasted. */
    public static final Vector HUB = new Vector(0, 100, 0);
    /** Distance in blocks between adjacent grid cells (hub and every base). */
    public static final int SPACING = 500;

    /** Shared ring placement (centred on the hub) used for both pasting and anchor derivation. */
    public static final ArenaGrid GRID = new RingArenaGrid(HUB, SPACING);

    private BuildMartLayout() {
    }

    /** Minimum-corner paste/anchor origin for the base in {@code seat} (0-based). */
    public static Vector gridOrigin(int seat) {
        return GRID.origin(seat);
    }

    /**
     * Translation from seat 0's base to {@code seat}'s base. Anchors are configured once against seat 0
     * (the physical base at {@code gridOrigin(0)}); every other seat's anchors are this delta applied.
     */
    public static Vector delta(int seat) {
        return GRID.delta(seat);
    }
}
