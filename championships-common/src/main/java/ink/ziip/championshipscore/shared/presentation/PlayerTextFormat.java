package ink.ziip.championshipscore.shared.presentation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Core-owned player text templates, frozen in a match without changing persisted old manifests. */
public record PlayerTextFormat(
        String prefix,
        String gameLabel,
        String inactiveNameColor,
        String identity,
        String chat,
        String dailyChat) {
    public static final String PREFIX = "presentation.player.";
    public static final List<String> KEYS =
            List.of(
                    "prefix",
                    "game-label",
                    "inactive-name-color",
                    "identity",
                    "chat",
                    "daily-chat");

    public PlayerTextFormat {
        Objects.requireNonNull(prefix);
        Objects.requireNonNull(gameLabel);
        Objects.requireNonNull(inactiveNameColor);
        Objects.requireNonNull(identity);
        Objects.requireNonNull(chat);
        Objects.requireNonNull(dailyChat);
    }

    public Map<String, String> attributes() {
        var values = new LinkedHashMap<String, String>();
        values.put(PREFIX + "version", "1");
        var templates = List.of(prefix, gameLabel, inactiveNameColor, identity, chat, dailyChat);
        for (int i = 0; i < KEYS.size(); i++) values.put(PREFIX + KEYS.get(i), templates.get(i));
        return Map.copyOf(values);
    }

    public static PlayerTextFormat from(Map<String, String> values) {
        if (!values.containsKey(PREFIX + "version")
                && values.keySet().stream().noneMatch(key -> key.startsWith(PREFIX)))
            return legacy();
        if (!"1".equals(PresentationAttributes.required(values, PREFIX + "version")))
            throw new IllegalArgumentException("Unknown player text schema");
        return new PlayerTextFormat(
                PresentationAttributes.required(values, PREFIX + "prefix"),
                PresentationAttributes.required(values, PREFIX + "game-label"),
                PresentationAttributes.required(values, PREFIX + "inactive-name-color"),
                PresentationAttributes.required(values, PREFIX + "identity"),
                PresentationAttributes.required(values, PREFIX + "chat"),
                PresentationAttributes.required(values, PREFIX + "daily-chat"));
    }

    public static PlayerTextFormat legacy() {
        return Legacy.VALUE;
    }

    private static final class Legacy {
        static final PlayerTextFormat VALUE = load();

        private static PlayerTextFormat load() {
            try (var stream =
                    PlayerTextFormat.class.getResourceAsStream(
                            "/presentation/legacy-player-text.properties")) {
                if (stream == null) throw new IllegalStateException("Missing legacy player text");
                var properties = new Properties();
                properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                var values = new LinkedHashMap<String, String>();
                properties.forEach((key, value) -> values.put(PREFIX + key, (String) value));
                return from(values);
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot load legacy player text", exception);
            }
        }
    }
}
