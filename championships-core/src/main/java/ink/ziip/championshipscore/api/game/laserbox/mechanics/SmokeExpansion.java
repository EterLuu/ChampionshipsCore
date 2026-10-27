package ink.ziip.championshipscore.api.game.laserbox.mechanics;

import org.bukkit.block.Block;

import java.util.*;
import java.util.function.Predicate;

/** Expands a cloud once per cell, retrying only cells temporarily blocked by world changes. */
public final class SmokeExpansion {
    private final List<Block> cells;
    private final List<Block> pending = new ArrayList<>();
    private int next;

    public SmokeExpansion(List<Block> cells) {
        this.cells = List.copyOf(cells);
    }

    public List<Block> cells() {
        return cells;
    }

    public void expand(int count, Predicate<Block> place) {
        int limit = Math.min(cells.size(), Math.max(0, count));
        while (next < limit) pending.add(cells.get(next++));
        pending.removeIf(place);
    }
}
