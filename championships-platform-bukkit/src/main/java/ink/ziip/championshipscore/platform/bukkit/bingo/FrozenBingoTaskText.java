package ink.ziip.championshipscore.platform.bukkit.bingo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Rich task text frozen by Core; a marked snapshot must never fall back to registry labels. */
public record FrozenBingoTaskText(Component name, List<Component> lore) {
    private static final String VERSION = "display.text-version";

    public FrozenBingoTaskText {
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
    }

    public Map<String, String> attributes() {
        var serializer = GsonComponentSerializer.gson();
        Map<String, String> result = new LinkedHashMap<>();
        result.put(VERSION, "1");
        result.put("display.name", serializer.serialize(name));
        result.put("display.lore-size", Integer.toString(lore.size()));
        for (int index = 0; index < lore.size(); index++)
            result.put("display.lore." + index, serializer.serialize(lore.get(index)));
        return Map.copyOf(result);
    }

    /** An absent marker denotes the historical optional text fields, not a corrupt new snapshot. */
    public static Optional<FrozenBingoTaskText> from(Map<String, String> attributes) {
        if (!attributes.containsKey(VERSION)) return Optional.empty();
        if (!"1".equals(attributes.get(VERSION)))
            throw new IllegalArgumentException("Unsupported Bingo task text version");
        try {
            int size = Integer.parseInt(required(attributes, "display.lore-size"));
            // Each line needs a distinct attribute; this also bounds work for malformed input.
            if (size < 0 || size > attributes.size())
                throw new IllegalArgumentException("Invalid Bingo task lore size");
            var serializer = GsonComponentSerializer.gson();
            Component name = serializer.deserialize(required(attributes, "display.name"));
            var lore = new java.util.ArrayList<Component>(size);
            for (int index = 0; index < size; index++)
                lore.add(serializer.deserialize(required(attributes, "display.lore." + index)));
            return Optional.of(new FrozenBingoTaskText(name, lore));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid frozen Bingo task text", invalid);
        }
    }

    private static String required(Map<String, String> attributes, String key) {
        return Objects.requireNonNull(attributes.get(key), key);
    }
}
