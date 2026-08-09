package ink.ziip.championshipscore.api.game.parkourtag;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.arena.ArenaGrid;
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
        return 8;
    }

    @ConfigOption(path = "name")
    private String areaName;

    /** Bound by the prepare flow before publication; blank in a new draft template. */
    @ConfigOption(path = "world-name", nullable = true)
    private String worldName;

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
        copyLayoutOrigin = areaPos1 == null || areaPos2 == null
                ? ParkourTagLayout.FIRST.clone()
                : Vector.getMinimum(areaPos1, areaPos2);
        // A PKT copy is a complete two-track match unit. Keep the established 512-block pitch even
        // when the captured schematic itself is much narrower; adjacent match infrastructure extends
        // beyond the tight clipboard footprint and must not be packed by the generic adaptive planner.
        copyLayoutStep = ParkourTagLayout.STEP.clone();
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

    /** Wall-mounted button used by slot A players to volunteer as their team's chaser. */
    @ConfigOption(path = "right-chaser-button")
    private Location rightChaserButton;

    /** Wall-mounted button used by slot B players to volunteer as their team's chaser. */
    @ConfigOption(path = "left-chaser-button")
    private Location leftChaserButton;

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

        // v8 introduces two required physical controls which cannot be inferred safely from an old map.
        // Keep the map unavailable until an administrator captures both buttons and republishes it.
        if (!oldConfiguration.contains("right-chaser-button")
                || !oldConfiguration.contains("left-chaser-button")) {
            migratedConfiguration.set("prepare.published", false);
            migratedConfiguration.set("prepare.dirty", true);
        }
    }
}
