package ink.ziip.championshipscore.api.object.game;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/** Controls the public lobby surface. Individual game runs still carry their own {@link GameRunMode}. */
public enum ServerMode {
    CHAMPIONSHIP,
    DAILY;

    public static @NotNull ServerMode parse(String value) {
        if (value == null) return CHAMPIONSHIP;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return CHAMPIONSHIP;
        }
    }
}
