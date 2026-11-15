package ink.ziip.championshipscore.platform.bukkit.bingo;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Preserves native bed/anchor validation and charge consumption within the current Bingo worlds.
 */
public final class BingoRespawnLocations {
    private BingoRespawnLocations() {}

    public static Location eventDestination(
            PlayerRespawnEvent event,
            boolean allowPersonalSpawn,
            Predicate<World> gameWorld,
            Supplier<Location> fallback) {
        Location nativeLocation = event.getRespawnLocation();
        if (allowPersonalSpawn
                && (event.isBedSpawn() || event.isAnchorSpawn())
                && nativeLocation.getWorld() != null
                && gameWorld.test(nativeLocation.getWorld())) return nativeLocation;
        return fallback.get();
    }

    /**
     * Called on the player thread after native respawn, including Folia's eventless path. Read only
     * stored spawn metadata: calculating a spawn here would read blocks on another region and could
     * reject an anchor whose last charge was already consumed by this respawn. Invalid native
     * beds/anchors clear that metadata. Keep the actual native destination, never teleport to the
     * stored block coordinates.
     */
    public static Location recoveryDestination(
            Player player, Predicate<World> gameWorld, Supplier<Location> fallback) {
        Location personal = player.getRespawnLocation(false);
        if (personal != null
                && personal.getWorld() != null
                && gameWorld.test(personal.getWorld())
                && personal.getWorld().equals(player.getWorld())) return null;
        return fallback.get();
    }
}
