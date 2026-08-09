package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.api.game.arena.ArenaGrid;
import ink.ziip.championshipscore.api.game.arena.RingArenaGrid;
import org.bukkit.util.Vector;

/**
 * Fixed fallback placement grid for a prepared Build Mart map. Prepare normally derives the grid centre from
 * the selected, hand-built resource-hub boundary. Copy 0 is the editable base template, while team bases
 * begin at copy 1.
 *
 * <p>The base placement is a {@link RingArenaGrid} centred on the hub, so the same function drives both the
 * offline paste and runtime anchor maths. The template at copy 0 is never assigned to a team.
 */
public final class BuildMartLayout {
    /** Fallback grid centre used only before an admin selects the resource-hub boundary. */
    public static final Vector HUB = new Vector(0, 100, 0);
    /** Fallback distance in blocks between adjacent grid cells. */
    public static final int SPACING = 500;

    /** Shared ring placement (centred on the hub) used for both pasting and anchor derivation. */
    public static final ArenaGrid GRID = new RingArenaGrid(HUB, SPACING);

    private BuildMartLayout() {
    }

    /** Minimum-corner paste/anchor origin for a physical base copy (0 is the template). */
    public static Vector gridOrigin(int seat) {
        return GRID.origin(seat);
    }

    /**
     * Translation from physical copy 0 to another physical copy. Anchors are configured once against the
     * 0th template; playable team seat {@code seat} maps to copy {@code seat + 1}.
     */
    public static Vector delta(int seat) {
        return GRID.delta(seat);
    }
}
