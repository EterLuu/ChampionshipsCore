package ink.ziip.championshipscore.api.finale;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Canonical registry for games which may decide the championship finale. */
public final class FinaleGameRegistry {
    private static final Map<GameTypeEnum, FinaleGameDefinition> DEFINITIONS;

    static {
        EnumMap<GameTypeEnum, FinaleGameDefinition> definitions = new EnumMap<>(GameTypeEnum.class);
        register(definitions, new FinaleGameDefinition(
                GameTypeEnum.Dodgebolt, "dodgebolt", "dodgebolt", true));
        register(definitions, new FinaleGameDefinition(
                GameTypeEnum.DragonEggCarnival, "dragoneggcarnival", "area1", false));
        DEFINITIONS = Collections.unmodifiableMap(definitions);
    }

    private FinaleGameRegistry() {
    }

    private static void register(@NotNull Map<GameTypeEnum, FinaleGameDefinition> definitions,
                                 @NotNull FinaleGameDefinition definition) {
        if (definitions.putIfAbsent(definition.gameType(), definition) != null)
            throw new IllegalStateException("Duplicate finale game: " + definition.gameType());
    }

    public static boolean isRegistered(@Nullable GameTypeEnum gameType) {
        return gameType != null && DEFINITIONS.containsKey(gameType);
    }

    public static @Nullable FinaleGameDefinition definition(@Nullable GameTypeEnum gameType) {
        return gameType == null ? null : DEFINITIONS.get(gameType);
    }

    public static @Nullable FinaleGameDefinition parse(@NotNull String raw) {
        String normalized = normalize(raw);
        for (FinaleGameDefinition definition : DEFINITIONS.values()) {
            if (normalize(definition.commandName()).equals(normalized)
                    || normalize(definition.gameType().name()).equals(normalized))
                return definition;
        }
        return null;
    }

    public static @NotNull Collection<FinaleGameDefinition> definitions() {
        return DEFINITIONS.values();
    }

    public static @NotNull Set<GameTypeEnum> gameTypes() {
        return DEFINITIONS.keySet();
    }

    private static @NotNull String normalize(@NotNull String value) {
        return value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }
}
