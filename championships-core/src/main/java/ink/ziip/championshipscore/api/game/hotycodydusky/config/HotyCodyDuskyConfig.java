package ink.ziip.championshipscore.api.game.hotycodydusky.config;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.arena.*;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.configuration.location.LocationConfig;

import lombok.Getter;
import lombok.Setter;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** One map definition, one match owner, and a main arena with translated physical copies. */
@Getter
@Setter
public class HotyCodyDuskyConfig extends BaseGameConfig {
    private final String resourceName = "hotycodydusky/area.yml";
    private final String folderName = "hotycodydusky/";

    public HotyCodyDuskyConfig(ChampionshipsCore plugin, String name) {
        super(plugin, name);
    }

    @Override
    public int getLatestVersion() {
        return 4;
    }

    @ConfigOption(path = "name")
    private String areaName;

    @ConfigOption(path = "timer")
    private int timer = 240;

    @ConfigOption(path = "area-pos1", nullable = true)
    private Vector areaPos1;

    @ConfigOption(path = "area-pos2", nullable = true)
    private Vector areaPos2;

    @ConfigOption(path = "spectator-spawn-point", nullable = true)
    private Location spectatorSpawnPoint;

    @ConfigOption(path = "copy-spawn", nullable = true)
    private Location copySpawn;

    @ConfigOption(path = "copies")
    private int copies;

    @ConfigOption(path = "copy-size", nullable = true)
    private Vector copySize;

    @ConfigOption(path = "copy-layout", nullable = true)
    private ConfigurationSection copyLayout;

    @ConfigOption(path = "spawn-points")
    private List<String> spawnPoints = new ArrayList<>();

    @ConfigOption(path = "copy-bounds")
    private List<Map<String, Object>> copyBounds = new ArrayList<>();

    public ArenaGrid getCopyGrid() {
        if (copyLayout != null && copyLayout.isList("origins")) {
            var entries = copyLayout.getList("origins");
            return new ConfiguredArenaGrid(
                    entries.stream().map(ArenaLayoutPlanner::readVector).toList());
        }
        Vector origin = copyLayout == null ? null : copyLayout.getVector("origin");
        Vector step = copyLayout == null ? null : copyLayout.getVector("step");
        if (origin == null)
            origin =
                    areaPos1 == null || areaPos2 == null
                            ? new Vector()
                            : Vector.getMinimum(areaPos1, areaPos2);
        if (step == null)
            step = copySize == null ? new Vector(192, 0, 0) : ArenaLayoutPlanner.rowStep(copySize);
        return new RowArenaGrid(origin, step);
    }

    public ArenaGrid prepareCopyGrid(Vector size) {
        copySize = size.clone();
        Vector origin =
                areaPos1 == null || areaPos2 == null
                        ? new Vector()
                        : Vector.getMinimum(areaPos1, areaPos2);
        configuration.set("copy-layout", null);
        copyLayout = configuration.createSection("copy-layout");
        copyLayout.set("origin", origin);
        copyLayout.set("step", ArenaLayoutPlanner.rowStep(size));
        copyBounds = new ArrayList<>();
        spawnPoints = new ArrayList<>();
        return getCopyGrid();
    }

    public List<BoundingBox> getCopyBoxes() {
        if (copies < 1 || copySize == null) return List.of();
        if (!copyBounds.isEmpty()) {
            if (copyBounds.size() != copies)
                throw new IllegalArgumentException("子场地边界数量与 copies 不一致");
            return copyBounds.stream()
                    .map(
                            row -> {
                                Vector first = ArenaLayoutPlanner.readVector(row.get("pos1")),
                                        second = ArenaLayoutPlanner.readVector(row.get("pos2"));
                                Vector min = Vector.getMinimum(first, second),
                                        max =
                                                Vector.getMaximum(first, second)
                                                        .add(new Vector(1, 1, 1));
                                return new BoundingBox(
                                        min.getX(),
                                        min.getY(),
                                        min.getZ(),
                                        max.getX(),
                                        max.getY(),
                                        max.getZ());
                            })
                    .toList();
        }
        return ArenaPreparer.copyBoxes(getCopyGrid(), copies, copySize);
    }

    public Location getPlayerSpawnPoint() {
        return copies < 1 ? null : spawn(0);
    }

    public Location spawn(int arena) {
        if (arena < 0 || arena >= copies) throw new IllegalArgumentException("子场地编号超出范围");
        if (!spawnPoints.isEmpty()) {
            if (spawnPoints.size() != copies)
                throw new IllegalArgumentException("出生点数量与 copies 不一致");
            return LocationConfig.readLocation(spawnPoints.get(arena));
        }
        return copySpawn == null ? null : copySpawn.clone().add(getCopyGrid().delta(arena));
    }

    public void validateLayout() {
        if (copies < 1
                || copies > 64
                || copySize == null
                || copySize.getX() < 1
                || copySize.getY() < 1
                || copySize.getZ() < 1
                || !Double.isFinite(copySize.getX())
                || !Double.isFinite(copySize.getY())
                || !Double.isFinite(copySize.getZ())) {
            throw new IllegalArgumentException("场地副本数量须为 1–64，尺寸须为有限正数");
        }
        if (copyLayout == null) throw new IllegalArgumentException("未记录副本布局");
        if (copyLayout.isList("origins") && copyLayout.getList("origins").size() != copies)
            throw new IllegalArgumentException("副本起点数量与 copies 不一致");
        if (!spawnPoints.isEmpty() && spawnPoints.size() != copies)
            throw new IllegalArgumentException("出生点数量与 copies 不一致");
        if (copySpawn == null && spawnPoints.isEmpty())
            throw new IllegalArgumentException("未记录主场地出生点");
        var boxes = getCopyBoxes();
        for (int i = 0; i < boxes.size(); i++) {
            var box = boxes.get(i);
            for (int j = 0; j < i; j++)
                if (box.overlaps(boxes.get(j))) throw new IllegalArgumentException("副本边界不能重叠");
            if (!spawnPoints.isEmpty()) {
                var point =
                        ink.ziip.championshipscore.configuration.location.ConfiguredLocation.read(
                                        spawnPoints.get(i))
                                .requireWorld(getConfiguredWorld());
                if (!box.contains(new Vector(point.x(), point.y(), point.z())))
                    throw new IllegalArgumentException("出生点不在对应子场地内：" + (i + 1));
            } else if (!box.contains(copySpawn.clone().add(getCopyGrid().delta(i)).toVector()))
                throw new IllegalArgumentException("主场地出生点不在模板内部");
        }
    }

    @Override
    public boolean isPrepareReady() {
        try {
            validateLayout();
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        return super.isPrepareReady()
                && isPrepareWorldBuilt()
                && !getConfiguredWorld().isBlank()
                && copies > 0
                && copySize != null
                && areaPos1 != null
                && areaPos2 != null
                && (copySpawn != null || spawnPoints.size() == copies)
                && spectatorSpawnPoint != null;
    }
}
