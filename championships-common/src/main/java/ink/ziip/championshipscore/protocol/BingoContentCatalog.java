package ink.ziip.championshipscore.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/** Registry baseline shared by Core card selection and the supported Folia worker. */
public final class BingoContentCatalog {
    private static final BingoContentCatalog WORKER = load("26.3");
    private final Map<String, Set<String>> registries;

    private BingoContentCatalog(Map<String, Set<String>> registries) {
        this.registries = Map.copyOf(registries);
    }

    public static BingoContentCatalog worker() {
        return WORKER;
    }

    public boolean contains(String registry, String value) {
        return value != null
                && registries.getOrDefault(registry, Set.of()).contains(normalize(registry, value));
    }

    public boolean supports(BingoTaskSpec task) {
        Map<String, String> values = task.attributes();
        for (String field : Set.of("material", "display.material")) {
            if (!optional(values, field, "materials")) return false;
        }
        for (String field : Set.of("entity", "display.entity")) {
            if (!optional(values, field, "entities")) return false;
        }
        if (!optional(values, "statistic", "statistics")
                || !optional(values, "display.potion-type", "potions")) return false;
        for (String field : Set.of("materials", "members")) {
            String list = values.get(field);
            if (list != null && !list.isBlank()) {
                for (String member : list.split(",")) {
                    if (!contains("materials", member)) return false;
                }
            }
        }
        String subjects = values.get("subjects");
        if (subjects != null && !subjects.isBlank()) {
            for (String subject : subjects.split(",")) {
                String[] parts = subject.split("=", 2);
                if (parts.length != 2) return false;
                String registry =
                        switch (parts[0]) {
                            case "MATERIAL" -> "materials";
                            case "ENTITY_TYPE" -> "entities";
                            case "BIOME" -> "biomes";
                            default -> "";
                        };
                boolean entityFamily =
                        registry.equals("entities")
                                && Set.of("UNDEAD", "ARTHROPOD", "HOSTILE").contains(parts[1]);
                if (!entityFamily && !contains(registry, parts[1])) return false;
            }
        }
        return switch (task.taskType()) {
            case "item" -> contains("materials", values.get("material"));
            case "item_set", "all_of" -> values.containsKey("materials");
            case "statistic" -> contains("statistics", values.get("statistic"));
            case "advancement" -> contains("advancements", values.get("key"));
            case "potion" -> contains("potions", values.get("effect"));
            case "event" -> supportsEvent(values);
            default -> false;
        };
    }

    public void requireSupported(BingoTaskSpec task) {
        if (!supports(task)) {
            throw new IllegalArgumentException(
                    "Bingo task exceeds the Minecraft 26.3 worker content baseline: "
                            + task.taskId());
        }
    }

    private boolean supportsEvent(Map<String, String> values) {
        String param = values.getOrDefault("param", "");
        if (param.isBlank() || Set.of("ANY", "*", "ALL").contains(param.toUpperCase(Locale.ROOT)))
            return true;
        return switch (values.getOrDefault("trigger", "")) {
            case "eat", "use" -> contains("materials", param);
            case "drink" -> param.equals("WATER_BOTTLE") || contains("materials", param);
            case "break_item" ->
                    Set.of("TOOL", "ARMOR").contains(param) || contains("materials", param);
            case "place" -> param.equals("HANGING_SIGN") || contains("materials", param);
            case "wear", "wear_duration" ->
                    Set.of(
                                            "LEATHER",
                                            "COPPER",
                                            "CHAIN",
                                            "CHAINMAIL",
                                            "IRON",
                                            "GOLDEN",
                                            "DIAMOND",
                                            "NETHERITE")
                                    .contains(param)
                            || contains("materials", param);
            case "tame", "breed", "leash", "ride", "spy" -> contains("entities", param);
            case "name" ->
                    Set.of("SHEEP_JEB", "IRON_GOLEM_DINNERBONE", "GHAST_DINNERBONE").contains(param)
                            || contains("entities", param);
            case "effect" -> contains("effects", param);
            case "die" ->
                    contains("damage-causes", param)
                            || contains("entities", param)
                            || contains("materials", param)
                            || Set.of("FIREWORK", "FALLING_STALACTITE", "BERRY_BUSH")
                                    .contains(param);
            default -> true;
        };
    }

    private boolean optional(Map<String, String> values, String field, String registry) {
        String value = values.get(field);
        return value == null || value.isBlank() || contains(registry, value);
    }

    private static String normalize(String registry, String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (registry.equals("advancements")) {
            return normalized.contains(":") ? normalized : "minecraft:" + normalized;
        }
        if (normalized.startsWith("minecraft:")) normalized = normalized.substring(10);
        return normalized.toUpperCase(Locale.ROOT);
    }

    private static BingoContentCatalog load(String version) {
        String path = "/bingo/registry-" + version + ".properties";
        try (InputStream input = BingoContentCatalog.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing Bingo registry baseline " + path);
            Properties properties = new Properties();
            properties.load(input);
            if (!version.equals(properties.getProperty("minecraft-version"))) {
                throw new IOException("Bingo registry baseline version mismatch");
            }
            Map<String, Set<String>> registries = new LinkedHashMap<>();
            for (String registry :
                    Set.of(
                            "materials",
                            "entities",
                            "statistics",
                            "potions",
                            "effects",
                            "biomes",
                            "damage-causes",
                            "advancements")) {
                String values = properties.getProperty(registry);
                if (values == null || values.isBlank())
                    throw new IOException("Missing " + registry);
                registries.put(
                        registry,
                        Arrays.stream(values.split(","))
                                .map(value -> normalize(registry, value))
                                .collect(Collectors.toUnmodifiableSet()));
            }
            return new BingoContentCatalog(registries);
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
