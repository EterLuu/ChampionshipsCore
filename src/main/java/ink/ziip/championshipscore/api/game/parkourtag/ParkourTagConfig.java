package ink.ziip.championshipscore.api.game.parkourtag;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.arena.ArenaGrid;
import ink.ziip.championshipscore.api.game.arena.ArenaLayoutPlanner;
import ink.ziip.championshipscore.api.game.arena.RowArenaGrid;
import ink.ziip.championshipscore.configuration.ConfigOption;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Getter
@Setter
public class ParkourTagConfig extends BaseGameConfig {
    private final String resourceName = "parkourtag/area.yml";
    private final String folderName = "parkourtag/";

    public ParkourTagConfig(ChampionshipsCore championshipsCore, String areaName) {
        super(championshipsCore, areaName);
    }

    @Override
    public int getLatestVersion() {
        return 6;
    }

    @ConfigOption(path = "name")
    private String areaName;

    /** Physical map world. Empty legacy configs continue to use the historical shared world. */
    @ConfigOption(path = "world-name", nullable = true)
    private String worldName;

    public String resolveWorldName() {
        return worldName == null || worldName.isBlank() ? "parkourtag" : worldName;
    }

    @ConfigOption(path = "timer")
    private int timer;

    /** Number of independently runnable instances stamped from copy 0 in this map. */
    @ConfigOption(path = "copy-count")
    private int copyCount = 8;

    @ConfigOption(path = "copy-layout.origin", nullable = true)
    private Vector copyLayoutOrigin;

    @ConfigOption(path = "copy-layout.step", nullable = true)
    private Vector copyLayoutStep;

    @ConfigOption(path = "copy-layout.size", nullable = true)
    private Vector copySize;

    public @NotNull ArenaGrid getCopyGrid() {
        Vector origin = copyLayoutOrigin == null ? ParkourTagLayout.FIRST : copyLayoutOrigin;
        Vector step = copyLayoutStep == null ? ParkourTagLayout.STEP : copyLayoutStep;
        return new RowArenaGrid(origin, step);
    }

    public @NotNull ArenaGrid prepareCopyGrid(@NotNull Vector size) {
        copyLayoutOrigin = ParkourTagLayout.FIRST.clone();
        copyLayoutStep = ArenaLayoutPlanner.rowStep(size);
        copySize = size.clone();
        return getCopyGrid();
    }

    @ConfigOption(path = "area-pos1")
    private Vector areaPos1;

    @ConfigOption(path = "area-pos2")
    private Vector areaPos2;

    @ConfigOption(path = "right-prepare-spot")
    private Location rightPrepareSpot;

    @ConfigOption(path = "left-prepare-spot")
    private Location leftPrepareSpot;

    @ConfigOption(path = "spectator-spawn-point")
    private Location spectatorSpawnPoint;

    @ConfigOption(path = "left-area.area-pos1")
    private Vector leftAreaAreaPos1;

    @ConfigOption(path = "left-area.area-pos2")
    private Vector leftAreaAreaPos2;

    @ConfigOption(path = "left-area.chaser-spawn-point")
    private Location leftAreaChaserSpawnPoint;

    @ConfigOption(path = "left-area.escapee-spawn-points")
    private List<String> leftAreaEscapeeSpawnPoints;

    @ConfigOption(path = "right-area.area-pos1")
    private Vector rightAreaAreaPos1;

    @ConfigOption(path = "right-area.area-pos2")
    private Vector rightAreaAreaPos2;

    @ConfigOption(path = "right-area.chaser-spawn-point")
    private Location rightAreaChaserSpawnPoint;

    @ConfigOption(path = "right-area.escapee-spawn-points")
    private List<String> rightAreaEscapeeSpawnPoints;

    @Override
    protected void customizeMigratedConfiguration(@NotNull YamlConfiguration oldConfiguration,
                                                  @NotNull YamlConfiguration migratedConfiguration) {
        if (!oldConfiguration.contains("right-prepare-spot") && oldConfiguration.contains("right-pre-spawn-point"))
            migratedConfiguration.set("right-prepare-spot", oldConfiguration.get("right-pre-spawn-point"));
        if (!oldConfiguration.contains("left-prepare-spot") && oldConfiguration.contains("left-pre-spawn-point"))
            migratedConfiguration.set("left-prepare-spot", oldConfiguration.get("left-pre-spawn-point"));

        if (oldConfiguration.getString("world-name", "").isBlank())
            migratedConfiguration.set("world-name", "parkourtag");
    }
}
