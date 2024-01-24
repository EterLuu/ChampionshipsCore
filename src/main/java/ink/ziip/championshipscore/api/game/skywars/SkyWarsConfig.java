package ink.ziip.championshipscore.api.game.skywars;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.configuration.ConfigOption;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Getter
@Setter
public class SkyWarsConfig extends BaseGameConfig {
    private final String resourceName = "skywars/area.yml";
    private final String folderName = "skywars/";

    public SkyWarsConfig(@NotNull ChampionshipsCore plugin, String areaName) {
        super(plugin, areaName);
    }

    @Override
    public int getLatestVersion() {
        return 1;
    }

    @ConfigOption(path = "name")
    private String areaName;

    @ConfigOption(path = "timer")
    private int timer;

    @ConfigOption(path = "area-pos1")
    private Vector areaPos1;

    @ConfigOption(path = "area-pos2")
    private Vector areaPos2;

    @ConfigOption(path = "pre-spawn-point")
    private Location preSpawnPoint;

    @ConfigOption(path = "spectator-spawn-point")
    private Location spectatorSpawnPoint;

    @ConfigOption(path = "team-spawn-points")
    private List<String> teamSpawnPoints;

    @ConfigOption(path = "boundary.default-height")
    private Integer boundaryDefaultHeight;

    @ConfigOption(path = "boundary.lowest-height")
    private Integer boundaryLowestHeight;

    @ConfigOption(path = "boundary.radius")
    private Integer boundaryRadius;

    @ConfigOption(path = "time.enable-boundary-shrink")
    private Integer timeEnableBoundaryShrink;

    @ConfigOption(path = "time.disable-health-regain")
    private Integer timeDisableHealthRegain;
}
