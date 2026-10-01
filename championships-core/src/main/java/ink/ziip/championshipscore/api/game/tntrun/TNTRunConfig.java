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

    /** Absolute world Y below which participants are eliminated; null preserves the old lower boundary. */
    @ConfigOption(path = "elimination-y", nullable = true)
    private Double eliminationY;

    @Override
    public void loadFromConfiguration(@NotNull YamlConfiguration configuration) {
        eliminationY = null;
        super.loadFromConfiguration(configuration);
        if (eliminationY != null && !Double.isFinite(eliminationY)) {
            throw new IllegalArgumentException("elimination-y must be finite");
        }
    }

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

    /** Suggested value for the editor; existing maps keep their original boundary until explicitly set. */
    public double getDefaultEliminationY() {
        List<BoundingBox> boxes = getCopyBoxes();
        if (!boxes.isEmpty()) return boxes.stream().mapToDouble(BoundingBox::getMinY).min().orElseThrow();
        if (areaPos1 != null && areaPos2 != null) return Math.min(areaPos1.getY(), areaPos2.getY());
        return getCopyGrid().origin(0).getY();
    }

    /** Keeps the template's horizontal/top limits, replacing only its lower limit when configured. */
    public boolean isInsidePlayerBounds(Vector point) {
        if (point == null) return false;
        List<BoundingBox> boxes = getCopyBoxes();
        for (BoundingBox box : boxes) {
            double lowerY = eliminationY == null ? box.getMinY() : eliminationY;
            if (point.getX() >= box.getMinX() && point.getX() < box.getMaxX()
                    && point.getZ() >= box.getMinZ() && point.getZ() < box.getMaxZ()
                    && point.getY() >= lowerY && point.getY() < box.getMaxY()) return true;
        }
        if (!boxes.isEmpty() || areaPos1 == null || areaPos2 == null) return false;
        Vector min = Vector.getMinimum(areaPos1, areaPos2);
        Vector max = Vector.getMaximum(areaPos1, areaPos2);
        double lowerY = eliminationY == null ? min.getY() : eliminationY;
        return point.getX() >= min.getX() && point.getX() <= max.getX()
                && point.getZ() >= min.getZ() && point.getZ() <= max.getZ()
                && point.getY() >= lowerY && point.getY() <= max.getY();
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


}
