package ink.ziip.championshipscore.api.game.tgttos;

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
public class TGTTOSConfig extends BaseGameConfig {
    private final String resourceName = "tgttos/area.yml";
    private final String folderName = "tgttos/";

    public TGTTOSConfig(@NotNull ChampionshipsCore plugin, String areaName) {
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

    @ConfigOption(path = "area-type")
    private String areaType;

    @ConfigOption(path = "spectator-spawn-point")
    private Location spectatorSpawnPoint;

    @ConfigOption(path = "monster-spawn-points")
    private List<String> monsterSpawnPoints;

    @ConfigOption(path = "chicken-spawn-points")
    private List<String> chickenSpawnPoints;

    @ConfigOption(path = "player-spawn-points")
    private List<String> playerSpawnPoints;
}
