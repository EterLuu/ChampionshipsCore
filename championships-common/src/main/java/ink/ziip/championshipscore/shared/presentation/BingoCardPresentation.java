package ink.ziip.championshipscore.shared.presentation;

import ink.ziip.championshipscore.protocol.BingoPresentation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Core-owned card window settings, frozen in the existing presentation attributes of a manifest.
 */
public record BingoCardPresentation(
        String title, Map<Integer, Layout> layouts, InventoryItemTemplate info) {
    public static final String PREFIX = "gui.card.";
    public static final List<Integer> WIDTHS = List.of(3, 4, 5);

    public BingoCardPresentation {
        Objects.requireNonNull(title);
        Objects.requireNonNull(info);
        layouts = Map.copyOf(layouts);
        if (!layouts.keySet().equals(java.util.Set.copyOf(WIDTHS)))
            throw new IllegalArgumentException("Card menu requires layouts for widths 3, 4 and 5");
        layouts.forEach(
                (width, layout) -> {
                    if (layout.tasks().size() != width * width)
                        throw new IllegalArgumentException(
                                "Card slot count differs from width: " + width);
                });
    }

    public record Layout(int size, List<Integer> tasks, int info) {
        public Layout {
            tasks = List.copyOf(tasks);
            if (size < 9 || size > 54 || size % 9 != 0 || info < 0 || info >= size)
                throw new IllegalArgumentException("Invalid card inventory size or info slot");
            if (tasks.stream().distinct().count() != tasks.size()
                    || tasks.contains(info)
                    || tasks.stream().anyMatch(slot -> slot < 0 || slot >= size))
                throw new IllegalArgumentException(
                        "Card task slots must be unique, in bounds and outside info");
        }
    }

    public Layout layout(int width) {
        Layout value = layouts.get(width);
        if (value == null) throw new IllegalArgumentException("Unsupported card width: " + width);
        return value;
    }

    public Map<String, String> attributes() {
        var values = new HashMap<String, String>();
        values.put(PREFIX + "version", "1");
        values.put(PREFIX + "title", title);
        PresentationAttributes.writeItem(values, PREFIX + "info.", info);
        layouts.forEach(
                (width, layout) -> {
                    String key = PREFIX + "x" + width + ".";
                    values.put(key + "size", Integer.toString(layout.size()));
                    values.put(key + "info", Integer.toString(layout.info()));
                    values.put(
                            key + "tasks",
                            layout.tasks().stream()
                                    .map(String::valueOf)
                                    .collect(Collectors.joining(",")));
                });
        return Map.copyOf(values);
    }

    public static BingoCardPresentation from(BingoPresentation presentation) {
        var values = presentation.messages();
        if (values.keySet().stream().noneMatch(key -> key.startsWith(PREFIX))) return Legacy.VALUE;
        if (!"1".equals(required(values, "version")))
            throw new IllegalArgumentException("Unknown card menu schema");
        return read(values);
    }

    private static BingoCardPresentation read(Map<String, String> values) {
        var layouts = new HashMap<Integer, Layout>();
        for (int width : WIDTHS) {
            String key = "x" + width + ".";
            var tasks =
                    java.util.Arrays.stream(required(values, key + "tasks").split(",", -1))
                            .map(Integer::parseInt)
                            .toList();
            layouts.put(
                    width,
                    new Layout(
                            Integer.parseInt(required(values, key + "size")),
                            tasks,
                            Integer.parseInt(required(values, key + "info"))));
        }
        return new BingoCardPresentation(
                required(values, "title"),
                layouts,
                PresentationAttributes.readItem(values, PREFIX + "info."));
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(PREFIX + key);
        if (value == null) throw new IllegalArgumentException("Missing card menu field: " + key);
        return value;
    }

    private static final class Legacy {
        static final BingoCardPresentation VALUE = load();

        private static BingoCardPresentation load() {
            try (var stream =
                    BingoCardPresentation.class.getResourceAsStream(
                            "/presentation/legacy-card-menu.properties")) {
                if (stream == null)
                    throw new IllegalStateException("Missing legacy card menu resource");
                var properties = new Properties();
                properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                var values = new HashMap<String, String>();
                properties.forEach((key, value) -> values.put(PREFIX + key, (String) value));
                return read(values);
            } catch (IOException error) {
                throw new IllegalStateException("Cannot read legacy card menu", error);
            }
        }
    }
}
