package ink.ziip.championshipscore.api.game.laserbox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.arena.ArenaGrid;
import ink.ziip.championshipscore.api.game.arena.ArenaLayoutPlanner;
import ink.ziip.championshipscore.api.game.arena.RowArenaGrid;
import ink.ziip.championshipscore.configuration.ConfigOption;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import java.util.List;

@Getter @Setter
public final class LaserBoxConfig extends BaseGameConfig {
    public LaserBoxConfig(ChampionshipsCore plugin, String name) { super(plugin, name); }
    @Override public String getResourceName() { return "laserbox/area.yml"; }
    @Override public String getFolderName() { return "laserbox/"; }
    @Override public int getLatestVersion() { return 5; }
    @ConfigOption(path="name") private String areaName;
    @ConfigOption(path="world-name", nullable=true) private String worldName;
    @ConfigOption(path="timer") private int timer = 90;
    /** Number of independently runnable arenas using this map's geometry. */
    @ConfigOption(path="copy-count") private int copyCount = 1;
    @ConfigOption(path="copy-layout.origin", nullable=true) private Vector copyLayoutOrigin;
    @ConfigOption(path="copy-layout.step", nullable=true) private Vector copyLayoutStep;
    @ConfigOption(path="copy-layout.size", nullable=true) private Vector copySize;
    @ConfigOption(path="area-pos1", nullable=true) private Vector areaPos1;
    @ConfigOption(path="area-pos2", nullable=true) private Vector areaPos2;
    @ConfigOption(path="right-spawn-point", nullable=true) private Location rightSpawnPoint;
    @ConfigOption(path="left-spawn-point", nullable=true) private Location leftSpawnPoint;
    @ConfigOption(path="spectator-spawn-point", nullable=true) private Location spectatorSpawnPoint;
    @ConfigOption(path="supply-points") private List<String> supplyPoints = List.of();
    @ConfigOption(path="shield-hits") private int shieldHits = 1;
    @ConfigOption(path="scoring.kill") private int killPoints = 15;
    @ConfigOption(path="scoring.win") private int winPoints = 40;
    @ConfigOption(path="scoring.draw") private int drawPoints = 15;

    public ArenaGrid getCopyGrid() {
        Vector origin = copyLayoutOrigin != null ? copyLayoutOrigin
                : areaPos1 != null && areaPos2 != null ? Vector.getMinimum(areaPos1, areaPos2) : new Vector();
        Vector step = copyLayoutStep != null ? copyLayoutStep : copyStep();
        return new RowArenaGrid(origin, step);
    }

    public ArenaGrid prepareCopyGrid(Vector size) {
        copyLayoutOrigin = areaPos1 == null || areaPos2 == null
                ? new Vector() : Vector.getMinimum(areaPos1, areaPos2);
        copySize = size.clone();
        copyLayoutStep = ArenaLayoutPlanner.rowStep(size);
        return getCopyGrid();
    }

    private Vector copyStep() {
        double width = copySize != null ? copySize.getX()
                : areaPos1 != null && areaPos2 != null ? Math.abs(areaPos1.getX() - areaPos2.getX()) + 1 : 0;
        return new Vector(Math.max(64, width + 32), 0, 0);
    }

    public Location bind(Location point) {
        if (point == null) return null;
        Location result = point.clone();
        result.setWorld(Bukkit.getWorld(getConfiguredWorld()));
        return result;
    }
    public List<Location> supplies() {
        World world = Bukkit.getWorld(getConfiguredWorld());
        return supplyPoints.stream().map(raw -> {
            Vector point = parseSupplyPoint(raw, getConfiguredWorld());
            return point.toLocation(world);
        }).toList();
    }

    static Vector parseSupplyPoint(String raw, String worldName) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("补给点坐标不能为空");
        String[] parts = raw.trim().split(":", -1);
        if (parts.length != 6 || worldName == null || !worldName.equals(parts[0]))
            throw new IllegalArgumentException("补给点格式必须为 当前世界:x:y:z:yaw:pitch");
        finite(parts[4]);
        finite(parts[5]);
        return new Vector(finite(parts[1]), finite(parts[2]), finite(parts[3]));
    }

    private static double finite(String value) {
        try {
            double number = Double.parseDouble(value);
            if (Double.isFinite(number)) return number;
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException("补给点坐标必须为有限数字");
    }
    public boolean contains(Location point) {
        if (point == null || point.getWorld() == null || !point.getWorld().getName().equals(getConfiguredWorld())
                || areaPos1 == null || areaPos2 == null) return false;
        return point.toVector().isInAABB(Vector.getMinimum(areaPos1, areaPos2),
                Vector.getMaximum(areaPos1, areaPos2).add(new Vector(1, 1, 1)));
    }
    public void validate() {
        if (timer < 1 || copyCount < 1 || shieldHits < 1 || shieldHits > 100 || killPoints < 0 || winPoints < 0 || drawPoints < 0)
            throw new IllegalArgumentException("时长/护盾必须为正数，积分不能为负数");
        if (!contains(bind(rightSpawnPoint)) || !contains(bind(leftSpawnPoint)) || !contains(bind(spectatorSpawnPoint)))
            throw new IllegalArgumentException("双方出生点和观战点必须在已加载世界的边界内");
        if (supplyPoints.isEmpty() || supplies().stream().anyMatch(p -> !contains(p)))
            throw new IllegalArgumentException("请在边界内设置补给点");
    }
}
