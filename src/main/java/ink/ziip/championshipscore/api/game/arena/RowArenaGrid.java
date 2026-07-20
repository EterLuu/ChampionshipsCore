package ink.ziip.championshipscore.api.game.arena;

import org.bukkit.util.Vector;

/**
 * Lays copies in a straight line: copy {@code index} sits at {@code first + index * step}. Suited to games
 * with no central hub that just need N identical arenas side by side (e.g. TNT Run's load-balancing copies).
 * {@code step} should exceed the arena footprint along its axis so copies don't overlap.
 */
public final class RowArenaGrid implements ArenaGrid {
    private final Vector first;
    private final Vector step;

    public RowArenaGrid(Vector first, Vector step) {
        this.first = first.clone();
        this.step = step.clone();
    }

    @Override
    public Vector origin(int index) {
        return new Vector(
                first.getBlockX() + step.getBlockX() * index,
                first.getBlockY() + step.getBlockY() * index,
                first.getBlockZ() + step.getBlockZ() * index);
    }
}
