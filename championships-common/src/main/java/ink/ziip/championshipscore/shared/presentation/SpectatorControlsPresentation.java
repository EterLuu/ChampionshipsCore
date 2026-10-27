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

/** Frozen worker hotbar appearance; roles, interaction and card ownership stay with the session. */
public record SpectatorControlsPresentation(
        int cardSlot,
        Map<String, Integer> slots,
        Map<String, InventoryItemTemplate> items,
        String speedFeedback) {
    public static final String PREFIX = "gui.spectator-controls.";
    public static final List<String> CONTROLS = List.of("teleport", "speed");

    public SpectatorControlsPresentation {
        slots = Map.copyOf(slots);
        items = Map.copyOf(items);
        Objects.requireNonNull(speedFeedback);
        if (!slots.keySet().equals(java.util.Set.copyOf(CONTROLS))
                || !items.keySet().equals(java.util.Set.copyOf(CONTROLS)))
            throw new IllegalArgumentException("Missing spectator hotbar controls");
        var occupied = new java.util.HashSet<Integer>();
        occupied.add(cardSlot);
        occupied.addAll(slots.values());
        if (occupied.size() != 3 || occupied.stream().anyMatch(slot -> slot < 0 || slot >= 9))
            throw new IllegalArgumentException(
                    "Spectator controls and card require distinct hotbar slots");
    }

    public Map<String, String> attributes() {
        var values = new HashMap<String, String>();
        values.put(PREFIX + "version", "1");
        values.put(PREFIX + "card-slot", Integer.toString(cardSlot));
        values.put(PREFIX + "speed-feedback", speedFeedback);
        CONTROLS.forEach(
                id -> {
                    values.put(PREFIX + id + ".slot", Integer.toString(slots.get(id)));
                    PresentationAttributes.writeItem(values, PREFIX + id + ".", items.get(id));
                });
        return Map.copyOf(values);
    }

    /** Only unmarked historical manifests retain their original forced-root styling. */
    public static String legacyStyle(String key) {
        return PresentationAttributes.required(Legacy.VALUES, "style." + key);
    }

    public static SpectatorControlsPresentation from(BingoPresentation presentation) {
        Map<String, String> values = presentation.messages();
        if (values.keySet().stream().noneMatch(key -> key.startsWith(PREFIX))) {
            var tokens = new HashMap<String, String>();
            for (String key :
                    List.of(
                            "teleport.name",
                            "teleport.hint",
                            "speed.name",
                            "speed.faster",
                            "speed.slower",
                            "speed.feedback"))
                tokens.put("%" + key + "%", presentation.message("spectator." + key));
            var legacy = new HashMap<String, String>();
            Legacy.VALUES.forEach(
                    (key, value) ->
                            legacy.put(PREFIX + key, MessageTemplate.render(value, tokens)));
            values = legacy;
        }
        if (!"1".equals(PresentationAttributes.required(values, PREFIX + "version")))
            throw new IllegalArgumentException("Unknown spectator hotbar schema");
        var slots = new HashMap<String, Integer>();
        var items = new HashMap<String, InventoryItemTemplate>();
        for (String id : CONTROLS) {
            slots.put(
                    id,
                    Integer.parseInt(
                            PresentationAttributes.required(values, PREFIX + id + ".slot")));
            items.put(id, PresentationAttributes.readItem(values, PREFIX + id + "."));
        }
        return new SpectatorControlsPresentation(
                Integer.parseInt(PresentationAttributes.required(values, PREFIX + "card-slot")),
                slots,
                items,
                PresentationAttributes.required(values, PREFIX + "speed-feedback"));
    }

    private static final class Legacy {
        static final Map<String, String> VALUES = load();

        private static Map<String, String> load() {
            try (var stream =
                    SpectatorControlsPresentation.class.getResourceAsStream(
                            "/presentation/legacy-spectator-controls.properties")) {
                if (stream == null)
                    throw new IllegalStateException("Missing legacy spectator controls");
                var properties = new Properties();
                properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                var values = new HashMap<String, String>();
                properties.forEach((key, value) -> values.put((String) key, (String) value));
                return Map.copyOf(values);
            } catch (IOException error) {
                throw new IllegalStateException("Cannot load legacy spectator controls", error);
            }
        }
    }
}
