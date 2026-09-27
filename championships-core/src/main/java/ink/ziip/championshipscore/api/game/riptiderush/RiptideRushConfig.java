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

    @Override public int getLatestVersion() { return 26; }

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
    @ConfigOption(path = "levels")
    private List<String> levels;

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
    private List<Map<String, Object>> pool = defaultPool();

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
        yaml.set("timer", 300);
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
        normalizeCoursePoints();
    }

    @Override public void bindConfiguredWorld(@NotNull String worldName) {
        super.bindConfiguredWorld(worldName);
        automaticLayout(configuration);
        var world = worldName.isBlank() ? null : plugin.getServer().getWorld(worldName);
        startPoint = worldName.isBlank() ? null : new Location(world, .5, 80, -110.5, 0, 0);
        finishPoint = worldName.isBlank() ? null : new Location(world, .5, 80, 389.5, 0, 0);
        timer = 300;
        normalizeCoursePoints();
    }

    /** Keep every consumer on the same block-aligned course height, including legacy decimal Y values. */
    private void normalizeCoursePoints() {
        if (startPoint == null || finishPoint == null) return;
        double y = startPoint.getBlockY();
        startPoint.setY(y);
        finishPoint.setY(y);
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

    /** v4 replaces mixed AUTO rows with actual editable variants, retaining custom entry settings. */
    public static void migrateBuildings(YamlConfiguration yaml) {
        automaticLayout(yaml);
        var rows = new java.util.ArrayList<Map<String, Object>>();
        var ids = new java.util.HashSet<String>();
        for (var row : yaml.getMapList("course.pool")) ids.add(String.valueOf(row.get("id")));
        for (var row : yaml.getMapList("course.pool")) {
            var t = RiptideLevelTemplate.parse(row);
            if (!t.variant().equals("AUTO")) { rows.add(t.serialize()); continue; }
            for (var variant : RiptideLevelTemplate.variants(t.type())) {
                if (variant.equals("AUTO")) continue;
                String base = t.id().substring(0, Math.min(26, t.id().length())) + "_" + variant.toLowerCase(java.util.Locale.ROOT);
                String id = base; int suffix = 1;
                while (!ids.add(id)) id = base + "_" + suffix++;
                String name = t.name().equals(t.type().displayName()) ? RiptideLevelTemplate.variantName(variant)
                        : t.name().substring(0, Math.min(20, t.name().length())) + " • " + RiptideLevelTemplate.variantName(variant);
                rows.add(new RiptideLevelTemplate(id, name, t.type(), variant, t.enabled(), t.weight(), t.maxUses(), t.difficulty(), t.blueprint()).serialize());
            }
        }
        if (rows.size() > MAX_POOL_SIZE) throw new IllegalArgumentException("展开具体变体后超过512项，请先减少旧随机条目");
        yaml.set("course.pool", rows);
    }

    public List<RiptideLevelTemplate> resolvePool() {
        if (pool == null || pool.size() > MAX_POOL_SIZE)
            throw new IllegalArgumentException("关卡池最多512项");
        List<RiptideLevelTemplate> resolved = pool.stream().map(RiptideLevelTemplate::parse).toList();
        var ids = new HashSet<String>();
        for (var template : resolved)
            if (!ids.add(template.id())) throw new IllegalArgumentException("关卡ID重复：" + template.id());
        return resolved;
    }

    public void setTemplates(List<RiptideLevelTemplate> templates) {
        pool = templates.stream().map(RiptideLevelTemplate::serialize).toList();
    }

    public long nextCourseSeed() {
        return fixedSeed == null || fixedSeed.isBlank() ? java.util.concurrent.ThreadLocalRandom.current().nextLong()
                : Long.parseLong(fixedSeed.trim());
    }

    @Override
    protected void customizeMigratedConfiguration(@NotNull YamlConfiguration old,
                                                  @NotNull YamlConfiguration migrated) {
        migrateCourse(old, migrated);
        migrateNewVariants(migrated);
        migrateBuildings(migrated);
        migrateWorkshop(migrated);
        migrated.set("course.hitw-catalog-version", old.getInt("course.hitw-catalog-version", 0));
        RiptideHitwCatalog.migrate(migrated);
        migratePassRules(migrated);
        migratePassDifficultyRules(migrated);
        migrateFinalStageRules(migrated);
        migrateChallenges(old, migrated);
        migrateChallengeVariety(migrated);
        migrateWindowRules(migrated);
        migrateRevealRules(migrated);
        migrateEarlierGroups(migrated);
        migrated.set("rules", migrated.getList("rules", List.of()).stream().map(section -> section instanceof List<?> lines
                ? lines.stream().map(line -> line.toString().replace("三次25秒/8种", "三次27秒/8种，四次25秒/8种")).toList() : section).toList());
        migrated.set("generation.pause-seconds", null);
        migrated.set("rules", migrated.getList("rules", List.of()).stream().map(section -> section instanceof List<?> lines
                ? lines.stream().map(line -> line.toString().replace("踩色初始30秒/5种方块，一次加速6种，二次27秒/7种，三次27秒/8种，四次25秒/8种",
                        "踩色五段依次32/28/24/20/16秒，方块种类依次5/6/7/8/8种")).toList() : section).toList());
    }

    static void migrateRevealRules(YamlConfiguration yaml) {
        var rules = new java.util.ArrayList<Object>(yaml.getList("rules", List.of()));
        rules.replaceAll(section -> section instanceof List<?> lines ? lines.stream().map(line -> line.toString()
                .replace("初始为完整去皮云杉木墙", "初始为统一尺寸的完整去皮云杉木墙")
                .replace("靠近至12格时显现原貌", "靠近至10格时显现原貌")).toList() : section);
        String rule = "&#ededed所有关卡初始为统一尺寸的完整去皮云杉木墙，船头靠近至10格时显现原貌；侧墙关卡的占位木墙同时撤去，移动侧墙保持原貌。";
        if (rules.stream().noneMatch(section -> section instanceof List<?> lines && lines.contains(rule)))
            rules.add(List.of(rule));
        yaml.set("rules", rules);
    }

    static void migrateEarlierGroups(YamlConfiguration yaml) {
        var rules = new java.util.ArrayList<Object>(yaml.getList("rules", List.of()));
        rules.replaceAll(section -> section instanceof List<?> lines ? lines.stream().map(line -> line.toString()
                .replace("穿越在第二次加速后开放双连续，第三次加速后开放三连续", "穿越、解题和节奏在第一次加速后开放双连续，第二次加速后开放三连续")
                .replace("一次加速加入减法，二次开放双连续，三次加入乘法", "一次加速加入减法与双连续，二次开放三连续，三次加入乘法")
                .replace("连续解题门需依次答对两题", "连续解题门需依次答对每道题")
                .replace("连续数学门需依次答对两题", "连续解题门需依次答对每道题")).toList() : section);
        String rule = "&#ededed节奏墙闭合时会将墙内玩家挤出，请把握开放时机。";
        if (rules.stream().noneMatch(section -> section instanceof List<?> lines && lines.contains(rule))) rules.add(List.of(rule));
        yaml.set("rules", rules);
    }

    static void migrateChallenges(YamlConfiguration old, YamlConfiguration migrated) {
        var rows = new java.util.ArrayList<Map<?, ?>>(migrated.getMapList("course.pool"));
        var ids = rows.stream().map(row -> String.valueOf(row.get("id"))).collect(java.util.stream.Collectors.toSet());
        for (var row : defaultPool()) {
            String variant = String.valueOf(row.get("variant"));
            if ((variant.startsWith("OBSERVE_") || "RHYTHM".equals(row.get("type")))
                    && ids.add(String.valueOf(row.get("id")))) rows.add(row);
        }
        if (rows.size() > MAX_POOL_SIZE) throw new IllegalArgumentException("新增解题与节奏变体后超过512项，请先减少关卡池");
        rows.replaceAll(row -> {
            if (!"连续两道数学门".equals(row.get("name"))) return row;
            var renamed = new java.util.LinkedHashMap<Object, Object>(row);
            renamed.put("name", "连续两道解题门");
            return renamed;
        });
        migrated.set("course.pool", rows);
        // Preserve old quotas; operators can introduce rhythm by replacing existing special slots.
        if (!old.contains("course.counts.rhythm")) migrated.set("course.counts.rhythm", 0);
        migrated.set("rules", migrated.getList("rules", List.of()).stream().map(section -> section instanceof List<?> lines
                ? lines.stream().map(line -> line.toString().replace("数学", "解题")).toList() : section).toList());
        var rules = new java.util.ArrayList<Object>(migrated.getList("rules", List.of()));
        rules.replaceAll(section -> section instanceof List<?> lines ? lines.stream().map(line -> line.toString()
                .replace("线索与答案同时显示", "上行显示线索，下行显示问题和左右答案")
                .replace("节奏闸门：停船后先到闸门后方，听预告，趁开放向前通过；限时结束仍未通过则出局。",
                        "节奏闸门：木筏持续前进，按开合节拍穿过；包含全门、左右交替、长短双拍、中间两侧，离筏或掉落出局。"))
                .toList() : section);
        if (!rules.toString().contains("观察题")) rules.add(List.of("&#ededed解题包含算术和观察题：数颜色、从左到右找第几项；上行显示线索，下行显示问题和左右答案。"));
        if (!rules.toString().contains("节奏闸门")) rules.add(List.of("&#ededed节奏闸门：木筏持续前进，按开合节拍穿过；包含全门、左右交替、长短双拍、中间两侧，离筏或掉落出局。"));
        migrated.set("rules", rules);
    }

    static void migrateWindowRules(YamlConfiguration yaml) {
        String rule = "&#ededed窗口节奏门：横移矮窗左右移动；升降窗口与左右高低交替窗须平走或跳上一格；定点窗口按节拍开关，观察两格高洞口及时穿过。";
        var rules = new java.util.ArrayList<Object>(yaml.getList("rules", List.of()));
        if (rules.stream().noneMatch(section -> section instanceof List<?> lines && lines.contains(rule)))
            rules.add(List.of(rule));
        yaml.set("rules", rules);
    }

    static void migrateChallengeVariety(YamlConfiguration yaml) {
        var rules = new java.util.ArrayList<Object>(yaml.getList("rules", List.of()));
        rules.replaceAll(section -> section instanceof List<?> lines ? lines.stream().map(line -> line.toString()
                .replace("数颜色、从左到右找第几项", "数颜色、左右找第几项、找最大最小、数奇偶、找唯一项")
                .replace("包含全门、左右交替、长短双拍、中间两侧，", "包含全门、左右交替、长短双拍、中间两侧、横移窗口、收放、交错双拍，"))
                .toList() : section);
        if (!rules.toString().contains("观察题随五段航程")) rules.add(List.of(
                "&#ededed观察题随五段航程增加线索数量，后段使用相似两位数；找第几项的两个答案均来自数列。",
                "&#ededed穿越墙体整场不重复，连续墙的通行位置错开；侧墙会推动身体碰到的玩家，及时换位穿洞避让。"));
        yaml.set("rules", rules);
    }

    static void migrateNewVariants(YamlConfiguration yaml) {
        var rows = new java.util.ArrayList<Map<?, ?>>(yaml.getMapList("course.pool"));
        rows.removeIf(row -> "COLOR_FLOOR".equals(row.get("type")) && "CONCRETE".equals(row.get("variant")));
        var ids = rows.stream().map(row -> String.valueOf(row.get("id"))).collect(java.util.stream.Collectors.toSet());
        for (var row : defaultPool()) {
            if (List.of("DOUBLE", "ORE", "LOG", "NETHER").contains(row.get("variant")) && ids.add(String.valueOf(row.get("id")))) rows.add(row);
        }
        yaml.set("course.pool", rows);
        var rules = new java.util.ArrayList<Object>(yaml.getList("rules", List.of()));
        if (!rules.toString().contains("连续数学门需依次答对两题")) rules.add(List.of("&#ededed连续数学门需依次答对两题；踩色含铜、木板、陶瓦、石材、矿石、原木和下界方块。"));
        yaml.set("rules", rules);
    }

    static void migratePassDifficultyRules(YamlConfiguration yaml) {
        var rules = new java.util.ArrayList<Object>(yaml.getList("rules", List.of()));
        if (rules.toString().contains("正向穿越难度")) return;
        rules.add(List.of(
                "&#ededed正向穿越难度：首次加速前仅1，首次后仅2，第二次后2或3。",
                "&#ededed侧向门难度：第二次加速后仅1或2，第三次后仅2或3。",
                "&#ededed穿越门和侧向门可能左右镜像，请观察本次洞口位置。"));
        yaml.set("rules", rules);
    }

    static void migrateFinalStageRules(YamlConfiguration yaml) {
        var rules = new java.util.ArrayList<Object>(yaml.getList("rules", List.of()));
        if (rules.toString().contains("第四次加速后，穿越每关固定三扇")) return;
        rules.add(List.of("&#ededed第四次加速后，穿越每关固定三扇；数学门与侧向门统一为三面侧向数学。",
                "&#ededed木筏分为左红右蓝两区（中间一列为红色）；每面侧墙一道题，完全扫过时须在正确答案颜色方块上方。"));
        yaml.set("rules", rules);
    }

    static void migratePassRules(YamlConfiguration yaml) {
        var rules = new java.util.ArrayList<Object>(yaml.getList("rules", List.of()).stream()
                .map(section -> section instanceof List<?> lines ? lines.stream()
                        .map(line -> line.toString().replace("&#ededed侧墙段木筏短暂停稳：左右避墙，第二面需潜行，碰墙即出局。", "&#ededed第二次加速后可出现侧墙：穿越池随机建筑、每面来向独立随机；第三次加速后每组三面，沿用穿越的离筏与掉落判定。").replace("&#ededed第二次加速后可出现侧墙：穿越池随机建筑、每面来向独立随机；第三次加速后每组三面，碰墙即出局。", "&#ededed第二次加速后可出现侧墙：穿越池随机建筑、每面来向独立随机；第三次加速后每组三面，沿用穿越的离筏与掉落判定。")).toList() : section).toList());
        yaml.set("rules",rules);
        if (rules.toString().contains("侧墙")) return;
        rules.add(List.of("&#ededed连续墙组需要连续换位、跳跃或潜行；留意下一面墙。",
                "&#ededed第二次加速后可出现侧墙：穿越池随机建筑、每面来向独立随机；第三次加速后每组三面，沿用穿越的离筏与掉落判定。"));
        yaml.set("rules", rules);
    }

    /** Expand stock workspaces, preserving deliberate custom generation settings and saved buildings. */
    static void migrateWorkshop(YamlConfiguration yaml) {
        if (yaml.getInt("generation.obstacle-margin", 2) == 2) yaml.set("generation.obstacle-margin", 5);
        if (yaml.getInt("generation.clear-height", 6) == 6) yaml.set("generation.clear-height", 12);
    }

    static void migrateCourse(YamlConfiguration old, YamlConfiguration migrated) {
        if (!old.contains("course")) {
            List<RiptideLevelType> previous = old.getStringList("levels").stream().map(RiptideLevelType::parse).toList();
            for (RiptideLevelType type : RiptideLevelType.values()) {
                String key = switch (type) { case PASS -> "pass"; case MATH -> "math"; case COLOR_FLOOR -> "stopped"; case DODGE -> "dodge"; case RHYTHM -> "rhythm"; };
                migrated.set("course.counts." + key, previous.stream().filter(t -> t == type).count());
            }
            migrated.set("course.pool", defaultPool());
            migrated.set("course.preview-seed", 1L);
            migrated.set("course.fixed-seed", "");
        }
        // Kept only for source compatibility with old tools; no longer drives placement or judging.
        migrated.set("levels", List.of());
        List<List<String>> rules = new java.util.ArrayList<>();
        for (Object section : migrated.getList("rules", List.of())) {
            if (section instanceof List<?> lines)
                rules.add(lines.stream().map(Object::toString).map(line -> line.replace("彩色地板", "踩色")).toList());
        }
        if (!rules.isEmpty()) migrated.set("rules", rules);
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
    // Legacy settings retained for old map files; the four acceleration points are fixed.
    @ConfigOption(path = "movement.boost-speed")
    private double boostSpeed;
    @ConfigOption(path = "movement.final-speed")
    private double finalSpeed;
    @ConfigOption(path = "movement.boost-progress")
    private double boostProgress;
    @ConfigOption(path = "movement.final-progress")
    private double finalProgress;

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
        normalizeCoursePoints();
        return RiptideCourseGeometry.resolve(startPoint.clone(), finishPoint.clone(), raftWidth, raftLength);
    }

    public List<RiptideLevelType> resolveLevels() {
        if (levels == null) return List.of();
        return levels.stream().map(RiptideLevelType::parse).toList();
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
