package ink.ziip.championshipscore.api.game.spatial;

import org.jetbrains.annotations.NotNull;

/** A typed spatial configuration that can be placed elsewhere without copying game rules or state. */
public interface SpatialTemplate<T> {
    @NotNull T transform(@NotNull SpatialTransform transform);
}
