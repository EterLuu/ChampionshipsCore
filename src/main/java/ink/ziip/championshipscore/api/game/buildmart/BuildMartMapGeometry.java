package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.api.game.arena.ArenaGrid;
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
    @Nullable private final BoundingBox hub;
    @Nullable private final Location goldenDisplay;
    @Nullable private final BuildMartBase baseTemplate;
    @NotNull private final ArenaGrid baseGrid;
    private final int baseCount;

    private BuildMartMapGeometry(@Nullable BoundingBox boundary, @Nullable Location spectatorSpawn,
                                 @Nullable BoundingBox hub, @Nullable Location goldenDisplay,
                                 @Nullable BuildMartBase baseTemplate, @NotNull ArenaGrid baseGrid,
                                 int baseCount) {
        this.boundary = boundary;
        this.spectatorSpawn = spectatorSpawn;
        this.hub = hub;
        this.goldenDisplay = goldenDisplay;
        this.baseTemplate = baseTemplate;
        this.baseGrid = baseGrid;
        this.baseCount = baseCount;
    }

    public static @NotNull BuildMartMapGeometry from(@NotNull BuildMartConfig config) {
        BuildMartBase template = config.getBaseTemplate();
        return new BuildMartMapGeometry(box(config.getAreaPos1(), config.getAreaPos2()),
                config.getSpectatorSpawnPoint(), box(config.getHubPos1(), config.getHubPos2()),
                config.getGoldenDisplayPoint(), template, config.getBaseGrid(), config.getBaseCount());
    }

    public @Nullable BuildMartBase baseForSeat(int seat) {
        if (baseTemplate == null || seat < 0 || seat >= baseCount) return null;
        return baseTemplate.transform(baseGrid.transform(BuildMartConfig.playableCopyIndex(seat))).forSeat(seat);
    }

    public boolean isInHub(@NotNull Location location) {
        return hub != null && hub.contains(location.toVector());
    }

    private static @Nullable BoundingBox box(@Nullable Vector a, @Nullable Vector b) {
        if (a == null || b == null) return null;
        return BoundingBox.of(Vector.getMinimum(a, b), Vector.getMaximum(a, b));
    }
}
