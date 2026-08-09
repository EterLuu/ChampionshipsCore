package ink.ziip.championshipscore.api.game.skywars;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Map-owned SkyWars positions and containment boundary, deliberately excluding rules and scoring. */
@Getter
public final class SkyWarsMapGeometry {
    private final Vector boundaryMin;
    private final Vector boundaryMax;
    private final Location boundaryCenter;
    private final Location spectatorSpawn;
    private final Location introductionSpawn;
    private final List<String> teamSpawns;

    private SkyWarsMapGeometry(Vector boundaryMin, Vector boundaryMax, Location boundaryCenter,
                               Location spectatorSpawn,
                               Location introductionSpawn, List<String> teamSpawns) {
        this.boundaryMin = boundaryMin;
        this.boundaryMax = boundaryMax;
        this.boundaryCenter = boundaryCenter;
        this.spectatorSpawn = spectatorSpawn;
        this.introductionSpawn = introductionSpawn;
        this.teamSpawns = teamSpawns == null ? List.of() : List.copyOf(teamSpawns);
    }

    public static @NotNull SkyWarsMapGeometry from(@NotNull SkyWarsConfig config) {
        return new SkyWarsMapGeometry(
                Vector.getMinimum(config.getAreaPos1(), config.getAreaPos2()),
                Vector.getMaximum(config.getAreaPos1(), config.getAreaPos2()),
                config.getBoundaryCenterPoint(), config.getSpectatorSpawnPoint(), config.getIntroductionSpawnPoint(),
                config.getTeamSpawnPoints());
    }
}
