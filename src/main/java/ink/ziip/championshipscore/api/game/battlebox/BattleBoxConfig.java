package ink.ziip.championshipscore.api.game.battlebox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.configuration.ConfigOption;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.List;

@Getter
@Setter
public class BattleBoxConfig extends BaseGameConfig {
    private final String resourceName = "battlebox/area.yml";
    private final String folderName = "battlebox/";

    public BattleBoxConfig(ChampionshipsCore championshipsCore, String areaName) {
        super(championshipsCore, areaName);
        this.areaName = areaName;
        this.timer = 60;
    }

    @Override
    public int getLatestVersion() {
        return 1;
    }

    @ConfigOption(path = "name")
    private String areaName;

    @ConfigOption(path = "timer")
    private int timer;

    @ConfigOption(path = "right-spawn-point")
    private Location rightSpawnPoint;

    @ConfigOption(path = "left-spawn-point")
    private Location leftSpawnPoint;

    @ConfigOption(path = "right-pre-spawn-point")
    private Location rightPreSpawnPoint;

    @ConfigOption(path = "left-pre-spawn-point")
    private Location leftPreSpawnPoint;

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
}
