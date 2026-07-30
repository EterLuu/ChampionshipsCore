package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.api.game.spatial.ReplicatedSpatialLayout;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Build Mart map geometry: one shared hub plus internal, non-instance team-base replicas. */
@Getter
public final class BuildMartMapGeometry {
    @Nullable private final BoundingBox boundary;
    @Nullable private final Location spectatorSpawn;
    @Nullable private final Location hubSpawn;
    @Nullable private final BoundingBox hub;
    @Nullable private final BoundingBox hubReturn;
    @Nullable private final Location goldenDisplay;
    @Nullable private final ReplicatedSpatialLayout<BuildMartBase> bases;

    private BuildMartMapGeometry(@Nullable BoundingBox boundary, @Nullable Location spectatorSpawn,
                                 @Nullable Location hubSpawn, @Nullable BoundingBox hub,
                                 @Nullable BoundingBox hubReturn, @Nullable Location goldenDisplay,
                                 @Nullable ReplicatedSpatialLayout<BuildMartBase> bases) {
        this.boundary = boundary;
        this.spectatorSpawn = spectatorSpawn;
        this.hubSpawn = hubSpawn;
        this.hub = hub;
        this.hubReturn = hubReturn;
        this.goldenDisplay = goldenDisplay;
        this.bases = bases;
    }

    public static @NotNull BuildMartMapGeometry from(@NotNull BuildMartConfig config) {
        BuildMartBase template = config.getBaseTemplate();
        ReplicatedSpatialLayout<BuildMartBase> bases = template == null ? null
                : new ReplicatedSpatialLayout<>(template, BuildMartLayout.GRID, config.getBaseCount());
        return new BuildMartMapGeometry(box(config.getAreaPos1(), config.getAreaPos2()),
                config.getSpectatorSpawnPoint(), config.getHubSpawnPoint(),
                box(config.getHubPos1(), config.getHubPos2()),
                box(config.getHubReturnPos1(), config.getHubReturnPos2()),
                config.getGoldenDisplayPoint(), bases);
    }

    public @Nullable BuildMartBase baseForSeat(int seat) {
        if (bases == null || seat < 0 || seat >= bases.copyCount()) return null;
        return bases.geometry(seat).forSeat(seat);
    }

    public boolean isInHub(@NotNull Location location) {
        return hub != null && hub.contains(location.toVector());
    }

    public boolean isInHubReturn(@NotNull Location location) {
        return hubReturn != null && hubReturn.contains(location.toVector());
    }

    private static @Nullable BoundingBox box(@Nullable Vector a, @Nullable Vector b) {
        if (a == null || b == null) return null;
        return BoundingBox.of(Vector.getMinimum(a, b), Vector.getMaximum(a, b));
    }
}
