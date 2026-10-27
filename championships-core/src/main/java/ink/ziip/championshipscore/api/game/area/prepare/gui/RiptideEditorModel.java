package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.game.riptiderush.*;
import ink.ziip.championshipscore.api.game.riptiderush.config.RiptideRushConfig;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelTemplate;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelType;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Category-scoped edits preserve the other categories, all saved IDs and the planner's existing
 * contract.
 */
public final class RiptideEditorModel {
    private RiptideEditorModel() {}

    public static List<RiptideLevelTemplate> rows(RiptideRushConfig config, RiptideLevelType type) {
        return config.resolvePool().stream().filter(t -> t.type() == type).toList();
    }

    /** Immutable values shown or consumed by the editor; no second mutable draft is created. */
    static List<?> snapshot(RiptideRushConfig c) {
        return java.util.Collections.unmodifiableList(
                java.util.Arrays.asList(
                        List.copyOf(c.resolvePool()),
                        c.getPreviewSeed(),
                        c.getFixedSeed(),
                        c.getPassCount(),
                        c.getMathCount(),
                        c.getStoppedCount(),
                        c.getRhythmCount(),
                        c.getStoppedAllocationMode(),
                        c.getColorFloorWeight(),
                        c.getDodgeWeight(),
                        c.getSideSweepWeight(),
                        c.isWallGroups(),
                        c.getMinimumOperand(),
                        c.getMaximumOperand(),
                        c.getMathPreviewBlocks(),
                        c.getWorldName(),
                        c.getTimer(),
                        c.getStartPoint() == null ? null : c.getStartPoint().clone(),
                        c.getFinishPoint() == null ? null : c.getFinishPoint().clone(),
                        c.getRaftWidth(),
                        c.getRaftLength(),
                        c.getObstacleMargin(),
                        c.getClearHeight(),
                        c.getMinimumLevelSpacing(),
                        c.getRaftMaterial(),
                        c.getObstacleMaterial(),
                        c.getBaseSpeed(),
                        c.getFinalSpeed(),
                        c.getHorizontalPadding(),
                        c.getFallDistance(),
                        c.getTrailMaterial()));
    }

    public static RiptideLevelTemplate find(
            RiptideRushConfig config, RiptideLevelType type, String id) {
        return rows(config, type).stream()
                .filter(t -> t.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        MessageConfig.codeText("riptide-editor-model.find-1")));
    }

    public static void update(
            RiptideRushConfig config,
            RiptideLevelType type,
            String id,
            String field,
            Object value) {
        if (!List.of("name", "variant", "enabled", "weight", "max-uses", "difficulty")
                        .contains(field)
                || type == RiptideLevelType.PASS && field.equals("max-uses"))
            throw new IllegalArgumentException(
                    MessageConfig.codeText("riptide-editor-model.update-1"));
        var original = find(config, type, id);
        var row = original.serialize();
        row.put(field, value);
        var replacement = RiptideLevelTemplate.parse(row);
        var all = new ArrayList<>(config.resolvePool());
        all.set(all.indexOf(original), replacement);
        config.setTemplates(all);
    }

    public static RiptideLevelTemplate add(
            RiptideRushConfig config, RiptideLevelType type, String variant) {
        var template =
                new RiptideLevelTemplate(
                        newId(),
                        RiptideLevelTemplate.variantName(variant),
                        type,
                        variant,
                        true,
                        10,
                        64,
                        1);
        append(config, template);
        return template;
    }

    public static RiptideLevelTemplate addBlank(RiptideRushConfig config, RiptideLevelType type) {
        String recipe =
                switch (type) {
                    case PASS -> "CUSTOM";
                    case MATH -> "ADD";
                    case COLOR_FLOOR -> "COPPER";
                    case DODGE -> "ZOMBIE";
                    case RHYTHM -> "SHUTTER";
                };
        var template =
                new RiptideLevelTemplate(
                        newId(),
                        MessageConfig.codeText(
                                "riptide-editor-model.add-blank-1", type.displayName()),
                        type,
                        recipe,
                        false,
                        10,
                        64,
                        1);
        append(config, template);
        return template;
    }

    public static RiptideLevelTemplate duplicate(
            RiptideRushConfig config, RiptideLevelType type, String id) {
        var t = find(config, type, id);
        var copy =
                new RiptideLevelTemplate(
                        newId(),
                        MessageConfig.codeText(
                                "riptide-editor-model.duplicate-1",
                                t.name().substring(0, Math.min(30, t.name().length()))),
                        type,
                        t.variant(),
                        t.enabled(),
                        t.weight(),
                        t.maxUses(),
                        t.difficulty(),
                        t.blueprint());
        append(config, copy);
        return copy;
    }

    private static void append(RiptideRushConfig config, RiptideLevelTemplate template) {
        var rows = new ArrayList<>(config.resolvePool());
        if (rows.size() >= RiptideRushConfig.MAX_POOL_SIZE)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("riptide-editor-model.append-1"));
        rows.add(template);
        config.setTemplates(rows);
    }

    public static void remove(RiptideRushConfig config, RiptideLevelType type, String id) {
        var target = find(config, type, id);
        var all = new ArrayList<>(config.resolvePool());
        all.remove(target);
        config.setTemplates(all);
    }

    public static void quota(RiptideRushConfig config, RiptideLevelType type, int value) {
        if (type == RiptideLevelType.COLOR_FLOOR || type == RiptideLevelType.DODGE)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("riptide-editor-model.quota-1"));
        if (value < (type == RiptideLevelType.PASS ? 2 : 0) || value > 64)
            throw new IllegalArgumentException(
                    type == RiptideLevelType.PASS
                            ? MessageConfig.codeText("riptide-editor-model.quota-3")
                            : MessageConfig.codeText("riptide-editor-model.quota-2"));
        switch (type) {
            case PASS -> config.setPassCount(value);
            case MATH -> config.setMathCount(value);
            case RHYTHM -> config.setRhythmCount(value);
            default ->
                    throw new IllegalArgumentException(
                            MessageConfig.codeText("riptide-editor-model.quota-4"));
        }
    }

    private static String newId() {
        return "level_" + UUID.randomUUID().toString().replace("-", "");
    }
}
