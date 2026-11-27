package ink.ziip.championshipscore.api.game.iamnotarobot.config;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.VerificationCourse;
import ink.ziip.championshipscore.configuration.ConfigOption;

import lombok.Getter;
import lombok.Setter;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.util.Vector;

@Getter
public final class RobotConfig extends BaseGameConfig {
    private final String folderName = "iamnotarobot/areas/";
    private final String resourceName = "iamnotarobot/area.yml";

    @Setter
    @ConfigOption(path = "name")
    private String areaName;

    @ConfigOption(path = "world-name")
    private String worldName;

    @Setter
    @ConfigOption(path = "lanes")
    private int lanes;

    @ConfigOption(path = "timer")
    private int timer;

    public RobotConfig(ChampionshipsCore plugin, String name) {
        super(plugin, name);
    }

    public static final int MAP_VERSION = 20;

    @Override
    public int getLatestVersion() {
        return MAP_VERSION;
    }

    @Override
    public Vector getAreaPos1() {
        return VerificationCourse.VENUE_GRID.origin(0);
    }

    @Override
    public Vector getAreaPos2() {
        return VerificationCourse.VENUE_GRID
                .origin(Math.max(0, lanes - 1))
                .add(VerificationCourse.VENUE_SIZE)
                .subtract(new Vector(1, 1, 1));
    }

    @Override
    public Location getSpectatorSpawnPoint() {
        var world = worldName == null || worldName.isBlank() ? null : Bukkit.getWorld(worldName);
        return world == null
                ? null
                : VerificationCourse.GRID
                        .origin(0)
                        .add(new Vector(9.5, VerificationCourse.HEIGHT, 5.5))
                        .toLocation(world);
    }

    @Override
    public boolean isPrepareReady() {
        return super.isPrepareReady()
                && isPrepareWorldBuilt()
                && validSettings()
                && getSpectatorSpawnPoint() != null;
    }

    public static final int MAX_SECONDS = 15 * 60;

    public boolean validSettings() {
        return lanes >= 1
                && lanes <= VerificationCourse.MAX_LANES
                && timer > 0
                && timer <= MAX_SECONDS;
    }
}
