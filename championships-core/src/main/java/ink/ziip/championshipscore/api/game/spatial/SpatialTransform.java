package ink.ziip.championshipscore.api.game.spatial;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable translation applied to map-local geometry. Keeping the transform separate from game state
 * lets setup code and runtime config resolution use the exact same placement calculation.
 */
public record SpatialTransform(double x, double y, double z) {
    public static final SpatialTransform IDENTITY = new SpatialTransform(0, 0, 0);

    public static @NotNull SpatialTransform translation(@NotNull Vector delta) {
        return new SpatialTransform(delta.getX(), delta.getY(), delta.getZ());
    }

    public @NotNull Vector apply(@NotNull Vector vector) {
        return vector.clone().add(new Vector(x, y, z));
    }

    public @Nullable Location apply(@Nullable Location location) {
        return location == null ? null : location.clone().add(x, y, z);
    }

    public @Nullable BoundingBox apply(@Nullable BoundingBox box) {
        return box == null ? null : box.clone().shift(x, y, z);
    }
}
