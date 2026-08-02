package ink.ziip.championshipscore.api.game.dodgebolt;

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
public final class DodgeboltConfig extends BaseGameConfig {
    private final String resourceName = "dodgebolt/area.yml";
    private final String folderName = "dodgebolt/";

    public DodgeboltConfig(ChampionshipsCore plugin, String areaName) {
        super(plugin, areaName);
    }

    @Override
    public int getLatestVersion() {
        return 3;
    }

    @ConfigOption(path = "name")
    private String areaName;
    @ConfigOption(path = "area-pos1")
    private Vector areaPos1;
    @ConfigOption(path = "area-pos2")
    private Vector areaPos2;
    @ConfigOption(path = "spectator-area-pos1")
    private Vector spectatorAreaPos1;
    @ConfigOption(path = "spectator-area-pos2")
    private Vector spectatorAreaPos2;
    @ConfigOption(path = "platform-pos1")
    private Vector platformPos1;
    @ConfigOption(path = "platform-pos2")
    private Vector platformPos2;
    @ConfigOption(path = "right-area-pos1")
    private Vector rightAreaPos1;
    @ConfigOption(path = "right-area-pos2")
    private Vector rightAreaPos2;
    @ConfigOption(path = "left-area-pos1")
    private Vector leftAreaPos1;
    @ConfigOption(path = "left-area-pos2")
    private Vector leftAreaPos2;
    @ConfigOption(path = "right-shoot-pos1")
    private Vector rightShootPos1;
    @ConfigOption(path = "right-shoot-pos2")
    private Vector rightShootPos2;
    @ConfigOption(path = "left-shoot-pos1")
    private Vector leftShootPos1;
    @ConfigOption(path = "left-shoot-pos2")
    private Vector leftShootPos2;
    @ConfigOption(path = "right-spawn-points")
    private List<String> rightSpawnPoints;
    @ConfigOption(path = "left-spawn-points")
    private List<String> leftSpawnPoints;
    @ConfigOption(path = "right-arrow-spawn-point")
    private String rightArrowSpawnPoint;
    @ConfigOption(path = "left-arrow-spawn-point")
    private String leftArrowSpawnPoint;
    @ConfigOption(path = "spectator-spawn-point")
    private Location spectatorSpawnPoint;
    @ConfigOption(path = "round-restart-delay")
    private int roundRestartDelay;
    @ConfigOption(path = "shots-per-shrink")
    private int shotsPerShrink;
    @ConfigOption(path = "max-shrink-levels")
    private int maxShrinkLevels;

    @Override
    protected void customizeMigratedConfiguration(@NotNull YamlConfiguration oldConfiguration,
                                                  @NotNull YamlConfiguration migratedConfiguration) {
        migrateArrowSpawn(oldConfiguration, migratedConfiguration,
                "right-arrow-spawn-points", "right-arrow-spawn-point");
        migrateArrowSpawn(oldConfiguration, migratedConfiguration,
                "left-arrow-spawn-points", "left-arrow-spawn-point");
    }

    private static void migrateArrowSpawn(@NotNull YamlConfiguration oldConfiguration,
                                          @NotNull YamlConfiguration migratedConfiguration,
                                          @NotNull String oldPath, @NotNull String newPath) {
        if (migratedConfiguration.getString(newPath) != null) return;
        List<String> legacyPoints = oldConfiguration.getStringList(oldPath);
        if (!legacyPoints.isEmpty()) migratedConfiguration.set(newPath, legacyPoints.get(0));
    }
}
