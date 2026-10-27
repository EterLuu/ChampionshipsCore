package ink.ziip.championshipscore.shared.presentation;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** Lossless primitive fields for inventory presentation carried by a manifest. */
public final class PresentationAttributes {
    private PresentationAttributes() {}

    public static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) throw new IllegalArgumentException("Missing presentation field: " + key);
        return value;
    }

    public static List<Integer> slots(String value) {
        if (value.isEmpty()) return List.of();
        return java.util.Arrays.stream(value.split(",", -1)).map(Integer::parseInt).toList();
    }

    public static String slots(List<Integer> values) {
        return values.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
    }

    public static void writeItem(
            Map<String, String> values, String prefix, InventoryItemTemplate item) {
        values.put(prefix + "material", item.material());
        values.put(prefix + "title", item.title());
        values.put(prefix + "glint", Boolean.toString(item.glint()));
        values.put(prefix + "lore.size", Integer.toString(item.lore().size()));
        for (int i = 0; i < item.lore().size(); i++)
            values.put(prefix + "lore." + i, item.lore().get(i));
    }

    public static InventoryItemTemplate readItem(Map<String, String> values, String prefix) {
        int lines = Integer.parseInt(required(values, prefix + "lore.size"));
        if (lines < 0 || lines > 4096) throw new IllegalArgumentException("Invalid item lore size");
        String glint = required(values, prefix + "glint");
        if (!glint.equals("true") && !glint.equals("false"))
            throw new IllegalArgumentException("Invalid item glint");
        return new InventoryItemTemplate(
                required(values, prefix + "material"),
                required(values, prefix + "title"),
                IntStream.range(0, lines)
                        .mapToObj(i -> required(values, prefix + "lore." + i))
                        .toList(),
                Boolean.parseBoolean(glint));
    }
}
