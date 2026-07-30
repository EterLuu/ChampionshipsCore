package ink.ziip.championshipscore.api.game.config;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/** Stable, filesystem-safe world names for map definitions whose display names may contain any language. */
public final class MapWorldNames {
    private MapWorldNames() {
    }

    public static @NotNull String forMap(@NotNull String game, @NotNull String mapName) {
        String slug = mapName.toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("^_+|_+$", "");
        if (slug.isBlank()) slug = "map";
        if (slug.length() > 24) slug = slug.substring(0, 24);
        String hash = UUID.nameUUIDFromBytes(mapName.getBytes(StandardCharsets.UTF_8))
                .toString().substring(0, 8);
        return game + "_" + slug + "_" + hash;
    }
}
