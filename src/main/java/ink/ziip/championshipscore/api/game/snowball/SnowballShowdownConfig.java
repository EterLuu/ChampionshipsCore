package ink.ziip.championshipscore.api.game.snowball;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.configuration.ConfigOption;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

@Getter
@Setter
public class SnowballShowdownConfig extends BaseGameConfig {
    private final String resourceName = "snowball/area.yml";
    private final String folderName = "snowball/";

    public SnowballShowdownConfig(ChampionshipsCore championshipsCore, String areaName) {
        super(championshipsCore, areaName);
    }

    @Override
    public int getLatestVersion() {
        return 2;
    }

    @ConfigOption(path = "name")
    private String areaName;

    @ConfigOption(path = "timer")
    private int timer;

    @ConfigOption(path = "area-pos1")
    private Vector areaPos1;

    @ConfigOption(path = "area-pos2")
    private Vector areaPos2;

    @ConfigOption(path = "spectator-spawn-point")
    private Location spectatorSpawnPoint;

    @ConfigOption(path = "player-spawn-points")
    private ConfigurationSection playerSpawnPoints;

    /** Ensures newly-created maps have the section used by the prepare list steps. */
    public ConfigurationSection ensurePlayerSpawnPoints() {
        if (playerSpawnPoints == null) {
            playerSpawnPoints = configuration.createSection("player-spawn-points");
        }
        return playerSpawnPoints;
    }
}
