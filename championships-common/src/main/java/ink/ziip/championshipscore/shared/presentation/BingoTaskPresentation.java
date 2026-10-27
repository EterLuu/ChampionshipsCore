package ink.ziip.championshipscore.shared.presentation;

import ink.ziip.championshipscore.protocol.BingoPresentation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/** Immutable task styles; task and completion state remain owned by the game model. */
public record BingoTaskPresentation(
        Map<State, InventoryItemTemplate> states,
        String fallbackMaterial,
        String separator,
        String player,
        List<String> completionSummary,
        String unknownTime,
        String shortTime,
        String longTime) {
    public static final String PREFIX = "gui.card-task.";
    public static final String TASK_MATERIAL = "TASK";
    private static final Pattern TASK_FIELDS =
            Pattern.compile(
                    "%(task|description|completed-by|occupied-by|player|completed-at|completions|completion-summary)%");

    public enum State {
        NORMAL,
        HIDDEN,
        LOCKED,
        COMPLETED,
        OCCUPIED;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public BingoTaskPresentation {
        states = Map.copyOf(states);
        completionSummary = List.copyOf(completionSummary);
        for (State state : State.values())
            if (!states.containsKey(state))
                throw new IllegalArgumentException("Missing task state: " + state);
        for (String value :
                new String[] {
                    fallbackMaterial, separator, player, unknownTime, shortTime, longTime
                }) java.util.Objects.requireNonNull(value);
        if (fallbackMaterial.isBlank() || TASK_MATERIAL.equals(fallbackMaterial))
            throw new IllegalArgumentException("Invalid task fallback material");
        for (State state : new State[] {State.HIDDEN, State.LOCKED})
            validateMasked(states.get(state));
    }

    public static void validateMasked(InventoryItemTemplate item) {
        if (TASK_MATERIAL.equals(item.material())
                || exposesTask(item.title())
                || item.lore().stream().anyMatch(BingoTaskPresentation::exposesTask))
            throw new IllegalArgumentException(
                    "Masked task styles must not expose dynamic task data");
    }

    private static boolean exposesTask(String text) {
        return TASK_FIELDS.matcher(text).find();
    }

    public static State state(
            boolean own,
            boolean anyCompleted,
            boolean neutral,
            boolean locksTasks,
            boolean hidden,
            boolean locked) {
        if (own && !neutral) return State.COMPLETED;
        if (anyCompleted && !neutral && locksTasks) return State.OCCUPIED;
        if (masked(anyCompleted, hidden, locked)) return locked ? State.LOCKED : State.HIDDEN;
        return State.NORMAL;
    }

    public static boolean masked(boolean anyCompleted, boolean hidden, boolean locked) {
        return !anyCompleted && (hidden || locked);
    }

    public String clock(long seconds) {
        return DurationText.clock(seconds, unknownTime, shortTime, longTime);
    }

    public Map<String, String> attributes() {
        var values = new HashMap<String, String>();
        values.put(PREFIX + "version", "1");
        values.put(PREFIX + "fallback-material", fallbackMaterial);
        values.put(PREFIX + "separator", separator);
        values.put(PREFIX + "player", player);
        values.put(PREFIX + "summary.size", Integer.toString(completionSummary.size()));
        for (int i = 0; i < completionSummary.size(); i++)
            values.put(PREFIX + "summary." + i, completionSummary.get(i));
        values.put(PREFIX + "time.unknown", unknownTime);
        values.put(PREFIX + "time.short", shortTime);
        values.put(PREFIX + "time.long", longTime);
        states.forEach(
                (state, item) ->
                        PresentationAttributes.writeItem(values, PREFIX + state.key() + ".", item));
        return Map.copyOf(values);
    }

    public static BingoTaskPresentation from(BingoPresentation presentation) {
        var values = presentation.messages();
        if (values.keySet().stream().noneMatch(key -> key.startsWith(PREFIX))) return Legacy.VALUE;
        if (!"1".equals(required(values, "version")))
            throw new IllegalArgumentException("Unknown task presentation schema");
        return read(values);
    }

    private static BingoTaskPresentation read(Map<String, String> values) {
        var states = new HashMap<State, InventoryItemTemplate>();
        for (State state : State.values())
            states.put(state, PresentationAttributes.readItem(values, PREFIX + state.key() + "."));
        int lines = Integer.parseInt(required(values, "summary.size"));
        if (lines < 0 || lines > 4096)
            throw new IllegalArgumentException("Invalid completion summary size");
        var summary =
                java.util.stream.IntStream.range(0, lines)
                        .mapToObj(i -> required(values, "summary." + i))
                        .toList();
        return new BingoTaskPresentation(
                states,
                required(values, "fallback-material"),
                required(values, "separator"),
                required(values, "player"),
                summary,
                required(values, "time.unknown"),
                required(values, "time.short"),
                required(values, "time.long"));
    }

    private static String required(Map<String, String> values, String key) {
        return PresentationAttributes.required(values, PREFIX + key);
    }

    private static final class Legacy {
        static final BingoTaskPresentation VALUE = load();

        private static BingoTaskPresentation load() {
            try (var stream =
                    BingoTaskPresentation.class.getResourceAsStream(
                            "/presentation/legacy-card-task.properties")) {
                if (stream == null) throw new IllegalStateException("Missing legacy task styles");
                var properties = new Properties();
                properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                var values = new HashMap<String, String>();
                properties.forEach((key, value) -> values.put(PREFIX + key, (String) value));
                return read(values);
            } catch (IOException error) {
                throw new IllegalStateException("Cannot read task styles", error);
            }
        }
    }
}
