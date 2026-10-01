package ink.ziip.championshipscore.api.game.riptiderush;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.configuration.ConfigOption;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
public class RiptideRushConfig extends BaseGameConfig {
    public static final int MAX_POOL_SIZE = 512;
    private final String resourceName = "riptiderush/area.yml";
    private final String folderName = "riptiderush/";

    public RiptideRushConfig(ChampionshipsCore plugin, String areaName) {
        super(plugin, areaName);
    }

    @Override public int getLatestVersion() { return 28; }

    @ConfigOption(path = "name")
    private String areaName;
    @ConfigOption(path = "world-name")
    private String worldName;
    @ConfigOption(path = "timer")
    private int timer;
    @ConfigOption(path = "start-point", nullable = true)
    private Location startPoint;
    @ConfigOption(path = "finish-point", nullable = true)
    private Location finishPoint;

    @ConfigOption(path = "course.wall-groups")
    private boolean wallGroups = true;
    @ConfigOption(path = "course.counts.pass")
    private int passCount = 12;
    @ConfigOption(path = "course.counts.math")
    private int mathCount = 5;
    @ConfigOption(path = "course.counts.stopped")
    private int stoppedCount = 8;
    @ConfigOption(path = "course.counts.rhythm")
    private int rhythmCount = 5;

    @ConfigOption(path = "course.stopped-allocation.mode")
    private String stoppedAllocationMode = "WEIGHTED";
    @ConfigOption(path = "course.stopped-allocation.weights.color-floor")
    private int colorFloorWeight = 3;
    @ConfigOption(path = "course.stopped-allocation.weights.dodge")
    private int dodgeWeight = 3;
    @ConfigOption(path = "course.stopped-allocation.weights.side-sweep")
    private int sideSweepWeight = 2;

    public RiptideStoppedAllocation stoppedAllocation(long seed) {
        return RiptideStoppedAllocation.allocate(stoppedCount, stoppedAllocationMode,
                colorFloorWeight, dodgeWeight, sideSweepWeight, seed);
    }

    public RiptideStoppedAllocation previewStoppedAllocation() {
        return stoppedAllocation(previewSeed);
    }

    public int stoppedChildWeight(RiptideLevelType type) {
        return switch (type) {
            case COLOR_FLOOR -> colorFloorWeight;
            case DODGE -> dodgeWeight;
            default -> throw new IllegalArgumentException("不是停船挑战子类");
        };
    }

    public void setStoppedChildWeight(RiptideLevelType type, int weight) {
        if (weight < 0 || weight > 100) throw new IllegalArgumentException("子类权重须为0–100");
        switch (type) {
            case COLOR_FLOOR -> {
                RiptideStoppedAllocation.allocate(stoppedCount, stoppedAllocationMode, weight, dodgeWeight, sideSweepWeight, previewSeed);
                colorFloorWeight = weight;
            }
            case DODGE -> {
                RiptideStoppedAllocation.allocate(stoppedCount, stoppedAllocationMode, colorFloorWeight, weight, sideSweepWeight, previewSeed);
                dodgeWeight = weight;
            }
            default -> throw new IllegalArgumentException("不是停船挑战子类");
        }
    }

    public void setSideSweepWeight(int weight) {
        if (weight < 0 || weight > 100) throw new IllegalArgumentException("子类权重须为0–100");
        RiptideStoppedAllocation.allocate(stoppedCount, stoppedAllocationMode, colorFloorWeight, dodgeWeight, weight, previewSeed);
        sideSweepWeight = weight;
    }

    public void setStoppedAllocationMode(String mode) {
        RiptideStoppedAllocation.allocate(stoppedCount, mode, colorFloorWeight, dodgeWeight, sideSweepWeight, previewSeed);
        stoppedAllocationMode = mode.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public void setStoppedCount(int stoppedCount) {
        RiptideStoppedAllocation.allocate(stoppedCount, stoppedAllocationMode, colorFloorWeight, dodgeWeight, sideSweepWeight, previewSeed);
        this.stoppedCount = stoppedCount;
    }
    @ConfigOption(path = "course.preview-seed")
    private long previewSeed = 1;
    @ConfigOption(path = "course.fixed-seed")
    private String fixedSeed = "";
    @ConfigOption(path = "course.pool")
    @Getter(lombok.AccessLevel.NONE)
    @Setter(lombok.AccessLevel.NONE)
    private List<Map<String, Object>> pool = defaultPool();
    @Getter(lombok.AccessLevel.NONE)
    @Setter(lombok.AccessLevel.NONE)
    private List<Map<String, Object>> resolvedPoolSource;
    @Getter(lombok.AccessLevel.NONE)
    @Setter(lombok.AccessLevel.NONE)
    private List<RiptideLevelTemplate> resolvedPool;

    public static List<Map<String, Object>> defaultPool() {
        var rows = new java.util.ArrayList<Map<String, Object>>();
        for (var type : RiptideLevelType.values()) {
            for (var variant : RiptideLevelTemplate.variants(type)) {
                if (variant.equals("AUTO")) continue;
                rows.add(new RiptideLevelTemplate(type.name().toLowerCase(java.util.Locale.ROOT) + "_" + variant.toLowerCase(java.util.Locale.ROOT),
                        RiptideLevelTemplate.variantName(variant), type, variant, true, 10, 64, 1).serialize());
            }
        }
        rows.addAll(RiptideHitwCatalog.templates().stream().map(RiptideLevelTemplate::serialize).toList());
        return List.copyOf(rows);
    }

    /** Automatic layout is serialized too, so world rebinding and publication use the same coordinates. */
    public static void automaticLayout(YamlConfiguration yaml) {
        yaml.set("timer", 330);
        String world = yaml.getString("world-name", "");
        for (String key : List.of("start-point", "finish-point")) {
            yaml.set(key, null);
            if (world.isBlank()) continue;
            yaml.set(key + ".world", world);
            yaml.set(key + ".x", .5D); yaml.set(key + ".y", 80D);
            yaml.set(key + ".z", key.equals("start-point") ? -110.5D : 389.5D);
            yaml.set(key + ".yaw", 0F); yaml.set(key + ".pitch", 0F);
        }
    }

    @Override public void loadFromConfiguration(@NotNull YamlConfiguration yaml) {
        automaticLayout(yaml);
        super.loadFromConfiguration(yaml);
        resolvePool();
    }

    @Override public void bindConfiguredWorld(@NotNull String worldName) {
        super.bindConfiguredWorld(worldName);
        automaticLayout(configuration);
        var world = worldName.isBlank() ? null : plugin.getServer().getWorld(worldName);
        startPoint = worldName.isBlank() ? null : new Location(world, .5, 80, -110.5, 0, 0);
        finishPoint = worldName.isBlank() ? null : new Location(world, .5, 80, 389.5, 0, 0);
        timer = 330;
    }

    /** Checked persistence: do not acknowledge a saved building if the map file could not be written. */
    public void saveBuilding(RiptideLevelTemplate replacement) throws java.io.IOException {
        var oldPool = pool; var oldDirty = prepareDirty;
        var rows = new java.util.ArrayList<>(resolvePool());
        int index = -1;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).id().equals(replacement.id())) index = i;
        if (index < 0) throw new IllegalArgumentException("该变体已不存在");
        rows.set(index, replacement);
        if (configuration == null || configurationPath == null) throw new java.io.IOException("地图配置尚未加载");
        setTemplates(rows); prepareDirty = true;
        configuration.set("course.pool", pool); configuration.set("prepare.dirty", true);
        java.nio.file.Path pending = null;
        try {
            pending = java.nio.file.Files.createTempFile(configurationPath.getParent(), "riptide-building-", ".tmp");
            configuration.save(pending.toFile());
            try { java.nio.file.Files.move(pending, configurationPath, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                java.nio.file.Files.move(pending, configurationPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (java.io.IOException error) {
            pool = oldPool; prepareDirty = oldDirty;
            configuration.set("course.pool", oldPool); configuration.set("prepare.dirty", oldDirty);
            throw error;
        } finally {
            if (pending != null) java.nio.file.Files.deleteIfExists(pending);
        }
    }


    public List<RiptideLevelTemplate> resolvePool() {
        // YAML loading and rollback replace the raw field through reflection. Identity, rather
        // than a dirty flag, also catches those replacements. Owned rows are deeply immutable.
        if (pool == resolvedPoolSource && resolvedPool != null) return resolvedPool;
        if (pool == null || pool.size() > MAX_POOL_SIZE)
            throw new IllegalArgumentException("关卡池最多512项");
        List<RiptideLevelTemplate> resolved = pool.stream().map(RiptideLevelTemplate::parse).toList();
        setTemplates(resolved);
        return resolvedPool;
    }

    public List<Map<String, Object>> getPool() {
        resolvePool();
        return pool;
    }

    public void setPool(List<Map<String, Object>> rows) {
        if (rows == null || rows.size() > MAX_POOL_SIZE)
            throw new IllegalArgumentException("关卡池最多512项");
        setTemplates(rows.stream().map(RiptideLevelTemplate::parse).toList());
    }

    public void setTemplates(List<RiptideLevelTemplate> templates) {
        var resolved = List.copyOf(templates);
        if (resolved.size() > MAX_POOL_SIZE) throw new IllegalArgumentException("关卡池最多512项");
        var ids = new HashSet<String>();
        for (var template : resolved)
            if (!ids.add(template.id())) throw new IllegalArgumentException("关卡ID重复：" + template.id());
        // serialize() owns the row, while blueprint.serialize() and floor are already immutable.
        pool = resolved.stream().map(t -> java.util.Collections.unmodifiableMap(t.serialize())).toList();
        resolvedPoolSource = pool;
        resolvedPool = resolved;
    }

    public long nextCourseSeed() {
        return fixedSeed == null || fixedSeed.isBlank() ? java.util.concurrent.ThreadLocalRandom.current().nextLong()
                : Long.parseLong(fixedSeed.trim());
    }













    @ConfigOption(path = "generation.raft-width")
    private int raftWidth;
    @ConfigOption(path = "generation.raft-length")
    private int raftLength;
    @ConfigOption(path = "generation.obstacle-margin")
    private int obstacleMargin;
    @ConfigOption(path = "generation.clear-height")
    private int clearHeight;
    @ConfigOption(path = "generation.minimum-level-spacing")
    private int minimumLevelSpacing;
    @ConfigOption(path = "generation.raft-material")
    private String raftMaterial;
    @ConfigOption(path = "generation.obstacle-material")
    private String obstacleMaterial;

    @ConfigOption(path = "movement.base-speed")
    private double baseSpeed;
    @ConfigOption(path = "movement.final-speed")
    private double finalSpeed;

    public boolean hasValidMovementSpeeds() {
        // Leave headroom below ordinary ground sprinting (~5.6 blocks/s), even without Speed I.
        return Double.isFinite(baseSpeed) && Double.isFinite(finalSpeed)
                && baseSpeed >= .25D && finalSpeed >= baseSpeed && finalSpeed <= 5.5D;
    }

    public double speedAtProgress(double progress) {
        int stage = progress >= .8D ? 4 : progress >= .6D ? 3 : progress >= .4D ? 2 : progress >= .2D ? 1 : 0;
        return baseSpeed + (finalSpeed - baseSpeed) * stage / 4D;
    }

    @ConfigOption(path = "movement.horizontal-padding")
    private double horizontalPadding;
    @ConfigOption(path = "movement.fall-distance")
    private double fallDistance;
    @ConfigOption(path = "movement.trail-material")
    private String trailMaterial;

    @ConfigOption(path = "math.preview-blocks")
    private int mathPreviewBlocks;
    // Addition/subtraction range; multiplication independently uses 10..99 and 2..9 in either order.
    @ConfigOption(path = "math.minimum-operand")
    private int minimumOperand;
    @ConfigOption(path = "math.maximum-operand")
    private int maximumOperand;

    public RiptideCourseGeometry resolveGeometry() {
        if (startPoint == null || finishPoint == null)
            throw new IllegalArgumentException("start and finish are required");
        // Draft configs are read before their world is loaded. Resolve against the current
        // server world here, also replacing references to a world that has since been reloaded.
        if (worldName != null && !worldName.isBlank()) {
            var world = plugin.getServer().getWorld(worldName);
            if (world == null) throw new IllegalArgumentException("地图世界尚未加载：" + worldName);
            startPoint.setWorld(world);
            finishPoint.setWorld(world);
        }
        return RiptideCourseGeometry.resolve(startPoint.clone(), finishPoint.clone(), raftWidth, raftLength);
    }

    @Override
    public @Nullable Vector getAreaPos1() {
        try {
            return resolveGeometry().areaMinimum(Math.max(2, obstacleMargin), 12);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public @Nullable Vector getAreaPos2() {
        try {
            return resolveGeometry().areaMaximum(Math.max(2, obstacleMargin), Math.max(12, clearHeight + 3));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public @Nullable Location getSpectatorSpawnPoint() {
        try {
            RiptideCourseGeometry geometry = resolveGeometry();
            Location location = geometry.centerAt(geometry.totalSteps() / 2).add(0D, 10D, 0D);
            float yaw = geometry.stepX() > 0 ? -90F : geometry.stepX() < 0 ? 90F
                    : geometry.stepZ() > 0 ? 0F : 180F;
            location.setYaw(yaw);
            location.setPitch(35F);
            return location;
        } catch (RuntimeException ignored) {
            return startPoint == null ? null : startPoint.clone().add(0D, 8D, 0D);
        }
    }
}
