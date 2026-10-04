package ink.ziip.championshipscore.api.game.decarnival.config;

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
        return 4;
    }

    @ConfigOption(path = "name")
    private String areaName;

    @ConfigOption(path = "area-pos1")
    private Vector areaPos1;

    @ConfigOption(path = "area-pos2")
    private Vector areaPos2;

    @ConfigOption(path = "spectator-spawn-point")
    private Location spectatorSpawnPoint;
}
