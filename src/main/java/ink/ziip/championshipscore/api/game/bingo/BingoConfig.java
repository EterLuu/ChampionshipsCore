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
        return 4;
    }

    @ConfigOption(path = "name")
    private String areaName;

    /** Round duration in seconds (10 minutes per the bingo design doc). */
    @ConfigOption(path = "timer")
    private int timer = 600;

    /** Preparation countdown before the round starts, in seconds. */
    @ConfigOption(path = "prepare-time")
    private int prepareTime = 10;

    /** Card width (3 or 5). A 5-wide card has 12 lines (5 rows + 5 cols + 2 diagonals). */
    @ConfigOption(path = "card-width")
    private int cardWidth = 5;

    /**
     * Unused for bingo (whole-world play, no area bounding-box); kept only to satisfy the
     * {@link BaseGameConfig} area contract. Always null, hence nullable so a missing key does not
     * log a spurious "Failed to find configuration file" error on load.
     */
    @ConfigOption(path = "area-pos1", nullable = true)
    private Vector areaPos1;

    /** Unused for bingo (whole-world play); see {@link #areaPos1}. */
    @ConfigOption(path = "area-pos2", nullable = true)
    private Vector areaPos2;

    /** Optional explicit spectator viewpoint; falls back to the bingo world spawn when unset. */
    @ConfigOption(path = "spectator-spawn-point", nullable = true)
    private Location spectatorSpawnPoint;

    /**
     * Radius (blocks) of the disc around the world spawn that players are randomly placed within at
     * round start. Each player lands at a uniformly random point in the disc (safe top-of-surface spot
     * only); at small radii players may overlap, which is intended.
     */
    @ConfigOption(path = "scatter-radius")
    private int scatterRadius = 6;

    /** Max attempts to find a safe scatter spot per player before falling back to the world spawn. */
    @ConfigOption(path = "scatter-max-tries")
    private int scatterMaxTries = 32;

    /**
     * Permanent potion effects granted to every participant for the whole round (applied at round
     * start, kept alive via the tracker, cleared at round end). Each entry is {@code "<effect>:<level>"}
     * where {@code <level>} is the in-game displayed level (1 = I, 8 = VIII) and may be omitted
     * (defaults to I); {@code <effect>} is a vanilla PotionEffectType key (night_vision, jump_boost,
     * slow_falling, speed, haste, …). Slow Falling is auto-removed while a participant is gliding
     * with an elytra (it cancels elytra flight). See {@link BingoPermanentEffects}.
     */
    @ConfigOption(path = "permanent-effects")
    private List<String> permanentEffects = List.of("night_vision:1", "jump_boost:8", "slow_falling:1");

    /**
     * Points awarded by claim rank: index 0 = first team to complete a cell, 1 = second, etc. The last
     * value is the floor for every later claim (6th team and beyond). Per the design doc:
     * 1st=60, 2nd=50, 3rd=40, 4th=30, 5th=20, 6th+=10. The completing player earns the cell points.
     */
    @ConfigOption(path = "points-per-rank")
    private List<Integer> pointsPerRank = List.of(60, 50, 40, 30, 20, 10);

    /**
     * Bonus for completing one of the first {@code line-bonus-major-count} lines. Awarded to every
     * member of the team ("队内所有成员+50"), not just the completing player. Per the design doc:
     * first 4 lines = 50 each (per member).
     */
    @ConfigOption(path = "line-bonus")
    private int lineBonus = 50;

    @ConfigOption(path = "line-bonus-major-count")
    private int lineBonusMajorCount = 4;

    /** Bonus for completing each subsequent (minor) line, per team member. Per the design doc: 25 each. */
    @ConfigOption(path = "line-bonus-minor")
    private int lineBonusMinor = 25;

    /** Resolved points-per-rank as a primitive array, never empty. */
    public int[] pointsArray() {
        List<Integer> list = pointsPerRank;
        if (list == null || list.isEmpty()) return new int[]{1};
        int[] out = new int[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = list.get(i);
        return out;
    }
}
