package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.bingo.FrozenBingoTaskText;
import ink.ziip.championshipscore.platform.bukkit.text.ComponentTemplates;
import ink.ziip.championshipscore.protocol.BingoTaskSpec;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import org.bukkit.Material;
import org.bukkit.advancement.Advancement;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** Frozen text is authoritative; unmarked historical manifests retain their original fallback. */
final class WorkerTaskText {
    private static final Properties LEGACY = legacy();

    private WorkerTaskText() {}

    static Component name(BingoTaskSpec task) {
        var frozen = FrozenBingoTaskText.from(task.attributes());
        if (frozen.isPresent()) return frozen.get().name();
        String serialized = task.attributes().get("display.name");
        if (serialized != null) {
            try {
                return GsonComponentSerializer.gson().deserialize(serialized);
            } catch (RuntimeException ignored) {
                // Historical fields were optional and could be absent or partially serialized.
            }
        }
        Map<String, String> attributes = task.attributes();
        return switch (task.taskType().toLowerCase(Locale.ROOT)) {
            case "item", "item_set" ->
                    item(Component.translatable(WorkerTaskDisplay.icon(task).translationKey()));
            case "potion" ->
                    item(
                            Component.translatable(
                                    "item.minecraft."
                                            + WorkerTaskDisplay.icon(task).key().value()
                                            + ".effect."
                                            + attributes.getOrDefault("effect", "water")));
            case "advancement" -> advancementTitle(attributes.get("key"));
            case "statistic" -> statisticName(attributes);
            default -> item(Component.text(task.taskId()));
        };
    }

    static List<Component> lore(BingoTaskSpec task) {
        var frozen = FrozenBingoTaskText.from(task.attributes());
        if (frozen.isPresent()) return frozen.get().lore();
        List<Component> result = new ArrayList<>();
        int size;
        try {
            size = Integer.parseInt(task.attributes().getOrDefault("display.lore-size", "0"));
        } catch (NumberFormatException ignored) {
            size = 0;
        }
        // Missing historical lines were skipped. Iterate existing keys to avoid huge sparse loops.
        final int declaredSize = size;
        task.attributes().keySet().stream()
                .filter(key -> key.startsWith("display.lore."))
                .map(key -> key.substring("display.lore.".length()))
                .filter(index -> index.matches("0|[1-9][0-9]{0,9}"))
                .map(Long::parseLong)
                .filter(index -> index < declaredSize)
                .sorted()
                .forEach(
                        index -> {
                            try {
                                result.add(
                                        GsonComponentSerializer.gson()
                                                .deserialize(
                                                        task.attributes()
                                                                .get("display.lore." + index)));
                            } catch (RuntimeException ignored) {
                                // Preserve the optional-line behavior of unmarked manifests only.
                            }
                        });
        return List.copyOf(result);
    }

    private static Component item(Component name) {
        return render("item", Map.of("%name%", name));
    }

    private static Component advancementTitle(String key) {
        Advancement advancement = WorkerTaskDisplay.advancement(key);
        Component title =
                advancement != null && advancement.getDisplay() != null
                        ? advancement.getDisplay().title().color(null)
                        : Component.text(
                                key == null ? LEGACY.getProperty("unknown-advancement") : key);
        return render("advancement", Map.of("%name%", title));
    }

    private static Component statisticName(Map<String, String> attributes) {
        String statistic =
                attributes
                        .getOrDefault("statistic", LEGACY.getProperty("unknown-statistic"))
                        .toLowerCase(Locale.ROOT);
        Component subject = Component.empty();
        Material material = WorkerTaskDisplay.material(attributes.get("material"), null);
        if (material != null)
            subject =
                    render(
                            "subject",
                            Map.of("%name%", Component.translatable(material.translationKey())));
        String entity = attributes.get("entity");
        if (entity != null)
            subject =
                    render(
                            "subject",
                            Map.of("%name%", Component.text(entity.toLowerCase(Locale.ROOT))));
        return render(
                "statistic",
                Map.of(
                        "%statistic%",
                        Component.text(statistic),
                        "%count%",
                        Component.text(requiredAmount(attributes)),
                        "%subject%",
                        subject));
    }

    private static int requiredAmount(Map<String, String> attributes) {
        String raw = attributes.getOrDefault("count", attributes.getOrDefault("target", "1"));
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static Component render(String key, Map<String, Component> values) {
        return ComponentTemplates.render(LEGACY.getProperty(key), values);
    }

    private static Properties legacy() {
        var result = new Properties();
        try (var input =
                WorkerTaskText.class.getResourceAsStream(
                        "/presentation/legacy-task-names.properties")) {
            if (input == null) throw new IllegalStateException("Missing legacy task name resource");
            result.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return result;
        } catch (java.io.IOException invalid) {
            throw new ExceptionInInitializerError(invalid);
        }
    }
}
