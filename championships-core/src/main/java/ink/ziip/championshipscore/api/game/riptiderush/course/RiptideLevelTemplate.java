package ink.ziip.championshipscore.api.game.riptiderush.course;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

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
            throw new IllegalArgumentException(
                    MessageConfig.codeText("riptide-level-template.text-1"));
        if (name == null
                || name.isBlank()
                || name.length() > 32
                || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(
                    MessageConfig.codeText("riptide-level-template.text-2"));
        if (type == null)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("riptide-level-template.text-3"));
        if (type == RiptideLevelType.RHYTHM && blueprint != null)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("riptide-level-template.text-4"));
        variant = variant == null ? "AUTO" : variant.toUpperCase(Locale.ROOT);
        if (type == RiptideLevelType.PASS && blueprint != null) variant = "CUSTOM";
        if (type == RiptideLevelType.PASS
                && variant.equals("CUSTOM")
                && blueprint == null
                && enabled)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("riptide-level-template.text-5"));
        if (!(type == RiptideLevelType.PASS && variant.equals("CUSTOM"))
                && !variants(type).contains(variant))
            throw new IllegalArgumentException(
                    MessageConfig.codeText("riptide-level-template.text-6", name, variant));
        if (type == RiptideLevelType.PASS) maxUses = 1;
        if (weight < 1
                || weight > 100
                || maxUses < 1
                || maxUses > 64
                || difficulty < 1
                || difficulty > 3)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("riptide-level-template.text-7", name));
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
            case "AUTO" -> MessageConfig.codeText("riptide-level-template.variant-name-1");
            case "CUSTOM" -> MessageConfig.codeText("riptide-level-template.variant-name-2");
            case "GAP" -> MessageConfig.codeText("riptide-level-template.variant-name-3");
            case "JUMP" -> MessageConfig.codeText("riptide-level-template.variant-name-4");
            case "WEAVE" -> MessageConfig.codeText("riptide-level-template.variant-name-5");
            case "ADD" -> MessageConfig.codeText("riptide-level-template.variant-name-6");
            case "SUBTRACT" -> MessageConfig.codeText("riptide-level-template.variant-name-7");
            case "MULTIPLY" -> MessageConfig.codeText("riptide-level-template.variant-name-8");
            case "DOUBLE" -> MessageConfig.codeText("riptide-level-template.variant-name-9");
            case "OBSERVE_COUNT" ->
                    MessageConfig.codeText("riptide-level-template.variant-name-10");
            case "OBSERVE_ORDER" ->
                    MessageConfig.codeText("riptide-level-template.variant-name-11");
            case "OBSERVE_EXTREME" ->
                    MessageConfig.codeText("riptide-level-template.variant-name-12");
            case "OBSERVE_PARITY" ->
                    MessageConfig.codeText("riptide-level-template.variant-name-13");
            case "OBSERVE_UNIQUE" ->
                    MessageConfig.codeText("riptide-level-template.variant-name-14");
            case "SHUTTER" -> MessageConfig.codeText("riptide-level-template.variant-name-15");
            case "ALTERNATING" -> MessageConfig.codeText("riptide-level-template.variant-name-16");
            case "DOUBLE_BEAT" -> MessageConfig.codeText("riptide-level-template.variant-name-17");
            case "CENTER_SIDES" -> MessageConfig.codeText("riptide-level-template.variant-name-18");
            case "SWEEP" -> MessageConfig.codeText("riptide-level-template.variant-name-19");
            case "IN_OUT" -> MessageConfig.codeText("riptide-level-template.variant-name-20");
            case "CROSS_BEAT" -> MessageConfig.codeText("riptide-level-template.variant-name-21");
            case "HORIZONTAL_WINDOW" ->
                    MessageConfig.codeText("riptide-level-template.variant-name-22");
            case "VERTICAL_WINDOW" ->
                    MessageConfig.codeText("riptide-level-template.variant-name-23");
            case "WINDOW_SHUTTER" ->
                    MessageConfig.codeText("riptide-level-template.variant-name-24");
            case "STAGGERED_WINDOWS" ->
                    MessageConfig.codeText("riptide-level-template.variant-name-25");
            case "TRIPLE_PULSE" -> MessageConfig.codeText("riptide-level-template.variant-name-26");
            case "LEFT_RIGHT_CENTER" ->
                    MessageConfig.codeText("riptide-level-template.variant-name-27");
            case "EDGE_SWAP" -> MessageConfig.codeText("riptide-level-template.variant-name-28");
            case "PINBALL" -> MessageConfig.codeText("riptide-level-template.variant-name-29");
            case "SNAKE" -> MessageConfig.codeText("riptide-level-template.variant-name-30");
            case "CENTER_PULSE" -> MessageConfig.codeText("riptide-level-template.variant-name-31");
            case "EDGE_PULSE" -> MessageConfig.codeText("riptide-level-template.variant-name-32");
            case "FOLD" -> MessageConfig.codeText("riptide-level-template.variant-name-33");
            case "SPLIT_MERGE" -> MessageConfig.codeText("riptide-level-template.variant-name-34");
            case "REST_ACCENT" -> MessageConfig.codeText("riptide-level-template.variant-name-35");
            case "MIRROR_CHASE" -> MessageConfig.codeText("riptide-level-template.variant-name-36");
            case "DIAGONAL" -> MessageConfig.codeText("riptide-level-template.variant-name-37");
            case "DOUBLE_WINDOW" ->
                    MessageConfig.codeText("riptide-level-template.variant-name-38");
            case "BACKBEAT" -> MessageConfig.codeText("riptide-level-template.variant-name-39");
            case "QUICK_TURN" -> MessageConfig.codeText("riptide-level-template.variant-name-40");
            case "ORE" -> MessageConfig.codeText("riptide-level-template.variant-name-41");
            case "LOG" -> MessageConfig.codeText("riptide-level-template.variant-name-42");
            case "NETHER" -> MessageConfig.codeText("riptide-level-template.variant-name-43");
            case "COPPER" -> MessageConfig.codeText("riptide-level-template.variant-name-44");
            case "WOOD" -> MessageConfig.codeText("riptide-level-template.variant-name-45");
            case "TERRACOTTA" -> MessageConfig.codeText("riptide-level-template.variant-name-46");
            case "STONE" -> MessageConfig.codeText("riptide-level-template.variant-name-47");
            case "ZOMBIE" -> MessageConfig.codeText("riptide-level-template.variant-name-48");
            case "HUSK" -> MessageConfig.codeText("riptide-level-template.variant-name-49");
            case "SKELETON" -> MessageConfig.codeText("riptide-level-template.variant-name-50");
            case "SPIDER" -> MessageConfig.codeText("riptide-level-template.variant-name-51");
            case "CREEPER" -> MessageConfig.codeText("riptide-level-template.variant-name-52");
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
                string(row, "name", MessageConfig.codeText("riptide-level-template.parse-1")),
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
