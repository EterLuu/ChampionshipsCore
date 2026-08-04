package ink.ziip.championshipscore.api.game.parkourtag;

import ink.ziip.championshipscore.api.game.spatial.SpatialTemplate;
import ink.ziip.championshipscore.api.game.spatial.SpatialTransform;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The complete, transformable spatial configuration of one Parkour Tag game instance. */
@Getter
public final class ParkourTagGeometry implements SpatialTemplate<ParkourTagGeometry> {
    private final Location rightPrepareSpot;
    private final Location leftPrepareSpot;
    private final Location spectatorSpawn;
    private final Vector min;
    private final Vector max;
    private final ChaseZone leftZone;
    private final ChaseZone rightZone;

    private ParkourTagGeometry(Location rightPrepareSpot, Location leftPrepareSpot, Location spectatorSpawn,
                               Vector min, Vector max, ChaseZone leftZone, ChaseZone rightZone) {
        this.rightPrepareSpot = rightPrepareSpot;
        this.leftPrepareSpot = leftPrepareSpot;
        this.spectatorSpawn = spectatorSpawn;
        this.min = min;
        this.max = max;
        this.leftZone = leftZone;
        this.rightZone = rightZone;
    }

    public static @NotNull ParkourTagGeometry from(@NotNull ParkourTagConfig config) {
        return new ParkourTagGeometry(
                config.getRightPrepareSpot(), config.getLeftPrepareSpot(),
                config.getSpectatorSpawnPoint(),
                Vector.getMinimum(config.getAreaPos1(), config.getAreaPos2()),
                Vector.getMaximum(config.getAreaPos1(), config.getAreaPos2()),
                new ChaseZone(config.getLeftAreaChaserSpawnPoint(),
                        locations(config.getLeftAreaEscapeeSpawnPoints()),
                        Vector.getMinimum(config.getLeftAreaAreaPos1(), config.getLeftAreaAreaPos2()),
                        Vector.getMaximum(config.getLeftAreaAreaPos1(), config.getLeftAreaAreaPos2())),
                new ChaseZone(config.getRightAreaChaserSpawnPoint(),
                        locations(config.getRightAreaEscapeeSpawnPoints()),
                        Vector.getMinimum(config.getRightAreaAreaPos1(), config.getRightAreaAreaPos2()),
                        Vector.getMaximum(config.getRightAreaAreaPos1(), config.getRightAreaAreaPos2())));
    }

    private static List<Location> locations(List<String> raw) {
        List<Location> locations = new ArrayList<>();
        if (raw != null) {
            for (String value : raw) {
                Location location = Utils.getLocation(value);
                if (location != null) locations.add(location);
            }
        }
        return locations;
    }

    @Override
    public @NotNull ParkourTagGeometry transform(@NotNull SpatialTransform transform) {
        return new ParkourTagGeometry(transform.apply(rightPrepareSpot), transform.apply(leftPrepareSpot),
                transform.apply(spectatorSpawn), transform.apply(min), transform.apply(max),
                leftZone.transform(transform), rightZone.transform(transform));
    }

    public boolean contains(@NotNull Location location) {
        return location.toVector().isInAABB(min, max);
    }

    @Getter
    public static final class ChaseZone implements SpatialTemplate<ChaseZone> {
        private final Location chaserSpawn;
        private final List<Location> escapeeSpawns;
        private final Vector min;
        private final Vector max;

        private ChaseZone(Location chaserSpawn, List<Location> escapeeSpawns, Vector min, Vector max) {
            this.chaserSpawn = chaserSpawn;
            this.escapeeSpawns = Collections.unmodifiableList(new ArrayList<>(escapeeSpawns));
            this.min = min;
            this.max = max;
        }

        @Override
        public @NotNull ChaseZone transform(@NotNull SpatialTransform transform) {
            return new ChaseZone(transform.apply(chaserSpawn),
                    escapeeSpawns.stream().map(transform::apply).toList(),
                    transform.apply(min), transform.apply(max));
        }

        public boolean contains(@NotNull Location location) {
            return location.toVector().isInAABB(min, max);
        }

        public @NotNull BoundingBox box() {
            return BoundingBox.of(min, max.clone().add(new Vector(1, 1, 1)));
        }
    }
}
