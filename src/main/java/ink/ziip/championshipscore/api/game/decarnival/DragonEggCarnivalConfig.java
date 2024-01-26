package ink.ziip.championshipscore.api.game.decarnival;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.configuration.ConfigOption;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.util.Vector;

@Getter
@Setter
public class DragonEggCarnivalConfig extends BaseGameConfig {
    private final String resourceName = "decarnival/area.yml";
    private final String folderName = "decarnival/";

    public DragonEggCarnivalConfig(ChampionshipsCore championshipsCore, String areaName) {
        super(championshipsCore, areaName);
    }

    @Override
    public int getLatestVersion() {
        return 1;
    }

    @ConfigOption(path = "name")
    private String areaName;

    @ConfigOption(path = "area-pos1")
    private Vector areaPos1;

    @ConfigOption(path = "area-pos2")
    private Vector areaPos2;

    @ConfigOption(path = "right-spawn-point")
    private Location rightSpawnPoint;

    @ConfigOption(path = "left-spawn-point")
    private Location leftSpawnPoint;

    @ConfigOption(path = "spectator-spawn-point")
    private Location spectatorSpawnPoint;

    @ConfigOption(path = "dragon-egg-spawn-point")
    private Location dragonEggSpawnPoint;
}
