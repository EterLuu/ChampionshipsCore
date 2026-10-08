package ink.ziip.championshipscore.api.game.spatial;

import ink.ziip.championshipscore.configuration.location.LocationConfig;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Remembers lobby departures in Core's player data, including proxy transfers and reconnects. */
public final class LobbyReturnLocations {
    private static final NamespacedKey LAST_LOBBY_LOCATION =
            NamespacedKey.fromString("championshipscore:last_lobby_location");

    private LobbyReturnLocations() {}

    public static void remember(
            @NotNull Player player, @Nullable Location location, @Nullable Location lobby) {
        if (lobby == null
                || lobby.getWorld() == null
                || location == null
                || !lobby.getWorld().equals(location.getWorld())) return;
        player.getPersistentDataContainer()
                .set(
                        LAST_LOBBY_LOCATION,
                        PersistentDataType.STRING,
                        LocationConfig.asString(location));
    }

    public static @Nullable Location resolve(@NotNull Player player, @Nullable Location lobby) {
        if (lobby == null || lobby.getWorld() == null) return lobby;
        String saved =
                player.getPersistentDataContainer()
                        .get(LAST_LOBBY_LOCATION, PersistentDataType.STRING);
        if (saved != null) {
            try {
                Location location = LocationConfig.readLocation(saved, player.getServer());
                if (location != null && lobby.getWorld().equals(location.getWorld()))
                    return location;
            } catch (IllegalArgumentException invalid) {
                player.getPersistentDataContainer().remove(LAST_LOBBY_LOCATION);
            }
        }
        return TeleportPositions.getScatteredLobbyLocation(lobby, player);
    }
}
