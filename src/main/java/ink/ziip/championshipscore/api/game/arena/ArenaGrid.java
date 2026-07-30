package ink.ziip.championshipscore.api.game.arena;

import ink.ziip.championshipscore.api.game.spatial.SpatialTransform;
import org.bukkit.util.Vector;

/**
 * Deterministic placement of repeated, identical arena copies inside a single world. Several games stamp
 * the same sub-arena out multiple times so teams/players can play in parallel (Build Mart bases, TNT Run
 * load-balancing copies, Battle Box / Parkour Tag matches). This interface is the single source of truth
 * for where copy {@code index} sits, so the offline schematic paste and the runtime per-copy geometry
 * always agree.
 *
 * <p>Copies are same-orientation (pure translation, no rotation): the schematic is pasted with its minimum
 * corner at {@link #origin(int)}, and a copy's anchors are the configured copy-0 anchors shifted by
 * {@link #delta(int)}.
 */
public interface ArenaGrid {
    /** Minimum-corner paste/anchor origin for the copy at {@code index} (0-based). */
    Vector origin(int index);

    /** Translation from copy 0 to {@code index}; copy-0 anchors shifted by this give copy {@code index}. */
    default Vector delta(int index) {
        return origin(index).clone().subtract(origin(0));
    }

    /** Typed placement transform from copy 0 to {@code index}. */
    default SpatialTransform transform(int index) {
        return SpatialTransform.translation(delta(index));
    }
}
