package ink.ziip.championshipscore.api.game.battlebox;

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

/** The complete, transformable spatial configuration of one Battle Box game instance. */
@Getter
public final class BattleBoxGeometry implements SpatialTemplate<BattleBoxGeometry> {
    private final Location rightSpawn;
    private final Location leftSpawn;
    private final Location rightPreSpawn;
    private final Location leftPreSpawn;
    private final Location spectatorSpawn;
    private final Vector woolMin;
    private final Vector woolMax;
    private final Vector boundaryMin;
    private final Vector boundaryMax;
    private final List<Location> potionSpawns;

    private BattleBoxGeometry(Location rightSpawn, Location leftSpawn, Location rightPreSpawn,
                              Location leftPreSpawn, Location spectatorSpawn, Vector woolMin,
                              Vector woolMax, Vector boundaryMin, Vector boundaryMax,
                              List<Location> potionSpawns) {
        this.rightSpawn = rightSpawn;
        this.leftSpawn = leftSpawn;
        this.rightPreSpawn = rightPreSpawn;
        this.leftPreSpawn = leftPreSpawn;
        this.spectatorSpawn = spectatorSpawn;
        this.woolMin = woolMin;
        this.woolMax = woolMax;
        this.boundaryMin = boundaryMin;
        this.boundaryMax = boundaryMax;
        this.potionSpawns = Collections.unmodifiableList(new ArrayList<>(potionSpawns));
    }

    public static @NotNull BattleBoxGeometry from(@NotNull BattleBoxConfig config) {
        List<Location> potions = new ArrayList<>();
        if (config.getPotionSpawnPoints() != null) {
            for (String raw : config.getPotionSpawnPoints()) {
                Location location = Utils.getLocation(raw);
                if (location != null) potions.add(location);
            }
        }
        return new BattleBoxGeometry(
                config.getRightSpawnPoint(), config.getLeftSpawnPoint(),
                config.getRightPreSpawnPoint(), config.getLeftPreSpawnPoint(),
                config.getSpectatorSpawnPoint(),
                Vector.getMinimum(config.getWoolPos1(), config.getWoolPos2()),
                Vector.getMaximum(config.getWoolPos1(), config.getWoolPos2()),
                Vector.getMinimum(config.getAreaPos1(), config.getAreaPos2()),
                Vector.getMaximum(config.getAreaPos1(), config.getAreaPos2()), potions);
    }

    @Override
    public @NotNull BattleBoxGeometry transform(@NotNull SpatialTransform transform) {
        List<Location> potions = potionSpawns.stream().map(transform::apply).toList();
        return new BattleBoxGeometry(
                transform.apply(rightSpawn), transform.apply(leftSpawn),
                transform.apply(rightPreSpawn), transform.apply(leftPreSpawn),
                transform.apply(spectatorSpawn), transform.apply(woolMin), transform.apply(woolMax),
                transform.apply(boundaryMin), transform.apply(boundaryMax), potions);
    }

    public boolean contains(@NotNull Vector point) {
        return point.isInAABB(boundaryMin, boundaryMax);
    }

    public boolean isInWool(@NotNull Vector point) {
        return point.isInAABB(woolMin, woolMax);
    }

    public @NotNull BoundingBox boundaryBox() {
        return BoundingBox.of(boundaryMin, boundaryMax.clone().add(new Vector(1, 1, 1)));
    }
}
