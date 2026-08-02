package ink.ziip.championshipscore.api.game.tntrun;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.util.Utils;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

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
        return 1;
    }

    @ConfigOption(path = "name")
    private String areaName;

    @ConfigOption(path = "timer")
    private volatile int timer;

    @ConfigOption(path = "area-pos1")
    private Vector areaPos1;

    @ConfigOption(path = "area-pos2")
    private Vector areaPos2;

    @ConfigOption(path = "spectator-spawn-point")
    private Location spectatorSpawnPoint;

    /**
     * Legacy explicit list of per-copy spawn points (one per hand-built map copy). Kept only as a fallback
     * for areas configured before the prepare/template model; new areas leave it empty and use
     * {@link #copySpawn} + {@link #copies}. Read via {@link #getPlayerSpawnPoints()}, never the raw field.
     */
    @Getter(AccessLevel.NONE)
    @ConfigOption(path = "player-spawn-points")
    private List<String> playerSpawnPoints;

    /** Copy-0 spawn point; every copy's spawn is this shifted by {@link TNTRunLayout#delta(int)}. */
    @ConfigOption(path = "copy-spawn", nullable = true)
    private Location copySpawn;

    /** Number of arena copies stamped by {@code prepare}; drives how many spawns are derived. */
    @ConfigOption(path = "copies", nullable = true)
    private int copies;

    /** Block dimensions of one copy (the schematic size), recorded by {@code prepare}. */
    @ConfigOption(path = "copy-size", nullable = true)
    private Vector copySize;

    /**
     * Per-copy bounding boxes (one tight box per sub-arena), derived from the grid + {@link #copySize}.
     * Empty when not prepared (legacy areas fall back to the single {@code area-pos} box). Used so each
     * copy's players are bounded by their own sub-arena rather than one box spanning the gaps.
     */
    public List<BoundingBox> getCopyBoxes() {
        if (copies <= 0 || copySize == null) return Collections.emptyList();
        return ArenaPreparer.copyBoxes(TNTRunLayout.GRID, copies, copySize);
    }

    /**
     * Effective per-copy spawn points the game spreads players across. When the prepare/template fields
     * ({@link #copySpawn} + {@link #copies}) are set they are derived from the grid; otherwise this falls
     * back to the legacy hand-configured {@link #playerSpawnPoints} list.
     */
    public List<String> getPlayerSpawnPoints() {
        if (copySpawn != null && copies > 0) {
            List<String> derived = new ArrayList<>();
            for (int i = 0; i < copies; i++) {
                derived.add(Utils.getLocationConfigString(copySpawn.clone().add(TNTRunLayout.delta(i))));
            }
            return derived;
        }
        return playerSpawnPoints;
    }
}
