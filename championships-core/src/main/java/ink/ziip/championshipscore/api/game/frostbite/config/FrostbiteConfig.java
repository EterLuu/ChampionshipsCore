package ink.ziip.championshipscore.api.game.frostbite.config;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.configuration.location.ConfiguredLocation;

import lombok.Getter;
import lombok.Setter;

import org.bukkit.*;
import org.bukkit.util.Vector;

import java.util.*;

@Getter
@Setter
public final class FrostbiteConfig extends BaseGameConfig {
    private final String resourceName = "frostbite/area.yml";
    private final String folderName = "frostbite/";

    @ConfigOption(path = "name")
    private String areaName;

    @ConfigOption(path = "world-name")
    private String worldName;

    @ConfigOption(path = "timer")
    private int timer = 210;

    @ConfigOption(path = "freeze-seconds")
    private int freezeSeconds = 5;

    @ConfigOption(path = "heat-seconds")
    private int heatSeconds = 3;

    @ConfigOption(path = "item-respawn-seconds")
    private int itemRespawnSeconds = 30;

    @ConfigOption(path = "points-per-kill")
    private int pointsPerKill = 6;

    @ConfigOption(path = "rank-points-per-player")
    private List<Integer> rankPointsPerPlayer = List.of(60, 55, 50, 45, 40, 35, 30, 25, 20, 15, 10);

    @ConfigOption(path = "copy-spacing")
    private int copySpacing = 256;

    @ConfigOption(path = "arena-min")
    private Vector arenaMin;

    @ConfigOption(path = "arena-max")
    private Vector arenaMax;

    @ConfigOption(path = "spawn-points")
    private List<String> spawnPoints = List.of();

    @ConfigOption(path = "item-points")
    private List<String> itemPoints = List.of();

    @ConfigOption(path = "spectator-spawn", nullable = true)
    private Location spectatorSpawnPoint;

    public FrostbiteConfig(ChampionshipsCore plugin, String name) {
        super(plugin, name);
    }

    @Override
    public int getLatestVersion() {
        return 2;
    }

    @Override
    public Vector getAreaPos1() {
        return arenaMin;
    }

    @Override
    public Vector getAreaPos2() {
        return arenaMax == null
                ? null
                : arenaMax.clone().add(new Vector(copySpacing, 0, copySpacing));
    }

    public Vector offset(int arena) {
        return new Vector((arena % 2) * copySpacing, 0, (arena / 2) * copySpacing);
    }

    public Location point(String text, int arena) {
        Location location = parsePoint(text, worldName);
        location.setWorld(Bukkit.getWorld(worldName));
        return location.add(offset(arena));
    }

    static Location parsePoint(String text, String worldName) {
        ConfiguredLocation point = ConfiguredLocation.read(text);
        if (point == null) throw new IllegalArgumentException("坐标不能为空");
        return point.requireWorld(worldName).resolve(identifier -> null);
    }

    public List<Location> spawns(int arena) {
        return spawnPoints.stream().map(p -> point(p, arena)).toList();
    }

    public boolean contains(Location location, int arena) {
        if (location.getWorld() == null || !location.getWorld().getName().equals(worldName))
            return false;
        Vector p = location.toVector().subtract(offset(arena));
        return p.isInAABB(arenaMin, arenaMax);
    }

    public void validate() {
        if (timer < 30
                || timer > 600
                || freezeSeconds < 1
                || freezeSeconds > 15
                || heatSeconds < 1
                || heatSeconds > 10
                || itemRespawnSeconds < 1
                || itemRespawnSeconds > 60
                || pointsPerKill < 0
                || pointsPerKill > 1000) throw new IllegalArgumentException("时长、冻结、保温、补给或击杀积分超出范围");
        if (rankPointsPerPlayer == null
                || rankPointsPerPlayer.size() > 16
                || rankPointsPerPlayer.stream()
                        .anyMatch(points -> points == null || points < 0 || points > 1000))
            throw new IllegalArgumentException("排名奖励需要最多16项，每项积分须为0–1000");
        if (arenaMin == null
                || arenaMax == null
                || copySpacing < 64
                || copySpacing > 4096
                || arenaMin.getX() >= arenaMax.getX()
                || arenaMin.getY() >= arenaMax.getY()
                || arenaMin.getZ() >= arenaMax.getZ()
                || arenaMax.getX() - arenaMin.getX() >= copySpacing
                || arenaMax.getZ() - arenaMin.getZ() >= copySpacing)
            throw new IllegalArgumentException("四张地图边界缺失或互相重叠");
        if (spawnPoints.size() < 16
                || spawnPoints.size() > 128
                || itemPoints.isEmpty()
                || itemPoints.size() > 128)
            throw new IllegalArgumentException("需要 16–128 个重生点和 1–128 个补给点");
        for (String p :
                java.util.stream.Stream.concat(spawnPoints.stream(), itemPoints.stream())
                        .toList()) {
            Location l = point(p, 0);
            if (!Double.isFinite(l.getX())
                    || !Double.isFinite(l.getY())
                    || !Double.isFinite(l.getZ())
                    || !Float.isFinite(l.getYaw())
                    || !l.toVector().isInAABB(arenaMin, arenaMax))
                throw new IllegalArgumentException("坐标无效或超出地图：" + p);
        }
    }
}
