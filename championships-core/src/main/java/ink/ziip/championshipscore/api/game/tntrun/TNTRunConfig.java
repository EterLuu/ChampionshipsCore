package ink.ziip.championshipscore.api.game.tntrun;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.arena.ArenaGrid;
import ink.ziip.championshipscore.api.game.arena.ArenaLayoutPlanner;
import ink.ziip.championshipscore.api.game.arena.RowArenaGrid;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
public class TNTRunConfig extends BaseGameConfig {
    private final String resourceName = "tntrun/area.yml";
    private final String folderName = "tntrun/";

    public TNTRunConfig(ChampionshipsCore championshipsCore, String areaName) {
        super(championshipsCore, areaName);
    }

    @Override
    public int getLatestVersion() {
        return 6;
    }

    @ConfigOption(path = "name")
    private String areaName;

    @ConfigOption(path = "timer")
    private int timer;

    @ConfigOption(path = "area-pos1")
    private Vector areaPos1;

    @ConfigOption(path = "area-pos2")
    private Vector areaPos2;

    @ConfigOption(path = "spectator-spawn-point")
    private Location spectatorSpawnPoint;

    /** Optional explicit per-copy spawns; when empty, stamped copies derive them from copy 0. */
    @ConfigOption(path = "spawn-points")
    private List<String> spawnPoints;

    /** Copy-0 spawn point; every copy's spawn is this shifted by {@link TNTRunLayout#delta(int)}. */
    @ConfigOption(path = "copy-spawn", nullable = true)
    private Location copySpawn;

    /** Number of arena copies stamped by {@code prepare}; drives how many spawns are derived. */
    @ConfigOption(path = "copies", nullable = true)
    private int copies;

    /** Block dimensions of one copy (the schematic size), recorded by {@code prepare}. */
    @ConfigOption(path = "copy-size", nullable = true)
    private Vector copySize;

    @ConfigOption(path = "copy-layout.origin", nullable = true)
    private Vector copyLayoutOrigin;

    @ConfigOption(path = "copy-layout.step", nullable = true)
    private Vector copyLayoutStep;

    public ArenaGrid getCopyGrid() {
        Vector origin = copyLayoutOrigin == null ? TNTRunLayout.FIRST : copyLayoutOrigin;
        Vector step = copyLayoutStep == null ? TNTRunLayout.STEP : copyLayoutStep;
        return new RowArenaGrid(origin, step);
    }

    public ArenaGrid prepareCopyGrid(Vector size) {
        copyLayoutOrigin = areaPos1 == null || areaPos2 == null
                ? TNTRunLayout.FIRST.clone()
                : Vector.getMinimum(areaPos1, areaPos2);
        copyLayoutStep = ArenaLayoutPlanner.rowStep(size);
        copySize = size.clone();
        return getCopyGrid();
    }

    /**
     * Per-copy bounding boxes (one tight box per sub-arena), derived from the grid + {@link #copySize}.
     * Empty when the map uses its configured aggregate {@code area-pos} box. Used so each
     * copy's players are bounded by their own sub-arena rather than one box spanning the gaps.
     */
    public List<BoundingBox> getCopyBoxes() {
        if (copies <= 0 || copySize == null) return Collections.emptyList();
        return ArenaPreparer.copyBoxes(getCopyGrid(), copies, copySize);
    }

    /**
     * Effective per-copy spawn points the game spreads players across. When the prepare/template fields
     * ({@link #copySpawn} + {@link #copies}) are set they are derived from the grid; otherwise explicit
     * {@link #spawnPoints} are used.
     */
    public List<String> getPlayerSpawnPoints() {
        if (copySpawn != null && copies > 0) {
            List<String> derived = new ArrayList<>();
            for (int i = 0; i < copies; i++) {
                derived.add(Utils.getLocationConfigString(copySpawn.clone().add(getCopyGrid().delta(i))));
            }
            return derived;
        }
        return spawnPoints;
    }

    @Override
    protected void customizeMigratedConfiguration(@NotNull YamlConfiguration oldConfiguration,
                                                  @NotNull YamlConfiguration migratedConfiguration) {
        // Prepared copies must be anchored to the hand-built source selection. Older prepared maps did
        // not persist that selection and therefore derived every generated copy from the wrong origin.
        if (oldConfiguration.getInt("copies", 0) > 0
                && (!oldConfiguration.contains("area-pos1") || !oldConfiguration.contains("area-pos2"))) {
            migratedConfiguration.set("prepare.published", false);
            migratedConfiguration.set("prepare.dirty", true);
        }

        // Version 5 introduced prepare metadata and marked legacy, single-arena maps dirty even when
        // their original aggregate bounds and explicit spawn points are still complete. Those maps are
        // already valid published maps; keep newly stamped/copy-based drafts locked instead.
        if (oldConfiguration.getBoolean("prepare.published", false)
                && oldConfiguration.getBoolean("prepare.dirty", false)
                && oldConfiguration.getInt("copies", 0) <= 0
                && oldConfiguration.getBoolean("prepare.world-built", false)
                && oldConfiguration.contains("area-pos1")
                && oldConfiguration.contains("area-pos2")
                && !oldConfiguration.getStringList("spawn-points").isEmpty()) {
            migratedConfiguration.set("prepare.dirty", false);
        }
    }
}
