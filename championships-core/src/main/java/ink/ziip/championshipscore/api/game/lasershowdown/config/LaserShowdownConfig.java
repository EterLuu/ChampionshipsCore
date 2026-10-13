package ink.ziip.championshipscore.api.game.lasershowdown.config;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.FourArenaConfig;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.configuration.location.ConfiguredLocation;
import ink.ziip.championshipscore.configuration.location.LocationConfig;

import lombok.Getter;
import lombok.Setter;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class LaserShowdownConfig extends FourArenaConfig {
    private final String resourceName = "lasershowdown/area.yml";
    private final String folderName = "lasershowdown/";

    public LaserShowdownConfig(ChampionshipsCore championshipsCore, String areaName) {
        super(championshipsCore, areaName);
    }

    @Override
    public int getLatestVersion() {
        return 3;
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

    public List<String> templateSpawns() {
        return new ArrayList<>(ensurePlayerSpawnPoints().getStringList("area1"));
    }

    /** Generated maps derive all four lists; prebuilt maps retain their individual coordinates. */
    public List<List<Location>> arenaSpawns() {
        if (!hasCopyLayout()) {
            ConfigurationSection section = ensurePlayerSpawnPoints();
            return section.getKeys(false).stream()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .map(
                            key ->
                                    section.getStringList(key).stream()
                                            .map(LocationConfig::readLocation)
                                            .toList())
                    .toList();
        }
        List<List<Location>> result = new ArrayList<>();
        for (int arena = 0; arena < ARENA_COUNT; arena++) {
            Vector offset = getCopyGrid().delta(arena);
            result.add(
                    templateSpawns().stream()
                            .map(
                                    raw ->
                                            ConfiguredLocation.read(raw)
                                                    .requireWorld(getConfiguredWorld())
                                                    .resolve(
                                                            identifier ->
                                                                    Bukkit.getWorld(
                                                                            getConfiguredWorld()))
                                                    .add(offset))
                            .toList());
        }
        return result;
    }

    @Override
    public Vector getAreaPos1() {
        return hasCopyLayout() ? getCopyGrid().origin(0) : areaPos1;
    }

    @Override
    public Vector getAreaPos2() {
        return hasCopyLayout()
                ? getCopyGrid().origin(3).add(getCopySize()).subtract(new Vector(1, 1, 1))
                : areaPos2;
    }

    public void validate() {
        validateCopies();
        if (timer < 1 || templateSpawns().isEmpty() || templateSpawns().size() > 128)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("laser-showdown-config.invalid-spawns"));
        for (String raw : templateSpawns()) {
            ConfiguredLocation point =
                    ConfiguredLocation.read(raw).requireWorld(getConfiguredWorld());
            if (!new Vector(point.x(), point.y(), point.z()).isInAABB(getArenaMin(), getArenaMax()))
                throw new IllegalArgumentException(
                        MessageConfig.codeText("laser-showdown-config.spawn-outside", raw));
        }
    }

    public boolean contains(Location location, int arena) {
        if (!hasCopyLayout()
                || location == null
                || location.getWorld() == null
                || !location.getWorld().getName().equals(getConfiguredWorld())) return false;
        Vector point = location.toVector().subtract(getCopyGrid().delta(arena));
        return point.isInAABB(getArenaMin(), getArenaMax().clone().add(new Vector(1, 1, 1)));
    }
}
