package ink.ziip.championshipscore.api.game.tgttos;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.configuration.ConfigOption;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
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
        return 4;
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

    @ConfigOption(path = "chicken-spawn-area-pos1", nullable = true)
    private Vector chickenSpawnAreaPos1;

    @ConfigOption(path = "chicken-spawn-area-pos2", nullable = true)
    private Vector chickenSpawnAreaPos2;

    @ConfigOption(path = "player-spawn-area-pos1", nullable = true)
    private Vector playerSpawnAreaPos1;

    @ConfigOption(path = "player-spawn-area-pos2", nullable = true)
    private Vector playerSpawnAreaPos2;

    @ConfigOption(path = "player-spawn-yaw", nullable = true)
    private Float playerSpawnYaw;

    @ConfigOption(path = "player-spawn-pitch", nullable = true)
    private Float playerSpawnPitch;

    @Override
    protected void customizeMigratedConfiguration(@NotNull YamlConfiguration oldConfiguration,
                                                  @NotNull YamlConfiguration migratedConfiguration) {
        boolean hadLegacySpawnPoints = oldConfiguration.contains("chicken-spawn-points")
                || oldConfiguration.contains("player-spawn-points");
        if (!hadLegacySpawnPoints) return;

        // Point lists cannot safely be converted to random spawn planes: the rectangle between those
        // points may contain track blocks or hazards. Keep them for the editor and require reconfiguration.
        migratedConfiguration.set("legacy-chicken-spawn-points",
                oldConfiguration.getStringList("chicken-spawn-points"));
        migratedConfiguration.set("legacy-player-spawn-points",
                oldConfiguration.getStringList("player-spawn-points"));
        migratedConfiguration.set("chicken-spawn-points", null);
        migratedConfiguration.set("player-spawn-points", null);
        migratedConfiguration.set("prepare.published", false);
        migratedConfiguration.set("prepare.dirty", true);
    }
}
