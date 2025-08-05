package ink.ziip.championshipscore.api.game.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.util.Vector;

@Getter
@Setter
public class BingoConfig extends BaseGameConfig {
    private final String resourceName = "bingo/area.yml";
    private final String folderName = "bingo/";

    public BingoConfig(ChampionshipsCore championshipsCore, String areaName) {
        super(championshipsCore, areaName);
        spectatorSpawnPoint = CCConfig.BINGO_SPAWN_LOCATION;
        areaPos1 = new Vector(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        areaPos2 = new Vector(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        this.areaName = areaName;
    }

    private String areaName;

    private Vector areaPos1;

    private Vector areaPos2;

    private Location spectatorSpawnPoint;

    @Override
    public int getLatestVersion() {
        return 0;
    }
}
