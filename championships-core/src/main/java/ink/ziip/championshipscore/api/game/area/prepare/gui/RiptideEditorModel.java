package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.game.riptiderush.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Category-scoped edits preserve the other categories, all saved IDs and the planner's existing contract. */
public final class RiptideEditorModel {
    private RiptideEditorModel() { }
    public static final int CATEGORY_PAGE_SIZE = 36;
    public static List<RiptideLevelTemplate> rows(RiptideRushConfig config, RiptideLevelType type) {
        return config.resolvePool().stream().filter(t -> t.type() == type).toList();
    }
    public static int clampPage(int page, int count, int size) {
        return Math.max(0, Math.min(page, Math.max(0, (count - 1) / size)));
    }
    public static RiptideLevelTemplate find(RiptideRushConfig config, RiptideLevelType type, String id) {
        return rows(config, type).stream().filter(t -> t.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("该变体已不存在，请返回类别列表"));
    }
    public static void update(RiptideRushConfig config, RiptideLevelType type, String id, String field, Object value) {
        if (!List.of("name", "variant", "enabled", "weight", "max-uses", "difficulty").contains(field)
                || type == RiptideLevelType.PASS && field.equals("max-uses"))
            throw new IllegalArgumentException("不可编辑的变体属性");
        var original = find(config, type, id);
        var row = original.serialize(); row.put(field, value);
        var replacement = RiptideLevelTemplate.parse(row);
        var all = new ArrayList<>(config.resolvePool());
        all.set(all.indexOf(original), replacement); config.setTemplates(all);
    }
    public static RiptideLevelTemplate add(RiptideRushConfig config, RiptideLevelType type, String variant) {
        var template = new RiptideLevelTemplate(newId(), RiptideLevelTemplate.variantName(variant), type,
                variant, true, 10, 64, 1);
        append(config, template); return template;
    }
    public static RiptideLevelTemplate addBlank(RiptideRushConfig config, RiptideLevelType type) {
        String recipe = switch (type) { case PASS -> "CUSTOM"; case MATH -> "ADD"; case COLOR_FLOOR -> "COPPER"; case DODGE -> "ZOMBIE"; case RHYTHM -> "SHUTTER"; };
        var template = new RiptideLevelTemplate(newId(), "未命名" + type.displayName() + "变体", type,
                recipe, false, 10, 64, 1);
        append(config, template); return template;
    }
    public static RiptideLevelTemplate duplicate(RiptideRushConfig config, RiptideLevelType type, String id) {
        var t = find(config, type, id);
        var copy = new RiptideLevelTemplate(newId(), t.name().substring(0, Math.min(30, t.name().length())) + "副本",
                type, t.variant(), t.enabled(), t.weight(), t.maxUses(), t.difficulty(), t.blueprint());
        append(config, copy); return copy;
    }
    private static void append(RiptideRushConfig config, RiptideLevelTemplate template) {
        var rows = new ArrayList<>(config.resolvePool());
        if (rows.size() >= RiptideRushConfig.MAX_POOL_SIZE) throw new IllegalArgumentException("关卡池最多512项");
        rows.add(template); config.setTemplates(rows);
    }
    public static void remove(RiptideRushConfig config, RiptideLevelType type, String id) {
        var target = find(config, type, id);
        var all = new ArrayList<>(config.resolvePool()); all.remove(target); config.setTemplates(all);
    }
    public static void quota(RiptideRushConfig config, RiptideLevelType type, int value) {
        if (type == RiptideLevelType.COLOR_FLOOR || type == RiptideLevelType.DODGE)
            throw new IllegalArgumentException("停船挑战子类配额由父类统一分配");
        if (value < (type == RiptideLevelType.PASS ? 2 : 0) || value > 64)
            throw new IllegalArgumentException(type == RiptideLevelType.PASS ? "穿越数量须为2–64" : "关卡数量须为0–64");
        switch (type) {
            case PASS -> config.setPassCount(value);
            case MATH -> config.setMathCount(value);
            case RHYTHM -> config.setRhythmCount(value);
            default -> throw new IllegalArgumentException("不是独立配额类别");
        }
    }
    private static String newId() { return "level_" + UUID.randomUUID().toString().replace("-", ""); }
}
