package ink.ziip.championshipscore.api.game.battlebox;

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
public class BattleBoxConfig extends BaseGameConfig {
    private final String resourceName = "battlebox/area.yml";
    private final String folderName = "battlebox/";

    public BattleBoxConfig(ChampionshipsCore championshipsCore, String areaName) {
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
        Vector origin = copyLayoutOrigin == null ? BattleBoxLayout.FIRST : copyLayoutOrigin;
        Vector step = copyLayoutStep == null ? BattleBoxLayout.STEP : copyLayoutStep;
        return new RowArenaGrid(origin, step);
    }

    public @NotNull ArenaGrid prepareCopyGrid(@NotNull Vector size) {
        copyLayoutOrigin = areaPos1 == null || areaPos2 == null
                ? BattleBoxLayout.FIRST.clone()
                : Vector.getMinimum(areaPos1, areaPos2);
        copyLayoutStep = ArenaLayoutPlanner.rowStep(size);
        copySize = size.clone();
        return getCopyGrid();
    }

    @ConfigOption(path = "right-spawn-point")
    private Location rightSpawnPoint;

    @ConfigOption(path = "left-spawn-point")
    private Location leftSpawnPoint;

    @ConfigOption(path = "right-prepare-spot")
    private Location rightPrepareSpot;

    @ConfigOption(path = "left-prepare-spot")
    private Location leftPrepareSpot;

    @ConfigOption(path = "spectator-spawn-point")
    private Location spectatorSpawnPoint;

    @ConfigOption(path = "wool-pos1")
    private Vector woolPos1;

    @ConfigOption(path = "wool-pos2")
    private Vector woolPos2;

    @ConfigOption(path = "area-pos1")
    private Vector areaPos1;

    @ConfigOption(path = "area-pos2")
    private Vector areaPos2;

    @ConfigOption(path = "potion-spawn-points")
    private List<String> potionSpawnPoints;

    @Override
    protected void customizeMigratedConfiguration(@NotNull YamlConfiguration oldConfiguration,
                                                  @NotNull YamlConfiguration migratedConfiguration) {
        if (!oldConfiguration.contains("right-prepare-spot") && oldConfiguration.contains("right-pre-spawn-point"))
            migratedConfiguration.set("right-prepare-spot", oldConfiguration.get("right-pre-spawn-point"));
        if (!oldConfiguration.contains("left-prepare-spot") && oldConfiguration.contains("left-pre-spawn-point"))
            migratedConfiguration.set("left-prepare-spot", oldConfiguration.get("left-pre-spawn-point"));

        if (oldConfiguration.getString("world-name", "").isBlank())
            migratedConfiguration.set("world-name", "battlebox");

        if (oldConfiguration.getInt("copy-count", 0) > 0
                && (!oldConfiguration.contains("area-pos1") || !oldConfiguration.contains("area-pos2"))) {
            migratedConfiguration.set("prepare.published", false);
            migratedConfiguration.set("prepare.dirty", true);
        }
    }
}
