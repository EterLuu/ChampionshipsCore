package ink.ziip.championshipscore.api.game.spatial;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Position geometry used by lobby returns, map capture and batch teleports. */
public final class TeleportPositions {
    private TeleportPositions() {}

    /**
     * Horizontal scatter radius (blocks) around the lobby spawn used to spread returning players
     * apart.
     */
    private static final double LOBBY_SCATTER_RADIUS = 5.0D;

    /**
     * Lobby spawn scattered horizontally around the configured centre for one player. The player's
     * UUID selects a stable golden-angle offset, so a crowd returning from a finished match spreads
     * over the surrounding floor instead of stacking on one point and pushing each other apart.
     * Offsets that land on walls or off the floor shrink progressively back to the exact centre.
     */
    public static Location getScatteredLobbyLocation(
            @Nullable Location lobby, @NotNull Player player) {
        if (lobby == null || lobby.getWorld() == null) return lobby;
        int hash = player.getUniqueId().hashCode();
        double angle = ((hash & 0xFFFF) / 65536.0D) * Math.PI * 2.0D;
        double radius = LOBBY_SCATTER_RADIUS * Math.sqrt(((hash >>> 16) & 0xFFFF) / 65536.0D);
        for (double scale = 1.0D; scale >= 0.2D; scale *= 0.5D) {
            Location candidate = lobby.clone();
            candidate.setX(lobby.getX() + Math.cos(angle) * radius * scale);
            candidate.setZ(lobby.getZ() + Math.sin(angle) * radius * scale);
            if (isSafeLobbySpot(candidate)) return candidate;
        }
        return lobby;
    }

    /**
     * Returns a deterministic nearby slot for a batch teleport. A shared target is still used as
     * the anchor, but each player receives a separate square-spiral slot so Bukkit's entity
     * collision resolution cannot launch a group that was teleported on top of itself.
     */
    public static Location getCollisionSafeTeleportLocation(@NotNull Location anchor, int index) {
        Location result = anchor.clone();
        if (index <= 0) return result;

        int remaining = index - 1;
        int ring = 1;
        while (remaining >= ring * 8) {
            remaining -= ring * 8;
            ring++;
        }
        int side = remaining / (ring * 2);
        int offset = remaining % (ring * 2);
        int x;
        int z;
        switch (side) {
            case 0 -> {
                x = -ring + offset;
                z = -ring;
            }
            case 1 -> {
                x = ring;
                z = -ring + offset;
            }
            case 2 -> {
                x = ring - offset;
                z = ring;
            }
            default -> {
                x = -ring;
                z = ring - offset;
            }
        }
        result.add(x * 1.25D, 0D, z * 1.25D);
        return result;
    }

    /**
     * Solid ground with passable feet and head space, so a scattered player neither falls nor
     * suffocates.
     */
    private static boolean isSafeLobbySpot(@NotNull Location spot) {
        World world = spot.getWorld();
        int x = spot.getBlockX();
        int y = spot.getBlockY();
        int z = spot.getBlockZ();
        return world.getBlockAt(x, y - 1, z).getType().isSolid()
                && world.getBlockAt(x, y, z).isPassable()
                && world.getBlockAt(x, y + 1, z).isPassable();
    }

    /**
     * Aligns a player-captured point with the horizontal centre of the block they occupy. Height
     * and view direction deliberately remain untouched, because spawn surfaces may be slabs or
     * otherwise sit between whole Y coordinates.
     */
    public static Location centerOnBlock(@NotNull Location location) {
        Location centered = location.clone();
        centered.setX(location.getBlockX() + 0.5D);
        centered.setZ(location.getBlockZ() + 0.5D);
        return centered;
    }
}
