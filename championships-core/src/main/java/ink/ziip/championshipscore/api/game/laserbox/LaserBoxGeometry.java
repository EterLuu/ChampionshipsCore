package ink.ziip.championshipscore.api.game.laserbox;

import ink.ziip.championshipscore.api.game.spatial.SpatialTemplate;
import ink.ziip.championshipscore.api.game.spatial.SpatialTransform;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** All spatial values belonging to one generated LaserBox copy. */
public final class LaserBoxGeometry implements SpatialTemplate<LaserBoxGeometry> {
    private final Location rightSpawn;
    private final Location leftSpawn;
    private final Location spectatorSpawn;
    private final Vector boundaryMin;
    private final Vector boundaryMax;
    private final List<Vector> supplyPoints;

    private LaserBoxGeometry(Location rightSpawn, Location leftSpawn, Location spectatorSpawn,
                              Vector boundaryMin, Vector boundaryMax, List<Vector> supplyPoints) {
        this.rightSpawn = rightSpawn;
        this.leftSpawn = leftSpawn;
        this.spectatorSpawn = spectatorSpawn;
        this.boundaryMin = boundaryMin;
        this.boundaryMax = boundaryMax;
        this.supplyPoints = Collections.unmodifiableList(new ArrayList<>(supplyPoints));
    }

    public static @NotNull LaserBoxGeometry from(@NotNull LaserBoxConfig config) {
        List<Vector> supplies = config.getSupplyPoints().stream()
                .map(raw -> LaserBoxConfig.parseSupplyPoint(raw, config.getConfiguredWorld())).toList();
        // YAML map-local points may omit a world. Bind every point before applying replica transforms.
        return new LaserBoxGeometry(config.bind(config.getRightSpawnPoint()), config.bind(config.getLeftSpawnPoint()),
                config.bind(config.getSpectatorSpawnPoint()), Vector.getMinimum(config.getAreaPos1(), config.getAreaPos2()),
                Vector.getMaximum(config.getAreaPos1(), config.getAreaPos2()), supplies);
    }

    @Override
    public @NotNull LaserBoxGeometry transform(@NotNull SpatialTransform transform) {
        return new LaserBoxGeometry(transform.apply(rightSpawn), transform.apply(leftSpawn),
                transform.apply(spectatorSpawn), transform.apply(boundaryMin), transform.apply(boundaryMax),
                supplyPoints.stream().map(transform::apply).toList());
    }

    public Location rightSpawn() { return rightSpawn; }
    public Location leftSpawn() { return leftSpawn; }
    public Location spectatorSpawn() { return spectatorSpawn; }
    public Vector boundaryMin() { return boundaryMin; }
    public Vector boundaryMax() { return boundaryMax; }
    public List<Vector> supplyPoints() { return supplyPoints; }
}
