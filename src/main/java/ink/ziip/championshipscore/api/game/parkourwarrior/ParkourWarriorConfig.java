package ink.ziip.championshipscore.api.game.parkourwarrior;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.configuration.ConfigOption;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
public class ParkourWarriorConfig extends BaseGameConfig {
    private final String resourceName = "parkourwarrior/area.yml";
    private final String folderName = "parkourwarrior/";

    public ParkourWarriorConfig(@NotNull ChampionshipsCore plugin, String areaName) {
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

    @ConfigOption(path = "sudden-death")
    private int suddenDeath;

    @ConfigOption(path = "area-pos1")
    private Vector areaPos1;

    @ConfigOption(path = "area-pos2")
    private Vector areaPos2;

    @ConfigOption(path = "spectator-spawn-point")
    private Location spectatorSpawnPoint;

    @ConfigOption(path = "player-spawn-point")
    private Location playerSpawnPoint;

    @ConfigOption(path = "points.2star")
    private int points2;

    @ConfigOption(path = "points.3star")
    private int points3;

    @ConfigOption(path = "points.4star")
    private int points4;

    @ConfigOption(path = "points.5star")
    private int points5;

    @ConfigOption(path = "points.gradient")
    private int pointsGradient;

    @ConfigOption(path = "final-points.easy")
    private double finalPointsEasy;

    @ConfigOption(path = "final-points.normal")
    private double finalPointsNormal;

    @ConfigOption(path = "final-points.hard")
    private double finalPointsHard;

    @ConfigOption(path = "checkpoints")
    private ConfigurationSection checkpoints;
}
