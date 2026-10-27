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

/** Frozen paged player selector; membership and admission remain with the owning session. */
public record PlayerListPresentation(
        String title, InventoryMenuLayout layout, Map<String, InventoryItemTemplate> items) {
    public static final String TEAMMATE_PREFIX = "gui.teammates.";
    public static final String SPECTATOR_PREFIX = "gui.spectator-players.";
    public static final List<String> CONTROLS = List.of("previous", "close", "page", "next");
    public static final List<String> ITEMS =
            List.of("player", "previous", "close", "page", "next", "border");

    public PlayerListPresentation {
        Objects.requireNonNull(title);
        Objects.requireNonNull(layout);
        items = Map.copyOf(items);
        if (!items.keySet().equals(java.util.Set.copyOf(ITEMS))
                || !layout.controls().keySet().equals(java.util.Set.copyOf(CONTROLS))
                || layout.content().isEmpty()
                || layout.controls().values().stream().distinct().count() != CONTROLS.size())
            throw new IllegalArgumentException(
                    "Player menu requires content, items and distinct controls");
    }

    public Map<String, String> attributes(String prefix) {
        var values = new HashMap<String, String>();
        values.put(prefix + "version", "1");
        values.put(prefix + "title", title);
        values.put(prefix + "size", Integer.toString(layout.size()));
        values.put(prefix + "content", PresentationAttributes.slots(layout.content()));
        values.put(prefix + "border", PresentationAttributes.slots(layout.border()));
        CONTROLS.forEach(
                id -> values.put(prefix + "control." + id, Integer.toString(layout.control(id))));
        layout.arrangements()
                .forEach(
                        (count, slots) ->
                                values.put(
                                        prefix + "arrangement." + count,
                                        PresentationAttributes.slots(slots)));
        items.forEach(
                (id, item) ->
                        PresentationAttributes.writeItem(
                                values, prefix + "item." + id + ".", item));
        return Map.copyOf(values);
    }

    public static PlayerListPresentation spectator(BingoPresentation presentation) {
        return from(presentation, SPECTATOR_PREFIX, Legacy.SPECTATOR);
    }

    public static PlayerListPresentation teammates(BingoPresentation presentation) {
        return from(presentation, TEAMMATE_PREFIX, Legacy.TEAMMATES);
    }

    private static PlayerListPresentation from(
            BingoPresentation presentation, String prefix, Map<String, String> fallback) {
        var values = presentation.messages();
        if (values.keySet().stream().anyMatch(key -> key.startsWith(prefix)))
            return read(values, prefix);
        var legacy = new HashMap<String, String>();
        fallback.forEach(
                (key, value) ->
                        legacy.put(
                                prefix + key,
                                value.startsWith("@language:")
                                        ? presentation.message(
                                                value.substring("@language:".length()))
                                        : value));
        return read(legacy, prefix);
    }

    private static PlayerListPresentation read(Map<String, String> values, String prefix) {
        if (!"1".equals(PresentationAttributes.required(values, prefix + "version")))
            throw new IllegalArgumentException("Unknown player menu schema");
        var controls = new HashMap<String, Integer>();
        CONTROLS.forEach(
                id ->
                        controls.put(
                                id,
                                Integer.parseInt(
                                        PresentationAttributes.required(
                                                values, prefix + "control." + id))));
        var arrangements = new HashMap<Integer, List<Integer>>();
        values.forEach(
                (key, value) -> {
                    if (key.startsWith(prefix + "arrangement."))
                        arrangements.put(
                                Integer.parseInt(key.substring((prefix + "arrangement.").length())),
                                PresentationAttributes.slots(value));
                });
        var items = new HashMap<String, InventoryItemTemplate>();
        ITEMS.forEach(
                id ->
                        items.put(
                                id,
                                PresentationAttributes.readItem(
                                        values, prefix + "item." + id + ".")));
        return new PlayerListPresentation(
                PresentationAttributes.required(values, prefix + "title"),
                new InventoryMenuLayout(
                        Integer.parseInt(PresentationAttributes.required(values, prefix + "size")),
                        PresentationAttributes.slots(
                                PresentationAttributes.required(values, prefix + "content")),
                        PresentationAttributes.slots(
                                PresentationAttributes.required(values, prefix + "border")),
                        controls,
                        arrangements),
                items);
    }

    private static final class Legacy {
        static final Map<String, String> SPECTATOR =
                load("legacy-spectator-player-menu.properties");
        static final Map<String, String> TEAMMATES = load("legacy-teammate-menu.properties");

        private static Map<String, String> load(String file) {
            try (var stream =
                    PlayerListPresentation.class.getResourceAsStream("/presentation/" + file)) {
                if (stream == null)
                    throw new IllegalStateException("Missing legacy player menu resource");
                var properties = new Properties();
                properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                var values = new HashMap<String, String>();
                properties.forEach((key, value) -> values.put((String) key, (String) value));
                return Map.copyOf(values);
            } catch (IOException error) {
                throw new IllegalStateException("Cannot load legacy player menu", error);
            }
        }
    }
}
