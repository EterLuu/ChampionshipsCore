package ink.ziip.championshipscore.api.game.riptiderush.course;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A map-owned pool entry with a concrete mechanic variant. */
public record RiptideLevelTemplate(
        String id,
        String name,
        RiptideLevelType type,
        String variant,
        boolean enabled,
        int weight,
        int maxUses,
        int difficulty,
        RiptideBlueprint blueprint) {
    public RiptideLevelTemplate(
            String id,
            String name,
            RiptideLevelType type,
            String variant,
            boolean enabled,
            int weight,
            int maxUses,
            int difficulty) {
        this(id, name, type, variant, enabled, weight, maxUses, difficulty, null);
    }

    public RiptideLevelTemplate withBlueprint(RiptideBlueprint value) {
        return new RiptideLevelTemplate(
                id, name, type, variant, enabled, weight, maxUses, difficulty, value);
    }

    public String designKey(String resolvedVariant) {
        return blueprint == null
                ? resolvedVariant
                : type == RiptideLevelType.PASS ? blueprint.schematic() : id;
    }

    public String usageKey() {
        return type == RiptideLevelType.PASS ? "wall:" + designKey(variant) : id;
    }

    public RiptideLevelTemplate {
        if (id == null || !id.matches("[a-z0-9_-]{1,48}"))
            throw new IllegalArgumentException("关卡ID须为1–48位小写字母、数字、下划线或连字符");
        if (name == null
                || name.isBlank()
                || name.length() > 32
                || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("关卡名称须为1–32字且不能含控制字符");
        if (type == null) throw new IllegalArgumentException("缺少关卡类型");
        if (type == RiptideLevelType.RHYTHM && blueprint != null)
            throw new IllegalArgumentException("节奏机关使用动态闸门，不支持静态建筑快照");
        variant = variant == null ? "AUTO" : variant.toUpperCase(Locale.ROOT);
        if (type == RiptideLevelType.PASS && blueprint != null) variant = "CUSTOM";
        if (type == RiptideLevelType.PASS
                && variant.equals("CUSTOM")
                && blueprint == null
                && enabled) throw new IllegalArgumentException("请先编辑并保存建筑，再启用此变体");
        if (!(type == RiptideLevelType.PASS && variant.equals("CUSTOM"))
                && !variants(type).contains(variant))
            throw new IllegalArgumentException(name + "：无效变体 " + variant);
        if (type == RiptideLevelType.PASS) maxUses = 1;
        if (weight < 1
                || weight > 100
                || maxUses < 1
                || maxUses > 64
                || difficulty < 1
                || difficulty > 3)
            throw new IllegalArgumentException(name + "：权重须1–100，次数上限须1–64，难度须1–3");
    }

    public static List<String> variants(RiptideLevelType type) {
        return switch (type) {
            case PASS -> List.of("AUTO", "GAP", "JUMP", "WEAVE");
            case MATH ->
                    List.of(
                            "AUTO",
                            "ADD",
                            "SUBTRACT",
                            "MULTIPLY",
                            "DOUBLE",
                            "OBSERVE_COUNT",
                            "OBSERVE_ORDER",
                            "OBSERVE_EXTREME",
                            "OBSERVE_PARITY",
                            "OBSERVE_UNIQUE");
            case COLOR_FLOOR ->
                    List.of(
                            "AUTO",
                            "COPPER",
                            "WOOD",
                            "TERRACOTTA",
                            "STONE",
                            "ORE",
                            "LOG",
                            "NETHER");
            case DODGE -> List.of("AUTO", "ZOMBIE", "HUSK", "SKELETON", "SPIDER", "CREEPER");
            case RHYTHM ->
                    List.of(
                            "AUTO",
                            "SHUTTER",
                            "ALTERNATING",
                            "DOUBLE_BEAT",
                            "CENTER_SIDES",
                            "SWEEP",
                            "IN_OUT",
                            "CROSS_BEAT",
                            "HORIZONTAL_WINDOW",
                            "VERTICAL_WINDOW",
                            "WINDOW_SHUTTER",
                            "STAGGERED_WINDOWS",
                            "TRIPLE_PULSE",
                            "LEFT_RIGHT_CENTER",
                            "EDGE_SWAP",
                            "PINBALL",
                            "SNAKE",
                            "CENTER_PULSE",
                            "EDGE_PULSE",
                            "FOLD",
                            "SPLIT_MERGE",
                            "REST_ACCENT",
                            "MIRROR_CHASE",
                            "DIAGONAL",
                            "DOUBLE_WINDOW",
                            "BACKBEAT",
                            "QUICK_TURN");
        };
    }

    public static String variantName(String variant) {
        return switch (variant) {
            case "AUTO" -> "随机变体";
            case "CUSTOM" -> "自定义建筑";
            case "GAP" -> "偏侧门洞";
            case "JUMP" -> "单栏跳跃";
            case "WEAVE" -> "左右折返";
            case "ADD" -> "加法";
            case "SUBTRACT" -> "减法";
            case "MULTIPLY" -> "乘法";
            case "DOUBLE" -> "连续两道解题门";
            case "OBSERVE_COUNT" -> "观察：数颜色";
            case "OBSERVE_ORDER" -> "观察：找第几项";
            case "OBSERVE_EXTREME" -> "观察：找最大最小";
            case "OBSERVE_PARITY" -> "观察：数奇偶";
            case "OBSERVE_UNIQUE" -> "观察：找唯一项";
            case "SHUTTER" -> "节拍闸门";
            case "ALTERNATING" -> "左右交替闸门";
            case "DOUBLE_BEAT" -> "长短双拍闸门";
            case "CENTER_SIDES" -> "中间两侧闸门";
            case "SWEEP" -> "横移窗口闸门";
            case "IN_OUT" -> "收放闸门";
            case "CROSS_BEAT" -> "交错双拍闸门";
            case "HORIZONTAL_WINDOW" -> "横移矮窗";
            case "VERTICAL_WINDOW" -> "升降窗口";
            case "WINDOW_SHUTTER" -> "定点开合窗";
            case "STAGGERED_WINDOWS" -> "左右高低交替窗";
            case "TRIPLE_PULSE" -> "三拍脉冲";
            case "LEFT_RIGHT_CENTER" -> "左-右-中接力";
            case "EDGE_SWAP" -> "边缘换气";
            case "PINBALL" -> "弹珠回弹";
            case "SNAKE" -> "蛇形追拍";
            case "CENTER_PULSE" -> "中心脉冲";
            case "EDGE_PULSE" -> "边缘脉冲";
            case "FOLD" -> "折叠开口";
            case "SPLIT_MERGE" -> "分流合流";
            case "REST_ACCENT" -> "反拍留白";
            case "MIRROR_CHASE" -> "镜像追逐";
            case "DIAGONAL" -> "斜线节拍";
            case "DOUBLE_WINDOW" -> "双窗交替";
            case "BACKBEAT" -> "后拍回收";
            case "QUICK_TURN" -> "急转弯拍";
            case "ORE" -> "矿石";
            case "LOG" -> "原木";
            case "NETHER" -> "下界方块";
            case "COPPER" -> "铜块";
            case "WOOD" -> "木板";
            case "TERRACOTTA" -> "陶瓦";
            case "STONE" -> "石材";
            case "ZOMBIE" -> "僵尸冲刺";
            case "HUSK" -> "尸壳冲刺";
            case "SKELETON" -> "骷髅冲刺";
            case "SPIDER" -> "蜘蛛冲刺";
            case "CREEPER" -> "苦力怕冲刺";
            default -> variant;
        };
    }

    public static RiptideLevelTemplate create(String id, RiptideLevelType type) {
        return new RiptideLevelTemplate(id, type.displayName(), type, "AUTO", true, 10, 64, 1);
    }

    public Map<String, Object> serialize() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        row.put("type", type.name());
        row.put("variant", variant);
        row.put("enabled", enabled);
        row.put("weight", weight);
        if (type != RiptideLevelType.PASS) row.put("max-uses", maxUses);
        row.put("difficulty", difficulty);
        if (blueprint != null) row.put("building", blueprint.serialize());
        return row;
    }

    public static RiptideLevelTemplate parse(Map<?, ?> row) {
        return new RiptideLevelTemplate(
                string(row, "id", ""),
                string(row, "name", "未命名"),
                RiptideLevelType.parse(string(row, "type", "")),
                string(row, "variant", "AUTO"),
                Boolean.parseBoolean(string(row, "enabled", "true")),
                integer(row, "weight", 10),
                integer(row, "max-uses", 64),
                integer(row, "difficulty", 1),
                row.get("building") instanceof Map<?, ?> building
                        ? RiptideBlueprint.parse(building)
                        : null);
    }

    private static String string(Map<?, ?> row, String key, String fallback) {
        Object value = row.get(key);
        return value == null ? fallback : value.toString();
    }

    private static int integer(Map<?, ?> row, String key, int fallback) {
        return Integer.parseInt(string(row, key, Integer.toString(fallback)));
    }
}
