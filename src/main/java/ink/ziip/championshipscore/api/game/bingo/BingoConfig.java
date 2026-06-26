package ink.ziip.championshipscore.api.game.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.configuration.ConfigOption;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Per-area bingo configuration. World/spawn geometry mirrors the other CC games (a pre-built static
 * arena world copied from {@code plugin/maps/bingo}); the scoring fields make the points rules tunable
 * per area.
 */
@Getter
@Setter
public class BingoConfig extends BaseGameConfig {
    private final String resourceName = "bingo/areas/area.yml";
    private final String folderName = "bingo/areas/";

    public BingoConfig(@NotNull ChampionshipsCore plugin, String areaName) {
        super(plugin, areaName);
    }

    @Override
    public int getLatestVersion() {
        return 1;
    }

    @ConfigOption(path = "name")
    private String areaName;

    /** Round duration in seconds. */
    @ConfigOption(path = "timer")
    private int timer = 1200;

    /** Preparation countdown before the round starts, in seconds. */
    @ConfigOption(path = "prepare-time")
    private int prepareTime = 10;

    /** Card width (3 or 5). A 5-wide card has 12 lines (5 rows + 5 cols + 2 diagonals). */
    @ConfigOption(path = "card-width")
    private int cardWidth = 5;

    @ConfigOption(path = "area-pos1")
    private Vector areaPos1;

    @ConfigOption(path = "area-pos2")
    private Vector areaPos2;

    @ConfigOption(path = "spectator-spawn-point")
    private Location spectatorSpawnPoint;

    /** Scatter ring radius (blocks) around the world spawn that players are spread into at round start. */
    @ConfigOption(path = "scatter-ring-radius")
    private int scatterRingRadius = 1000;

    /** Random +/- variation added to the ring radius per player. */
    @ConfigOption(path = "scatter-ring-jitter")
    private int scatterRingJitter = 300;

    /** Max attempts to find a safe scatter spot per player before falling back to the world spawn. */
    @ConfigOption(path = "scatter-max-tries")
    private int scatterMaxTries = 32;

    /**
     * Points awarded by claim rank: index 0 = first team to complete a cell, 1 = second, etc. The last
     * value is the floor for every later claim (6th team and beyond). Default: 1st=50, 2nd=40, 3rd=30,
     * 4th=20, 5th=10, 6th+=5.
     */
    @ConfigOption(path = "points-per-rank")
    private List<Integer> pointsPerRank = List.of(50, 40, 30, 20, 10, 5);

    /**
     * Bonus for completing one of the first {@code line-bonus-major-count} lines. Line bonuses are
     * per-team and independent of other teams (each team can earn all 12). Default: first 4 lines = 200.
     */
    @ConfigOption(path = "line-bonus")
    private int lineBonus = 200;

    @ConfigOption(path = "line-bonus-major-count")
    private int lineBonusMajorCount = 4;

    /** Bonus for completing each subsequent (minor) line. Default: remaining 8 lines = 100 each. */
    @ConfigOption(path = "line-bonus-minor")
    private int lineBonusMinor = 100;

    /** Resolved points-per-rank as a primitive array, never empty. */
    public int[] pointsArray() {
        List<Integer> list = pointsPerRank;
        if (list == null || list.isEmpty()) return new int[]{1};
        int[] out = new int[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = list.get(i);
        return out;
    }
}
